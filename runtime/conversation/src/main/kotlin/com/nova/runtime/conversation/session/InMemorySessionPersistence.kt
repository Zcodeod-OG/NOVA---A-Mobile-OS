package com.nova.runtime.conversation.session

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class InMemorySessionPersistence : SessionPersistence {
    private val sessions = ConcurrentHashMap<UUID, ConversationSessionRecord>()

    override suspend fun insert(session: ConversationSessionRecord) {
        sessions[session.sessionId] = session
    }

    override suspend fun update(session: ConversationSessionRecord) {
        sessions[session.sessionId] = session
    }

    override suspend fun getById(sessionId: UUID): ConversationSessionRecord? = sessions[sessionId]

    override suspend fun getActiveSessions(): List<ConversationSessionRecord> =
        sessions.values.filter { it.isActive }.sortedByDescending { it.startedAt }
}
