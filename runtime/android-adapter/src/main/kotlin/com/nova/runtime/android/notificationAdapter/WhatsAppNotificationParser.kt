package com.nova.runtime.android.notificationAdapter

import android.app.Notification
import android.os.Bundle
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat

/** Extracts WhatsApp message previews from notification extras. */
object WhatsAppNotificationParser {
    const val CHANNEL_WHATSAPP = "whatsapp"
    const val PACKAGE_WHATSAPP = "com.whatsapp"
    const val PACKAGE_WHATSAPP_BUSINESS = "com.whatsapp.w4b"

    private val WHATSAPP_PACKAGES = setOf(PACKAGE_WHATSAPP, PACKAGE_WHATSAPP_BUSINESS)

    fun isWhatsAppPackage(packageName: String): Boolean = packageName in WHATSAPP_PACKAGES

    fun parsePostedNotification(sbn: StatusBarNotification): List<ParsedWhatsAppMessage> {
        if (!isWhatsAppPackage(sbn.packageName)) return emptyList()
        val extras = sbn.notification.extras ?: return emptyList()
        val threadKey = buildThreadKey(sbn)
        val receivedAt = sbn.postTime
        val externalPrefix = "${sbn.packageName}:${sbn.id}:${sbn.tag.orEmpty()}"

        val messagingStyleMessages =
            parseMessagingStyleFromNotification(
                notification = sbn.notification,
                threadKey = threadKey,
                receivedAt = receivedAt,
                externalPrefix = externalPrefix,
            )
        if (messagingStyleMessages.isNotEmpty()) return messagingStyleMessages

        return parseTextExtras(
            extras = extras,
            threadKey = threadKey,
            receivedAt = receivedAt,
            externalPrefix = externalPrefix,
        )
    }

    fun parseMessagingStyleFromNotification(
        notification: Notification,
        threadKey: String,
        receivedAt: Long,
        externalPrefix: String,
    ): List<ParsedWhatsAppMessage> =
        parseMessagingStyle(notification, threadKey, receivedAt, externalPrefix)

    fun parseTextExtras(
        extras: Bundle,
        threadKey: String,
        receivedAt: Long,
        externalPrefix: String,
    ): List<ParsedWhatsAppMessage> {
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()
        val body = bigText.takeIf { it.isNotBlank() } ?: text
        if (body.isBlank()) return emptyList()

        return listOf(
            ParsedWhatsAppMessage(
                channel = CHANNEL_WHATSAPP,
                sender = title.ifBlank { "WhatsApp" },
                body = body.trim(),
                threadKey = threadKey,
                receivedAt = receivedAt,
                externalId = "$externalPrefix:${body.hashCode()}",
            ),
        )
    }

    private fun parseMessagingStyle(
        notification: Notification,
        threadKey: String,
        receivedAt: Long,
        externalPrefix: String,
    ): List<ParsedWhatsAppMessage> {
        val style = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification)
            ?: return emptyList()
        val conversationTitle = style.conversationTitle?.toString().orEmpty()
        return style.messages.mapIndexedNotNull { index, message ->
            val body = message.text?.toString()?.trim().orEmpty()
            if (body.isBlank()) return@mapIndexedNotNull null
            val sender =
                message.person?.name?.toString()?.takeIf { it.isNotBlank() }
                    ?: conversationTitle.ifBlank { "WhatsApp" }
            ParsedWhatsAppMessage(
                channel = CHANNEL_WHATSAPP,
                sender = sender,
                body = body,
                threadKey = threadKey,
                receivedAt = message.timestamp.takeIf { it > 0L } ?: receivedAt,
                externalId = "$externalPrefix:msg:$index:${body.hashCode()}",
            )
        }
    }

    private fun buildThreadKey(sbn: StatusBarNotification): String {
        val extras: Bundle = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val conversation = extras.getString("android.conversationId")
        return listOf(sbn.packageName, conversation.orEmpty(), title, sbn.tag.orEmpty())
            .filter { it.isNotBlank() }
            .joinToString(":")
    }
}

data class ParsedWhatsAppMessage(
    val channel: String,
    val sender: String,
    val body: String,
    val threadKey: String,
    val receivedAt: Long,
    val externalId: String,
)
