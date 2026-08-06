package com.nova.runtime.conversation

import com.nova.runtime.models.Modality
import com.nova.runtime.models.Observation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import java.time.Instant
import java.util.UUID

class ConversationServiceStub : ConversationService {
    override suspend fun startSession(): UUID = UUID.randomUUID()
    override suspend fun stopSession(sessionId: UUID) { /* TODO */ }

    override suspend fun receiveVoice(sessionId: UUID, audioPayload: ByteArray): Observation? = null

    override suspend fun receiveText(sessionId: UUID, text: String): Observation =
        Observation(
            id = UUID.randomUUID(),
            timestamp = Instant.now(),
            sessionId = sessionId,
            traceId = UUID.randomUUID(),
            modality = Modality.TEXT,
            payload = text,
        )

    override fun streamResponse(sessionId: UUID): Flow<String> = emptyFlow()
}
