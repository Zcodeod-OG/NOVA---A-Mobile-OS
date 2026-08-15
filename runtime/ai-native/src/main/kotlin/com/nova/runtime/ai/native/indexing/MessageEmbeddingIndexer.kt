package com.nova.runtime.ai.native.indexing

import com.nova.runtime.storage.entities.MessageEntity
import com.nova.runtime.storage.repository.MessageRepository

/** Background indexer that embeds ingested message bodies for semantic search. */
class MessageEmbeddingIndexer(
    private val messageRepository: MessageRepository,
    private val embeddingIndexer: EmbeddingIndexer,
) {
    suspend fun indexBatch(limit: Int = DEFAULT_BATCH_SIZE): Int {
        val pending = messageRepository.listUnindexed(limit)
        var indexed = 0
        for (message in pending) {
            if (message.body.isBlank()) continue
            val embeddingId =
                embeddingIndexer.indexText(
                    objectId = message.id,
                    objectType = OBJECT_TYPE_MESSAGE,
                    text = buildIndexText(message),
                    replaceExisting = true,
                )
            if (embeddingId != null) {
                messageRepository.update(message.copy(indexedAt = System.currentTimeMillis()))
                indexed++
            }
        }
        return indexed
    }

    private fun buildIndexText(message: MessageEntity): String =
        buildString {
            append("channel: ").append(message.channel).append('\n')
            append("from: ").append(message.sender).append('\n')
            message.subject?.takeIf { it.isNotBlank() }?.let { append("subject: ").append(it).append('\n') }
            append(message.body)
        }

    companion object {
        const val OBJECT_TYPE_MESSAGE = "message"
        const val DEFAULT_BATCH_SIZE = 32
    }
}
