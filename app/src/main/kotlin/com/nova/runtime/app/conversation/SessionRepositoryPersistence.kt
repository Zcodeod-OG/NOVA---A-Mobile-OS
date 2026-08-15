package com.nova.runtime.app.conversation

import com.nova.runtime.conversation.session.ConversationSessionRecord
import com.nova.runtime.conversation.session.SessionPersistence
import com.nova.runtime.conversation.state.ConversationState
import com.nova.runtime.storage.entities.SessionEntity
import com.nova.runtime.storage.repository.SessionRepository
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Bridges conversation session persistence to Room [SessionRepository]. */
class SessionRepositoryPersistence(
    private val sessionRepository: SessionRepository,
) : SessionPersistence {

    private val activeSessionIds = ConcurrentHashMap.newKeySet<UUID>()

    override suspend fun insert(session: ConversationSessionRecord) {
        sessionRepository.insert(session.toEntity())
        trackActive(session)
    }

    override suspend fun update(session: ConversationSessionRecord) {
        sessionRepository.update(session.toEntity())
        trackActive(session)
    }

    override suspend fun getById(sessionId: UUID): ConversationSessionRecord? =
        sessionRepository.getById(sessionId)?.toRecord()?.also(::trackActive)

    override suspend fun getActiveSessions(): List<ConversationSessionRecord> =
        activeSessionIds.mapNotNull { sessionId ->
            sessionRepository.getById(sessionId)?.toRecord()?.takeIf { it.isActive }
        }.sortedByDescending { it.startedAt }

    private fun trackActive(session: ConversationSessionRecord) {
        if (session.isActive) {
            activeSessionIds.add(session.sessionId)
        } else {
            activeSessionIds.remove(session.sessionId)
        }
    }

    private fun ConversationSessionRecord.toEntity(): SessionEntity =
        SessionEntity(
            sessionId = sessionId,
            traceId = traceId,
            startedAt = startedAt.toEpochMilli(),
            endedAt = endedAt?.toEpochMilli(),
            state = state.name,
        )

    private fun SessionEntity.toRecord(): ConversationSessionRecord =
        ConversationSessionRecord(
            sessionId = sessionId,
            traceId = traceId,
            startedAt = Instant.ofEpochMilli(startedAt),
            endedAt = endedAt?.let(Instant::ofEpochMilli),
            state = runCatching { ConversationState.valueOf(state) }
                .getOrDefault(ConversationState.IDLE),
        )
}
