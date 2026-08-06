package com.nova.runtime.conversation.session

import com.nova.runtime.conversation.state.ConversationState
import java.time.Instant
import java.util.UUID

/** In-memory representation of an active or completed conversation session. */
data class ConversationSessionRecord(
    val sessionId: UUID,
    val traceId: UUID,
    val startedAt: Instant,
    val endedAt: Instant? = null,
    val state: ConversationState = ConversationState.IDLE,
    val turnCount: Int = 0,
) {
    val isActive: Boolean get() = endedAt == null
}
