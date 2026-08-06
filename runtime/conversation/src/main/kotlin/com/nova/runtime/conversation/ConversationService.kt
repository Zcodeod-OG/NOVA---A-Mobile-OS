package com.nova.runtime.conversation

import com.nova.runtime.models.Observation
import kotlinx.coroutines.flow.Flow
import java.util.UUID

interface ConversationService {
    suspend fun startSession(): UUID
    suspend fun stopSession(sessionId: UUID)
    suspend fun receiveVoice(sessionId: UUID, audioPayload: ByteArray): Observation?
    suspend fun receiveText(sessionId: UUID, text: String): Observation
    fun streamResponse(sessionId: UUID): Flow<String>
}
