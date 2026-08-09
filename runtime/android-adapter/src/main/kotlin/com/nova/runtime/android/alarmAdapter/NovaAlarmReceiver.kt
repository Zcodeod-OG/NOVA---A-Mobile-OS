package com.nova.runtime.android.alarmAdapter

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.nova.runtime.utils.logging.NoOpRuntimeLogger
import com.nova.runtime.utils.logging.NovaLogger

/** Receives AlarmManager callbacks — AIS §4.6. Posts a reminder notification when fired. */
class NovaAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val requestCode = intent.getIntExtra(EXTRA_REQUEST_CODE, -1)
        val label = intent.getStringExtra(EXTRA_LABEL)
        val logger: NovaLogger = NoOpRuntimeLogger()
        logger.info(
            module = "ANDROID_ADAPTER",
            message = "Alarm fired",
            metadata = mapOf(
                "requestCode" to requestCode.toString(),
                "action" to (intent.action ?: DEFAULT_ACTION),
                "label" to (label ?: ""),
            ),
        )
        postReminderNotification(context, requestCode, label)
    }

    private fun postReminderNotification(context: Context, requestCode: Int, label: String?) {
        try {
            ensureChannel(context)
            val notification =
                NotificationCompat.Builder(context, CHANNEL_ID)
                    .setContentTitle(if (label.isNullOrBlank()) "NOVA Alarm" else "NOVA Reminder")
                    .setContentText(label?.takeIf { it.isNotBlank() } ?: "Your alarm is going off.")
                    .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setCategory(NotificationCompat.CATEGORY_REMINDER)
                    .setAutoCancel(true)
                    .build()
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_BASE + requestCode, notification)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS not granted — the alarm still fired; nothing else to do.
        }
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "NOVA Reminders", NotificationManager.IMPORTANCE_HIGH),
        )
    }

    companion object {
        const val DEFAULT_ACTION = "com.nova.runtime.android.ALARM_FIRED"
        const val EXTRA_REQUEST_CODE = "requestCode"
        const val EXTRA_LABEL = "label"
        private const val CHANNEL_ID = "nova_reminders"
        private const val NOTIFICATION_ID_BASE = 40_000
    }
}
