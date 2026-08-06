package com.nova.runtime.app.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

data class NovaNotificationItem(
    val packageName: String,
    val title: String,
    val text: String,
    val postTime: Long,
    val notification: Notification
)

class NovaNotificationListenerService : NotificationListenerService() {

    companion object {
        var instance: NovaNotificationListenerService? = null
            private set

        private val recentNotifications = mutableListOf<NovaNotificationItem>()

        fun getLatestNotification(): NovaNotificationItem? {
            return recentNotifications.lastOrNull()
        }

        fun getAllRecentNotifications(): List<NovaNotificationItem> {
            return recentNotifications.toList()
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return

        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: "Notification"
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""

        val item = NovaNotificationItem(
            packageName = sbn.packageName,
            title = title,
            text = text,
            postTime = sbn.postTime,
            notification = sbn.notification
        )

        synchronized(recentNotifications) {
            recentNotifications.add(item)
            if (recentNotifications.size > 50) {
                recentNotifications.removeAt(0)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) {
            instance = null
        }
    }
}
