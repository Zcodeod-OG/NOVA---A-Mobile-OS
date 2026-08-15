package com.nova.runtime.android.capability.provider

import android.content.Context
import com.nova.runtime.android.AndroidAdapterLayer
import com.nova.runtime.android.email.GmailOAuthManager
import com.nova.runtime.android.email.GmailSyncService
import com.nova.runtime.capability.provider.CapabilityProvider
import com.nova.runtime.storage.entities.MessageEntity
import com.nova.runtime.storage.repository.MessageRepository
import com.nova.runtime.utils.logging.NovaLogger
import java.util.UUID

/** Test helper wiring production providers with an in-memory message repository. */
fun productionCapabilityProvidersForTest(
    context: Context,
    adapters: AndroidAdapterLayer,
    logger: NovaLogger,
    messageRepository: MessageRepository = InMemoryMessageRepository(),
): List<CapabilityProvider> {
    val oauthManager = GmailOAuthManager(context)
    val gmailSyncService = GmailSyncService(oauthManager, messageRepository, logger)
    return productionCapabilityProviders(
        context = context,
        adapters = adapters,
        logger = logger,
        oauthManager = oauthManager,
        gmailSyncService = gmailSyncService,
        messageRepository = messageRepository,
    )
}

class InMemoryMessageRepository : MessageRepository {
    private val messages = linkedMapOf<UUID, MessageEntity>()

    override suspend fun insert(message: MessageEntity): Boolean {
        if (messages.values.any { it.externalId != null && it.externalId == message.externalId }) {
            return false
        }
        messages[message.id] = message
        return true
    }

    override suspend fun update(message: MessageEntity) {
        messages[message.id] = message
    }

    override suspend fun getById(id: UUID): MessageEntity? = messages[id]

    override suspend fun getByExternalId(externalId: String): MessageEntity? =
        messages.values.firstOrNull { it.externalId == externalId }

    override suspend fun listByChannel(channel: String, limit: Int): List<MessageEntity> =
        messages.values.filter { it.channel == channel }
            .sortedByDescending { it.receivedAt }
            .take(limit)

    override suspend fun listUnindexed(limit: Int): List<MessageEntity> =
        messages.values.filter { it.indexedAt == null && it.body.isNotBlank() }
            .sortedByDescending { it.receivedAt }
            .take(limit)

    override suspend fun search(channel: String, query: String, limit: Int): List<MessageEntity> =
        messages.values.filter { message ->
            message.channel == channel &&
                (
                    message.body.contains(query, ignoreCase = true) ||
                        message.sender.contains(query, ignoreCase = true) ||
                        message.subject.orEmpty().contains(query, ignoreCase = true)
                    )
        }.sortedByDescending { it.receivedAt }.take(limit)

    override suspend fun countByChannel(channel: String): Int =
        messages.values.count { it.channel == channel }

    override suspend fun getRecent(limit: Int): List<MessageEntity> =
        messages.values.sortedByDescending { it.receivedAt }.take(limit)

    override suspend fun getHighImportance(limit: Int): List<MessageEntity> =
        messages.values.filter { it.importanceScore != null }
            .sortedByDescending { it.importanceScore ?: 0f }
            .take(limit)

    override suspend fun getSince(since: Long): List<MessageEntity> =
        messages.values.filter { it.receivedAt >= since }
            .sortedByDescending { it.receivedAt }
}
