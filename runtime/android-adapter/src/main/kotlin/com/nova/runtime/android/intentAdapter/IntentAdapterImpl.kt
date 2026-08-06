package com.nova.runtime.android.intentAdapter

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.nova.runtime.android.internal.AdapterBoundary
import com.nova.runtime.android.internal.AdapterErrorMapper
import com.nova.runtime.models.contracts.CapabilityResult
import com.nova.runtime.utils.logging.NovaLogger
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class IntentAdapterImpl(
    private val context: Context,
    private val logger: NovaLogger,
) : IntentAdapter {
    override fun supportedOperations(): Set<String> =
        setOf(
            IntentOperations.OPEN_APP,
            IntentOperations.SHARE,
            IntentOperations.VIEW_DOCUMENT,
            IntentOperations.DIAL,
            IntentOperations.LAUNCH_SETTINGS,
            IntentOperations.OPEN_URL,
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
            withContext(Dispatchers.Main) {
                when (operation) {
                    IntentOperations.OPEN_APP -> openApp(parameters)
                    IntentOperations.SHARE -> share(parameters)
                    IntentOperations.VIEW_DOCUMENT -> viewDocument(parameters)
                    IntentOperations.DIAL -> dial(parameters)
                    IntentOperations.LAUNCH_SETTINGS -> launchSettings(parameters)
                    IntentOperations.OPEN_URL -> openUrl(parameters)
                    else -> error("unreachable")
                }
            }
        }
    }

    private fun openApp(parameters: Map<String, String>): Map<String, String> {
        val packageName = parameters.require("packageName")
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
            ?: throw IllegalArgumentException("No launch intent for $packageName")
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(launchIntent)
        return mapOf("status" to "launched", "packageName" to packageName)
    }

    private fun share(parameters: Map<String, String>): Map<String, String> {
        val text = parameters["text"]
        val uri = parameters["uri"]
        val mimeType = parameters["mimeType"] ?: "text/plain"
        val packageName = parameters["packageName"]
        val intent =
            Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                text?.let { putExtra(Intent.EXTRA_TEXT, it) }
                uri?.let {
                    putExtra(Intent.EXTRA_STREAM, Uri.parse(it))
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                packageName?.let { setPackage(it) }
            }
        val launchIntent =
            if (packageName.isNullOrBlank()) {
                Intent.createChooser(intent, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            } else {
                intent
            }
        context.startActivity(launchIntent)
        return buildMap {
            put("status", "shared")
            packageName?.let { put("packageName", it) }
        }
    }

    private fun viewDocument(parameters: Map<String, String>): Map<String, String> {
        val uri = parameters.require("uri")
        val mimeType = parameters["mimeType"]
        val intent =
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.parse(uri), mimeType ?: "*/*")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        context.startActivity(intent)
        return mapOf("status" to "viewing", "uri" to uri)
    }

    private fun dial(parameters: Map<String, String>): Map<String, String> {
        val phoneNumber = parameters.require("phoneNumber")
        val intent =
            Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phoneNumber")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        context.startActivity(intent)
        return mapOf("status" to "dialed", "phoneNumber" to phoneNumber)
    }

    private fun launchSettings(parameters: Map<String, String>): Map<String, String> {
        val action = parameters["settingsAction"] ?: Settings.ACTION_SETTINGS
        val intent = Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        return mapOf("status" to "settingsOpened", "action" to action)
    }

    private fun openUrl(parameters: Map<String, String>): Map<String, String> {
        val url = parameters.require("url")
        val packageName = parameters["packageName"]
        val intent =
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                packageName?.let { setPackage(it) }
            }
        context.startActivity(intent)
        return buildMap {
            put("status", "opened")
            put("url", url)
            packageName?.let { put("packageName", it) }
        }
    }

    private fun Map<String, String>.require(key: String): String =
        this[key]?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("Missing required parameter: $key")

    private companion object {
        const val ADAPTER_NAME = "Intent"
    }
}

class IntentAdapterStub : IntentAdapter {
    override suspend fun execute(
        operation: String,
        parameters: Map<String, String>,
        traceId: UUID,
    ): CapabilityResult = CapabilityResult.Success(mapOf("stub" to "true", "operation" to operation))

    override fun supportedOperations(): Set<String> = IntentOperations.run {
        setOf(OPEN_APP, SHARE, VIEW_DOCUMENT, DIAL, LAUNCH_SETTINGS, OPEN_URL)
    }
}
