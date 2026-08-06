package com.nova.runtime.app.action

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings

data class ActionExecutionResult(
    val isSuccess: Boolean,
    val appName: String,
    val message: String
)

open class AndroidActionExecutor(
    private val context: Context
) {

    open fun executeAction(rawPrompt: String): ActionExecutionResult {
        val prompt = rawPrompt.lowercase().trim()

        return when {
            // 1. Gmail / Email
            prompt.contains("gmail") || prompt.contains("email") || prompt.contains("mail") -> {
                launchAppOrIntent(
                    packageName = "com.google.android.gm",
                    fallbackIntent = Intent(Intent.ACTION_MAIN).apply {
                        addCategory(Intent.CATEGORY_APP_EMAIL)
                    },
                    appName = "Gmail"
                )
            }

            // 2. Camera
            prompt.contains("camera") || prompt.contains("photo") || prompt.contains("picture") -> {
                launchIntent(
                    intent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA),
                    appName = "Camera"
                )
            }

            // 3. Settings
            prompt.contains("setting") || prompt.contains("preference") -> {
                launchIntent(
                    intent = Intent(Settings.ACTION_SETTINGS),
                    appName = "Settings"
                )
            }

            // 4. YouTube
            prompt.contains("youtube") || prompt.contains("video") -> {
                launchAppOrIntent(
                    packageName = "com.google.android.youtube",
                    fallbackIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com")),
                    appName = "YouTube"
                )
            }

            // 5. Google Maps
            prompt.contains("map") || prompt.contains("navigation") || prompt.contains("location") -> {
                launchAppOrIntent(
                    packageName = "com.google.android.apps.maps",
                    fallbackIntent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0")),
                    appName = "Google Maps"
                )
            }

            // 6. Browser / Search
            prompt.contains("browser") || prompt.contains("chrome") || prompt.contains("google") || prompt.contains("web") -> {
                launchAppOrIntent(
                    packageName = "com.android.chrome",
                    fallbackIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com")),
                    appName = "Browser"
                )
            }

            // 7. Messages / SMS
            prompt.contains("message") || prompt.contains("sms") || prompt.contains("text") -> {
                launchAppOrIntent(
                    packageName = "com.google.android.apps.messaging",
                    fallbackIntent = Intent(Intent.ACTION_MAIN).apply {
                        addCategory(Intent.CATEGORY_APP_MESSAGING)
                    },
                    appName = "Messages"
                )
            }

            // 8. Clock / Alarm
            prompt.contains("clock") || prompt.contains("alarm") || prompt.contains("timer") -> {
                launchIntent(
                    intent = Intent(AlarmClock.ACTION_SHOW_ALARMS),
                    appName = "Clock"
                )
            }

            // 9. Phone / Call
            prompt.contains("phone") || prompt.contains("dial") || prompt.contains("call") -> {
                launchIntent(
                    intent = Intent(Intent.ACTION_DIAL),
                    appName = "Phone"
                )
            }

            // 10. Fallback: Installed App Search
            else -> {
                searchAndLaunchInstalledApp(prompt)
            }
        }
    }

    private fun launchAppOrIntent(packageName: String, fallbackIntent: Intent, appName: String): ActionExecutionResult {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName) ?: fallbackIntent
        return launchIntent(launchIntent, appName)
    }

    private fun launchIntent(intent: Intent, appName: String): ActionExecutionResult {
        return try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            ActionExecutionResult(
                isSuccess = true,
                appName = appName,
                message = "Successfully opened $appName"
            )
        } catch (e: Exception) {
            ActionExecutionResult(
                isSuccess = false,
                appName = appName,
                message = "Failed to open $appName: ${e.localizedMessage}"
            )
        }
    }

    private fun searchAndLaunchInstalledApp(prompt: String): ActionExecutionResult {
        return try {
            val pm = context.packageManager
            val packages = pm.getInstalledApplications(0)

            for (app in packages) {
                val appLabel = pm.getApplicationLabel(app).toString().lowercase()
                if (prompt.contains(appLabel) && appLabel.length >= 3) {
                    val intent = pm.getLaunchIntentForPackage(app.packageName)
                    if (intent != null) {
                        return launchIntent(intent, pm.getApplicationLabel(app).toString())
                    }
                }
            }

            ActionExecutionResult(
                isSuccess = false,
                appName = "None",
                message = "No matching app found for command: '$prompt'"
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
