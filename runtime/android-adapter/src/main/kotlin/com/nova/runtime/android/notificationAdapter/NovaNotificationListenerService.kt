package com.nova.runtime.android.notificationAdapter

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Observes active notifications — AIS §4.7 (requires user-enabled listener). */
class NovaNotificationListenerService : NotificationListenerService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onListenerConnected() {
        connected = true
        refreshSnapshot()
    }

    override fun onListenerDisconnected() {
        connected = false
        NotificationListenerState.updateSnapshot(emptyList())
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn != null) {
            ingestWhatsAppNotification(sbn)
        }
        refreshSnapshot()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        refreshSnapshot()
    }

    private fun ingestWhatsAppNotification(sbn: StatusBarNotification) {
        val parsed = WhatsAppNotificationParser.parsePostedNotification(sbn)
        if (parsed.isEmpty()) return
        serviceScope.launch {
            runCatching { MessageIngestionBridge.ingest(parsed) }
        }
    }

    private fun refreshSnapshot() {
        val entries =
            activeNotifications.orEmpty().map { notification ->
                val extras = notification.notification.extras
                val title = extras.getCharSequence("android.title")?.toString().orEmpty()
                val text = extras.getCharSequence("android.text")?.toString().orEmpty()
                val bigText = extras.getCharSequence("android.bigText")?.toString().orEmpty()
                val body = bigText.takeIf { it.isNotBlank() } ?: text
                listOf(
                    notification.id,
                    notification.packageName,
                    title,
                    body,
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
