package com.nova.runtime.conversation.session

import java.util.UUID

/** Port for persisting session metadata without coupling to Room/Android. */
interface SessionPersistence {
    suspend fun insert(session: ConversationSessionRecord)

    suspend fun update(session: ConversationSessionRecord)

    suspend fun getById(sessionId: UUID): ConversationSessionRecord?

    suspend fun getActiveSessions(): List<ConversationSessionRecord>
}
