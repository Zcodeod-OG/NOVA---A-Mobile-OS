package com.nova.runtime.android.mediaStoreAdapter

import android.content.Context
import android.os.Build
import android.provider.MediaStore
import com.nova.runtime.android.internal.AdapterBoundary
import com.nova.runtime.android.internal.AdapterErrorMapper
import com.nova.runtime.android.internal.PermissionChecker
import com.nova.runtime.models.contracts.CapabilityResult
import com.nova.runtime.utils.logging.NovaLogger
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MediaStoreAdapterImpl(
    private val context: Context,
    private val logger: NovaLogger,
) : MediaStoreAdapter {
    override fun supportedOperations(): Set<String> =
        setOf(
            MediaStoreOperations.QUERY_IMAGES,
            MediaStoreOperations.QUERY_VIDEOS,
            MediaStoreOperations.QUERY_AUDIO,
        )

    override fun requiredPermissions(): Set<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            setOf(
                android.Manifest.permission.READ_MEDIA_IMAGES,
                android.Manifest.permission.READ_MEDIA_VIDEO,
                android.Manifest.permission.READ_MEDIA_AUDIO,
            )
        } else {
            setOf(android.Manifest.permission.READ_EXTERNAL_STORAGE)
        }

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
                ensureMediaPermission(operation)
                when (operation) {
                    MediaStoreOperations.QUERY_IMAGES -> queryMedia(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, parameters)
                    MediaStoreOperations.QUERY_VIDEOS -> queryMedia(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, parameters)
                    MediaStoreOperations.QUERY_AUDIO -> queryMedia(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, parameters)
                    else -> error("unreachable")
                }
            }
        }
    }

    private fun ensureMediaPermission(operation: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permission =
                when (operation) {
                    MediaStoreOperations.QUERY_IMAGES -> android.Manifest.permission.READ_MEDIA_IMAGES
                    MediaStoreOperations.QUERY_VIDEOS -> android.Manifest.permission.READ_MEDIA_VIDEO
                    MediaStoreOperations.QUERY_AUDIO -> android.Manifest.permission.READ_MEDIA_AUDIO
                    else -> android.Manifest.permission.READ_MEDIA_IMAGES
                }
            PermissionChecker.ensureGranted(context, permission)
        } else {
            PermissionChecker.ensureGranted(context, android.Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    private fun queryMedia(
        collection: android.net.Uri,
        parameters: Map<String, String>,
    ): Map<String, String> {
        val limit = parameters["limit"]?.toIntOrNull() ?: 50
        val offset = parameters["offset"]?.toIntOrNull()?.coerceAtLeast(0) ?: 0
        val projection =
            arrayOf(
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.MIME_TYPE,
                MediaStore.MediaColumns.DATE_ADDED,
                MediaStore.MediaColumns.SIZE,
            )
        val items = mutableListOf<String>()
        context.contentResolver.query(
            collection,
            projection,
            null,
            null,
            "${MediaStore.MediaColumns.DATE_ADDED} DESC",
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val mimeIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
            val dateIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
            val sizeIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            var count = 0
            var skipped = 0
            while (cursor.moveToNext()) {
                if (skipped < offset) {
                    skipped++
                    continue
                }
                if (count >= limit) break
                val id = cursor.getLong(idIndex)
                val uri = android.content.ContentUris.withAppendedId(collection, id).toString()
                items.add(
                    listOf(
                        id,
                        uri,
                        cursor.getString(nameIndex),
                        cursor.getString(mimeIndex),
                        cursor.getLong(dateIndex),
                        cursor.getLong(sizeIndex),
                    ).joinToString(":"),
                )
                count++
            }
        } ?: throw IllegalStateException("MediaStore unavailable")
        return mapOf("count" to items.size.toString(), "items" to items.joinToString("|"))
    }

    private companion object {
        const val ADAPTER_NAME = "MediaStore"
    }
}

class MediaStoreAdapterStub : MediaStoreAdapter {
    override suspend fun execute(
        operation: String,
        parameters: Map<String, String>,
        traceId: UUID,
    ): CapabilityResult = CapabilityResult.Success(mapOf("stub" to "true", "operation" to operation))

    override fun supportedOperations(): Set<String> =
        setOf(
            MediaStoreOperations.QUERY_IMAGES,
            MediaStoreOperations.QUERY_VIDEOS,
            MediaStoreOperations.QUERY_AUDIO,
        )

    override fun requiredPermissions(): Set<String> = emptySet()
}
