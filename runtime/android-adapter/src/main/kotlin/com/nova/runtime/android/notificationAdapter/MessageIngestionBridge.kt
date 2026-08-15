package com.nova.runtime.android.notificationAdapter

/** Allows [NovaNotificationListenerService] to persist messages without direct DI. */
object MessageIngestionBridge {
    private var handler: suspend (List<ParsedWhatsAppMessage>) -> Unit = {}

    fun install(handler: suspend (List<ParsedWhatsAppMessage>) -> Unit) {
        this.handler = handler
    }

    suspend fun ingest(messages: List<ParsedWhatsAppMessage>) {
        if (messages.isEmpty()) return
        handler(messages)
    }
}
