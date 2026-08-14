package com.nova.runtime.android.notificationAdapter

import com.nova.runtime.storage.entities.MessageEntity
import com.nova.runtime.storage.repository.MessageRepository
import com.nova.runtime.utils.logging.NovaLogger
import java.util.UUID

/** Persists parsed WhatsApp notification previews into [MessageEntity]. */
class WhatsAppMessageIngestionService(
    private val messageRepository: MessageRepository,
    private val logger: NovaLogger,
) {
    suspend fun ingest(messages: List<ParsedWhatsAppMessage>) {
        var inserted = 0
        for (parsed in messages) {
            val entity =
                MessageEntity(
                    id = UUID.randomUUID(),
                    channel = parsed.channel,
                    sender = parsed.sender,
                    body = parsed.body,
                    threadKey = parsed.threadKey,
                    receivedAt = parsed.receivedAt,
                    externalId = parsed.externalId,
                )
            if (messageRepository.insert(entity)) {
                inserted++
            }
        }
        if (inserted > 0) {
            logger.info(
                module = "ANDROID_ADAPTER",
                message = "WhatsApp messages ingested from notifications",
                metadata = mapOf("inserted" to inserted.toString(), "total" to messages.size.toString()),
            )
        }
    }
}
