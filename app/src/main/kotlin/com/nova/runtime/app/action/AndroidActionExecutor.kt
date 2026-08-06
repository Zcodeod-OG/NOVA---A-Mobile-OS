package com.nova.runtime.app.action

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import java.net.URLEncoder

data class ActionExecutionResult(
    val isSuccess: Boolean,
    val appName: String,
    val message: String
)

open class AndroidActionExecutor(
    private val context: Context
) {
    private val intentEngine = CognitiveIntentEngine()
    private val fileShareManager = FileShareManager(context)
    private var isTorchOn = false

    open fun executeAction(rawPrompt: String): ActionExecutionResult {
        val parsedIntent = intentEngine.parseIntent(rawPrompt)

        return when (parsedIntent.action) {
            SemanticAction.SHARE_DOCUMENT -> fileShareManager.shareDocumentOrFile(parsedIntent.queryOrContent ?: "file.pdf", parsedIntent.targetApp)
            SemanticAction.PLAY_MEDIA -> handlePlayMedia(parsedIntent)
            SemanticAction.SEND_MESSAGE -> handleSendMessage(parsedIntent)
            SemanticAction.MAKE_CALL -> handleMakeCall(parsedIntent)
            SemanticAction.TOGGLE_SETTING -> handleToggleSetting(parsedIntent)
            SemanticAction.NAVIGATE_SYSTEM -> handleNavigateSystem(parsedIntent)
            SemanticAction.SEARCH_CONTENT -> handleSearchContent(parsedIntent)
            SemanticAction.LAUNCH_APP -> handleLaunchApp(parsedIntent)
            SemanticAction.UNKNOWN -> handleUnknownAction(parsedIntent)
        }
    }

    private fun handlePlayMedia(intentData: ParsedIntent): ActionExecutionResult {
        val query = intentData.queryOrContent ?: intentData.originalPrompt
        val encoded = URLEncoder.encode(query, "UTF-8")

        return if (intentData.targetApp == "Spotify") {
            try {
                val spotifyIntent = Intent(Intent.ACTION_VIEW, Uri.parse("spotify:search:$encoded")).apply {
                    `package` = "com.spotify.music"
                }
                launchIntent(spotifyIntent, "Spotify", "Searching Spotify for '$query'")
            } catch (e: Exception) {
                val webUri = Uri.parse("https://open.spotify.com/search/$encoded")
                launchIntent(Intent(Intent.ACTION_VIEW, webUri), "Spotify Web", "Opened Spotify Web search for '$query'")
            }
        } else {
            val ytMusicUri = Uri.parse("https://music.youtube.com/search?q=$encoded")
            launchIntent(Intent(Intent.ACTION_VIEW, ytMusicUri), "YouTube Music", "Searching YouTube Music for '$query'")
        }
    }

    private fun handleSendMessage(intentData: ParsedIntent): ActionExecutionResult {
        val content = intentData.queryOrContent ?: intentData.originalPrompt
        return if (intentData.targetApp == "WhatsApp") {
            try {
                val whatsappIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    `package` = "com.whatsapp"
                    putExtra(Intent.EXTRA_TEXT, content)
                }
                launchIntent(whatsappIntent, "WhatsApp", "Opened WhatsApp with message '$content'")
            } catch (e: Exception) {
                val webUri = Uri.parse("https://api.whatsapp.com/send?text=${URLEncoder.encode(content, "UTF-8")}")
                launchIntent(Intent(Intent.ACTION_VIEW, webUri), "WhatsApp Web", "Opened WhatsApp Web link for message")
            }
        } else {
            val smsIntent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")).apply {
                putExtra("sms_body", content)
            }
            launchIntent(smsIntent, "Messages", "Opened SMS with message body '$content'")
        }
    }

    private fun handleMakeCall(intentData: ParsedIntent): ActionExecutionResult {
        val recipient = intentData.recipient ?: ""
        val cleanNumber = recipient.replace(Regex("[^0-9+]"), "")

        return if (intentData.targetApp == "WhatsApp") {
            val waIntent = context.packageManager.getLaunchIntentForPackage("com.whatsapp")
                ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://api.whatsapp.com"))
            launchIntent(waIntent, "WhatsApp Call", "Opening WhatsApp for voice/video contact")
        } else {
            val dialUri = if (cleanNumber.isNotBlank()) Uri.parse("tel:$cleanNumber") else Uri.parse("tel:")
            launchIntent(Intent(Intent.ACTION_DIAL, dialUri), "Phone Dialer", if (cleanNumber.isNotBlank()) "Opening dialer for $cleanNumber" else "Opening Phone Dialer")
        }
    }

    private fun handleToggleSetting(intentData: ParsedIntent): ActionExecutionResult {
        return when (intentData.targetApp) {
            "Flashlight" -> toggleFlashlight()
            "Wi-Fi" -> launchIntent(Intent(Settings.ACTION_WIFI_SETTINGS), "Wi-Fi Settings", "Opened Wi-Fi Settings")
            "Bluetooth" -> launchIntent(Intent(Settings.ACTION_BLUETOOTH_SETTINGS), "Bluetooth Settings", "Opened Bluetooth Settings")
            "Sound" -> launchIntent(Intent(Settings.ACTION_SOUND_SETTINGS), "Sound Settings", "Opened Sound & Volume Settings")
            else -> launchIntent(Intent(Settings.ACTION_SETTINGS), "Settings", "Opened System Settings")
        }
    }

    private fun toggleFlashlight(): ActionExecutionResult {
        return try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            val cameraId = cameraManager?.cameraIdList?.firstOrNull()
            if (cameraManager != null && cameraId != null) {
                isTorchOn = !isTorchOn
                cameraManager.setTorchMode(cameraId, isTorchOn)
                ActionExecutionResult(
                    isSuccess = true,
                    appName = "Flashlight",
                    message = "Toggled Flashlight ${if (isTorchOn) "ON" else "OFF"}"
                )
            } else {
                ActionExecutionResult(false, "Flashlight", "Flashlight hardware unavailable")
            }
        } catch (e: Exception) {
            launchIntent(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA), "Camera Flashlight", "Opened Camera for Flashlight control")
        }
    }

    private fun handleNavigateSystem(intentData: ParsedIntent): ActionExecutionResult {
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
        }
        return launchIntent(homeIntent, "System Navigation", "Navigated to Android Home Screen")
    }

    private fun handleSearchContent(intentData: ParsedIntent): ActionExecutionResult {
        val query = intentData.queryOrContent ?: intentData.originalPrompt
        val encoded = URLEncoder.encode(query, "UTF-8")

        return when (intentData.targetApp) {
            "YouTube" -> launchIntent(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=$encoded")), "YouTube Search", "Searching YouTube for '$query'")
            "Google Maps" -> launchIntent(Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=$encoded")), "Google Maps", "Navigating to '$query'")
            "Play Store" -> launchIntent(Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=$encoded")), "Play Store", "Searching Play Store for '$query'")
            else -> launchIntent(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=$encoded")), "Google Search", "Searching web for '$query'")
        }
    }

    private fun handleLaunchApp(intentData: ParsedIntent): ActionExecutionResult {
        val appName = intentData.targetApp ?: intentData.originalPrompt
        return smartLaunchApp(appName)
    }

    private fun handleUnknownAction(intentData: ParsedIntent): ActionExecutionResult {
        return smartLaunchApp(intentData.originalPrompt)
    }

    private fun smartLaunchApp(appNameOrPrompt: String): ActionExecutionResult {
        val cleanName = appNameOrPrompt.lowercase().trim()

        // 1. Direct High-Priority Intent Mapping for Core System & Social Apps
        val directIntent = when {
            cleanName.contains("gmail") || cleanName.contains("mail") || cleanName.contains("email") -> {
                val pmLaunch = context.packageManager.getLaunchIntentForPackage("com.google.android.gm")
                pmLaunch ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://mail.google.com"))
            }
            cleanName.contains("camera") || cleanName.contains("photo") || cleanName.contains("picture") -> {
                Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
            }
            cleanName.contains("youtube") || cleanName.contains("video") -> {
                val pmLaunch = context.packageManager.getLaunchIntentForPackage("com.google.android.youtube")
                pmLaunch ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com"))
            }
            cleanName.contains("map") || cleanName.contains("navigation") || cleanName.contains("direction") -> {
                Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=places"))
            }
            cleanName.contains("chrome") || cleanName.contains("browser") || cleanName.contains("web") -> {
                Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com"))
            }
            cleanName.contains("setting") -> {
                Intent(Settings.ACTION_SETTINGS)
            }
            cleanName.contains("clock") || cleanName.contains("alarm") || cleanName.contains("timer") -> {
                Intent(AlarmClock.ACTION_SHOW_ALARMS)
            }
            cleanName.contains("phone") || cleanName.contains("dialer") -> {
                Intent(Intent.ACTION_DIAL)
            }
            cleanName.contains("message") || cleanName.contains("sms") -> {
                Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_APP_MESSAGING) }
            }
            cleanName.contains("whatsapp") -> {
                val pmLaunch = context.packageManager.getLaunchIntentForPackage("com.whatsapp")
                pmLaunch ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://api.whatsapp.com"))
            }
            cleanName.contains("spotify") || cleanName.contains("music") -> {
                val pmLaunch = context.packageManager.getLaunchIntentForPackage("com.spotify.music")
                pmLaunch ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://open.spotify.com"))
            }
            else -> null
        }

        if (directIntent != null) {
            return launchIntent(directIntent, appNameOrPrompt, "Opened $appNameOrPrompt")
        }

        // 2. Installed Package Matching Fallback
        return searchAndLaunchInstalledApp(cleanName)
    }

    private fun launchIntent(intent: Intent, appName: String, successMessage: String = "Successfully executed $appName"): ActionExecutionResult {
        return try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            ActionExecutionResult(
                isSuccess = true,
                appName = appName,
                message = successMessage
            )
        } catch (e: Exception) {
            // Web fallback if native app intent failed
            val webFallback = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=${URLEncoder.encode(appName, "UTF-8")}")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(webFallback)
                ActionExecutionResult(
                    isSuccess = true,
                    appName = appName,
                    message = "Opened web search fallback for $appName"
                )
            } catch (ex: Exception) {
                ActionExecutionResult(
                    isSuccess = false,
                    appName = appName,
                    message = "Failed to execute $appName: ${e.localizedMessage}"
                )
            }
        }
    }

    private fun searchAndLaunchInstalledApp(prompt: String): ActionExecutionResult {
        return try {
            val pm = context.packageManager
            val packages = pm.getInstalledApplications(0)

            for (app in packages) {
                val appLabel = pm.getApplicationLabel(app).toString().lowercase()
                val pkgName = app.packageName.lowercase()

                if (prompt.contains(appLabel) || prompt.contains(pkgName) || appLabel.contains(prompt)) {
                    val intent = pm.getLaunchIntentForPackage(app.packageName)
                    if (intent != null) {
                        return launchIntent(intent, pm.getApplicationLabel(app).toString(), "Opened ${pm.getApplicationLabel(app)}")
                    }
                }
            }

            // Ultimate fallback to Web search so any command performs a visible action!
            val webFallback = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=${URLEncoder.encode(prompt, "UTF-8")}")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(webFallback)

            ActionExecutionResult(
                isSuccess = true,
                appName = prompt,
                message = "Executed action & opened web search for '$prompt'"
            )
        } catch (e: Exception) {
            ActionExecutionResult(
                isSuccess = false,
                appName = "None",
                message = "Action executed for '$prompt'"
            )
        }
    }
}
