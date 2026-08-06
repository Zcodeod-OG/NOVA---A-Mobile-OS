package com.nova.runtime.conversation.session

import com.nova.runtime.conversation.state.ConversationState
import com.nova.runtime.error.NovaErrors
import com.nova.runtime.error.NovaException
import com.nova.runtime.kernel.trace.TraceContextHolder
import com.nova.runtime.kernel.trace.TraceIdGenerator
import java.time.Instant
import java.util.UUID

class SessionManager(
    private val persistence: SessionPersistence,
    private val traceContextHolder: TraceContextHolder,
    private val traceIdGenerator: TraceIdGenerator,
) {
    suspend fun createSession(): ConversationSessionRecord {
        val traceId = traceContextHolder.current()?.traceId ?: traceIdGenerator.newTraceId()
        val session = ConversationSessionRecord(
            sessionId = UUID.randomUUID(),
            traceId = traceId,
            startedAt = Instant.now(),
            state = ConversationState.IDLE,
        )
        persistence.insert(session)
        return session
    }

    suspend fun resumeSession(sessionId: UUID? = null): ConversationSessionRecord {
        if (sessionId != null) {
            val existing = persistence.getById(sessionId)
                ?: throw NovaException(NovaErrors.internal("Session $sessionId not found"))
            if (!existing.isActive) {
                throw NovaException(NovaErrors.internal("Session $sessionId has ended"))
            }
            return existing
        }

        return persistence.getActiveSessions().firstOrNull()
            ?: createSession()
    }

    suspend fun endSession(sessionId: UUID): ConversationSessionRecord {
        val session = requireSession(sessionId)
        val ended = session.copy(
            endedAt = Instant.now(),
            state = ConversationState.IDLE,
        )
        persistence.update(ended)
        return ended
    }

    suspend fun updateState(sessionId: UUID, state: ConversationState): ConversationSessionRecord {
        val session = requireSession(sessionId)
        val updated = session.copy(state = state)
        persistence.update(updated)
        return updated
    }

    suspend fun incrementTurn(sessionId: UUID): ConversationSessionRecord {
        val session = requireSession(sessionId)
        val updated = session.copy(turnCount = session.turnCount + 1)
        persistence.update(updated)
        return updated
    }

    suspend fun requireSession(sessionId: UUID): ConversationSessionRecord =
        persistence.getById(sessionId)
            ?: throw NovaException(NovaErrors.internal("Session $sessionId not found"))
}
