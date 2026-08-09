package com.nova.runtime.android.intentAdapter

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import com.nova.runtime.android.internal.AdapterBoundary
import com.nova.runtime.android.internal.AdapterErrorMapper
import com.nova.runtime.models.contracts.CapabilityResult
import com.nova.runtime.utils.logging.NovaLogger
import java.io.File
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
        val launchIntent = resolveLaunchIntent(packageName)
            ?: throw IllegalArgumentException("No launch intent for $packageName")
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(launchIntent)
        return mapOf("status" to "launched", "packageName" to packageName)
    }

    private fun resolveLaunchIntent(packageName: String): Intent? {
        context.packageManager.getLaunchIntentForPackage(packageName)?.let { return it }

        val probe = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setPackage(packageName)
        val match = context.packageManager.queryIntentActivities(probe, 0).firstOrNull()
            ?: return null
        return Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setClassName(match.activityInfo.packageName, match.activityInfo.name)
    }

    private fun share(parameters: Map<String, String>): Map<String, String> {
        val text = parameters["text"]
        val uri = parameters["uri"]
        val mimeType = parameters["mimeType"] ?: "text/plain"
        val packageName = parameters["packageName"]
        // WhatsApp-specific: phone@s.whatsapp.net opens the chat with the attachment
        // instead of the contact/"send to" picker.
        val jid = parameters["jid"]?.takeIf { it.isNotBlank() }
        val parsedUri = uri?.takeIf { it.isNotBlank() }?.let { toShareableUri(it) }
        val intent =
            Intent(Intent.ACTION_SEND).apply {
                type = if (parsedUri != null && mimeType == "text/plain") "*/*" else mimeType
                text?.let { putExtra(Intent.EXTRA_TEXT, it) }
                jid?.let { putExtra("jid", it) }
                if (parsedUri != null) {
                    putExtra(Intent.EXTRA_STREAM, parsedUri)
                    // ClipData + grant flags are required for WhatsApp to read content URIs.
                    clipData = ClipData.newUri(context.contentResolver, "shared", parsedUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    packageName?.takeIf { it.isNotBlank() }?.let { pkg ->
                        runCatching {
                            context.grantUriPermission(
                                pkg,
                                parsedUri,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION,
                            )
                        }
                    }
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
            parsedUri?.let { put("uri", it.toString()) }
            jid?.let { put("jid", it) }
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
        if (intent.resolveActivity(context.packageManager) == null) {
            throw IllegalStateException(
                "No activity to handle url" +
                    if (packageName.isNullOrBlank()) "" else " for package $packageName",
            )
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

    /** Converts file:// paths to FileProvider content URIs so WhatsApp can read them. */
    private fun toShareableUri(raw: String): Uri {
        val parsed = Uri.parse(raw)
        if (parsed.scheme != "file") return parsed
        val path = parsed.path ?: return parsed
        val file = File(path)
        if (!file.exists()) return parsed
        return runCatching {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file,
            )
        }.getOrElse { parsed }
    }

    private companion object {
        const val ADAPTER_NAME = "Intent"
    }
}

class IntentAdapterStub : IntentAdapter {
    override suspend fun execute(
        operation: String,
        parameters: Map<String, String>,
        traceId: UUID,
    ): CapabilityResult =
        CapabilityResult.Success(
            buildMap {
                put("stub", "true")
                put("operation", operation)
                when (operation) {
                    IntentOperations.SHARE -> {
                        put("status", "shared")
                        parameters["packageName"]?.let { put("packageName", it) }
                    }
                    IntentOperations.LAUNCH_SETTINGS -> put("status", "launched")
                    else -> Unit
                }
            },
        )

    override fun supportedOperations(): Set<String> = IntentOperations.run {
        setOf(OPEN_APP, SHARE, VIEW_DOCUMENT, DIAL, LAUNCH_SETTINGS, OPEN_URL)
    }
}
