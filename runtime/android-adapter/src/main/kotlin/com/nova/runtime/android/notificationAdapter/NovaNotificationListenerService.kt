package com.nova.runtime.android.notificationAdapter

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/** Observes active notifications — AIS §4.7 (requires user-enabled listener). */
class NovaNotificationListenerService : NotificationListenerService() {
    override fun onListenerConnected() {
        connected = true
        refreshSnapshot()
    }

    override fun onListenerDisconnected() {
        connected = false
        NotificationListenerState.updateSnapshot(emptyList())
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        refreshSnapshot()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        refreshSnapshot()
    }

    private fun refreshSnapshot() {
        val entries =
            activeNotifications.orEmpty().map { notification ->
                val extras = notification.notification.extras
                listOf(
                    notification.id,
                    notification.packageName,
                    extras.getCharSequence("android.title")?.toString().orEmpty(),
                ).joinToString(":")
            }
        NotificationListenerState.updateSnapshot(entries)
    }

    companion object {
        @Volatile
        private var connected: Boolean = false

        fun isConnected(): Boolean = connected
    }
}
