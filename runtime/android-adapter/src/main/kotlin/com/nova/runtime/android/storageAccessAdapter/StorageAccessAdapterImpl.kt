package com.nova.runtime.android.storageAccessAdapter

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import com.nova.runtime.android.internal.AdapterBoundary
import com.nova.runtime.android.internal.AdapterErrorMapper
import com.nova.runtime.models.contracts.CapabilityResult
import com.nova.runtime.utils.logging.NovaLogger
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class StorageAccessAdapterImpl(
    private val context: Context,
    private val logger: NovaLogger,
) : StorageAccessAdapter {
    override fun supportedOperations(): Set<String> =
        setOf(
            StorageAccessOperations.BUILD_DOCUMENT_PICKER,
            StorageAccessOperations.TAKE_PERSISTABLE_PERMISSION,
            StorageAccessOperations.OPEN_DOCUMENT,
            StorageAccessOperations.QUERY_DOWNLOADS,
        )

    override suspend fun execute(
        operation: String,
        parameters: Map<String, String>,
        traceId: UUID,
    ): CapabilityResult {
        if (operation !in supportedOperations()) {
            return CapabilityResult.Failure(
                AdapterErrorMapper.invalidOperation(ADAPTER_NAME, operation, supportedOperations()),
            )
        }
        return AdapterBoundary.execute(logger, ADAPTER_NAME, operation, traceId) {
            withContext(Dispatchers.IO) {
                when (operation) {
                    StorageAccessOperations.BUILD_DOCUMENT_PICKER -> buildDocumentPicker(parameters)
                    StorageAccessOperations.TAKE_PERSISTABLE_PERMISSION -> takePersistablePermission(parameters)
                    StorageAccessOperations.OPEN_DOCUMENT -> openDocument(parameters)
                    StorageAccessOperations.QUERY_DOWNLOADS -> queryDownloads(parameters)
                    else -> error("unreachable")
                }
            }
        }
    }

    private fun buildDocumentPicker(parameters: Map<String, String>): Map<String, String> {
        val mimeTypes = parameters["mimeTypes"]?.split(",")?.filter { it.isNotBlank() }.orEmpty()
        val intent =
            Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = if (mimeTypes.size == 1) mimeTypes.first() else "*/*"
                if (mimeTypes.size > 1) {
                    putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes.toTypedArray())
                }
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            }
        return mapOf(
            "action" to intent.action.orEmpty(),
            "type" to (intent.type ?: "*/*"),
            "requiresActivityLaunch" to "true",
        )
    }

    private fun takePersistablePermission(parameters: Map<String, String>): Map<String, String> {
        val uri = Uri.parse(parameters.require("uri"))
        val mode = parameters["mode"] ?: "read"
        val flags =
            when (mode) {
                "write" -> Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                "readWrite" -> Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                else -> Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
        context.contentResolver.takePersistableUriPermission(uri, flags)
        return mapOf("uri" to uri.toString(), "mode" to mode, "status" to "persisted")
    }

    private fun openDocument(parameters: Map<String, String>): Map<String, String> {
        val uri = Uri.parse(parameters.require("uri"))
        val metadata = mutableMapOf<String, String>("uri" to uri.toString())
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameIndex >= 0) metadata["displayName"] = cursor.getString(nameIndex)
                if (sizeIndex >= 0) metadata["size"] = cursor.getLong(sizeIndex).toString()
            }
        }
        context.contentResolver.openInputStream(uri)?.use { stream ->
            metadata["readable"] = "true"
            metadata["availableBytes"] = stream.available().toString()
        } ?: throw IllegalStateException("Unable to open document stream")
        return metadata
    }

    private fun queryDownloads(parameters: Map<String, String>): Map<String, String> {
        val limit = parameters["limit"]?.toIntOrNull() ?: 50
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) {
            return mapOf("count" to "0", "documents" to "")
        }
        val items = mutableListOf<String>()
        context.contentResolver.query(
            android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            arrayOf(
                android.provider.MediaStore.Downloads._ID,
                android.provider.MediaStore.Downloads.DISPLAY_NAME,
                android.provider.MediaStore.Downloads.MIME_TYPE,
            ),
            null,
            null,
            "${android.provider.MediaStore.Downloads.DATE_ADDED} DESC",
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Downloads._ID)
            val nameIndex = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Downloads.DISPLAY_NAME)
            val mimeIndex = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Downloads.MIME_TYPE)
            var count = 0
            while (cursor.moveToNext() && count < limit) {
                items.add(
                    listOf(
                        cursor.getLong(idIndex),
                        cursor.getString(nameIndex),
                        cursor.getString(mimeIndex),
                    ).joinToString(":"),
                )
                count++
            }
        }
        return mapOf("count" to items.size.toString(), "documents" to items.joinToString("|"))
    }

    private fun Map<String, String>.require(key: String): String =
        this[key]?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("Missing required parameter: $key")

    private companion object {
        const val ADAPTER_NAME = "StorageAccess"
    }
}

class StorageAccessAdapterStub : StorageAccessAdapter {
    override suspend fun execute(
        operation: String,
        parameters: Map<String, String>,
        traceId: UUID,
    ): CapabilityResult = CapabilityResult.Success(mapOf("stub" to "true", "operation" to operation))

    override fun supportedOperations(): Set<String> =
        setOf(
            StorageAccessOperations.BUILD_DOCUMENT_PICKER,
            StorageAccessOperations.TAKE_PERSISTABLE_PERMISSION,
            StorageAccessOperations.OPEN_DOCUMENT,
            StorageAccessOperations.QUERY_DOWNLOADS,
        )
}
