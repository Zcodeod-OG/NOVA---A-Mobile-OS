package com.nova.runtime.app.action

import android.content.Context
import android.content.Intent
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

    open fun executeAction(rawPrompt: String): ActionExecutionResult {
        val prompt = rawPrompt.trim()
        val lowerPrompt = prompt.lowercase()

        return when {
            // 1. YouTube Video Search & Playback
            lowerPrompt.contains("youtube") || lowerPrompt.contains("play ") || lowerPrompt.contains("watch ") || lowerPrompt.contains("video") -> {
                val searchQuery = extractQuery(prompt, listOf("play", "watch", "youtube", "video", "on", "search"))
                if (searchQuery.isNotBlank()) {
                    val encoded = URLEncoder.encode(searchQuery, "UTF-8")
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=$encoded"))
                    launchIntent(intent, "YouTube Search", "Searching YouTube for '$searchQuery'")
                } else {
                    launchAppOrIntent("com.google.android.youtube", Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com")), "YouTube")
                }
            }

            // 2. Phone Calling / Dialing
            lowerPrompt.startsWith("call") || lowerPrompt.startsWith("dial") || lowerPrompt.contains("phone call") -> {
                val numberOrContact = extractQuery(prompt, listOf("call", "dial", "phone", "number", "to"))
                val cleanNumber = numberOrContact.replace(Regex("[^0-9+]"), "")
                val dialUri = if (cleanNumber.isNotBlank()) Uri.parse("tel:$cleanNumber") else Uri.parse("tel:")
                val intent = Intent(Intent.ACTION_DIAL, dialUri)
                launchIntent(intent, "Phone Dialer", if (cleanNumber.isNotBlank()) "Opening dialer for $cleanNumber" else "Opening Phone Dialer")
            }

            // 3. Navigation & Directions
            lowerPrompt.contains("navigate") || lowerPrompt.contains("direction") || lowerPrompt.contains("where is") -> {
                val destination = extractQuery(prompt, listOf("navigate", "to", "directions", "where", "is", "get", "find"))
                if (destination.isNotBlank()) {
                    val encoded = URLEncoder.encode(destination, "UTF-8")
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=$encoded"))
                    launchIntent(intent, "Google Maps Navigation", "Navigating to '$destination'")
                } else {
                    launchAppOrIntent("com.google.android.apps.maps", Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0")), "Google Maps")
                }
            }

            // 4. SMS / Text Messaging
            lowerPrompt.startsWith("text") || lowerPrompt.startsWith("send message") || lowerPrompt.startsWith("sms") -> {
                val messageText = extractQuery(prompt, listOf("text", "send", "message", "sms", "to"))
                val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")).apply {
                    putExtra("sms_body", messageText)
                }
                launchIntent(intent, "Messages", if (messageText.isNotBlank()) "Opening SMS with message '$messageText'" else "Opening Messaging")
            }

            // 5. Play Store App Downloads
            lowerPrompt.contains("download") || lowerPrompt.contains("install") || lowerPrompt.contains("play store") -> {
                val appName = extractQuery(prompt, listOf("download", "install", "play", "store", "app", "get"))
                if (appName.isNotBlank()) {
                    val encoded = URLEncoder.encode(appName, "UTF-8")
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=$encoded"))
                    launchIntent(intent, "Google Play Store", "Searching Play Store for '$appName'")
                } else {
                    launchAppOrIntent("com.android.vending", Intent(Intent.ACTION_VIEW, Uri.parse("market://details")), "Play Store")
                }
            }

            // 6. Web Search / Google
            lowerPrompt.startsWith("search") || lowerPrompt.startsWith("google") || lowerPrompt.startsWith("find") -> {
                val searchQuery = extractQuery(prompt, listOf("search", "google", "find", "for", "on", "web"))
                if (searchQuery.isNotBlank()) {
                    val encoded = URLEncoder.encode(searchQuery, "UTF-8")
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=$encoded"))
                    launchIntent(intent, "Google Search", "Searching web for '$searchQuery'")
                } else {
                    launchAppOrIntent("com.android.chrome", Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com")), "Browser")
                }
            }

            // 7. Gmail / Email
            lowerPrompt.contains("gmail") || lowerPrompt.contains("email") || lowerPrompt.contains("mail") -> {
                launchAppOrIntent(
                    packageName = "com.google.android.gm",
                    fallbackIntent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_APP_EMAIL) },
                    appName = "Gmail"
                )
            }

            // 8. Camera
            lowerPrompt.contains("camera") || lowerPrompt.contains("photo") || lowerPrompt.contains("picture") -> {
                launchIntent(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA), "Camera", "Opening Camera")
            }

            // 9. Settings
            lowerPrompt.contains("setting") || lowerPrompt.contains("preference") -> {
                launchIntent(Intent(Settings.ACTION_SETTINGS), "Settings", "Opening System Settings")
            }

            // 10. Clock / Alarm
            lowerPrompt.contains("clock") || lowerPrompt.contains("alarm") || lowerPrompt.contains("timer") -> {
                launchIntent(Intent(AlarmClock.ACTION_SHOW_ALARMS), "Clock", "Opening Clock")
            }

            // 11. Fallback: Search Installed Applications
            else -> {
                searchAndLaunchInstalledApp(prompt)
            }
        }
    }

    private fun extractQuery(fullPrompt: String, stopWords: List<String>): String {
        val words = fullPrompt.split("\\s+".toRegex())
        val filtered = words.filter { word -> word.lowercase().trim() !in stopWords }
        return filtered.joinToString(" ").trim()
    }

    private fun launchAppOrIntent(packageName: String, fallbackIntent: Intent, appName: String): ActionExecutionResult {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName) ?: fallbackIntent
        return launchIntent(launchIntent, appName, "Opened $appName")
    }

    private fun launchIntent(intent: Intent, appName: String, successMessage: String = "Successfully opened $appName"): ActionExecutionResult {
        return try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            ActionExecutionResult(
                isSuccess = true,
                appName = appName,
                message = successMessage
            )
        } catch (e: Exception) {
            ActionExecutionResult(
                isSuccess = false,
                appName = appName,
                message = "Failed to execute $appName action: ${e.localizedMessage}"
            )
        }
    }

    private fun searchAndLaunchInstalledApp(prompt: String): ActionExecutionResult {
        return try {
            val pm = context.packageManager
            val packages = pm.getInstalledApplications(0)

            for (app in packages) {
                val appLabel = pm.getApplicationLabel(app).toString().lowercase()
                if (prompt.lowercase().contains(appLabel) && appLabel.length >= 3) {
                    val intent = pm.getLaunchIntentForPackage(app.packageName)
                    if (intent != null) {
                        return launchIntent(intent, pm.getApplicationLabel(app).toString(), "Opened ${pm.getApplicationLabel(app)}")
                    }
                }
            }

            ActionExecutionResult(
                isSuccess = false,
                appName = "None",
                message = "No matching application found for: '$prompt'"
            )
        } catch (e: Exception) {
            ActionExecutionResult(
                isSuccess = false,
                appName = "None",
                message = "Action processed for command: '$prompt'"
            )
        }
    }
}
