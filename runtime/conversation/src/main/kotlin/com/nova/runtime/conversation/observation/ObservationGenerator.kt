package com.nova.runtime.conversation.observation

import com.nova.runtime.conversation.input.NormalizedInput
import com.nova.runtime.kernel.trace.TraceIdGenerator
import com.nova.runtime.models.Observation
import java.time.Instant
import java.util.UUID

class ObservationGenerator(
    private val traceIdGenerator: TraceIdGenerator,
) {
    fun fromInput(
        input: NormalizedInput,
        sessionTraceId: UUID,
        turnNumber: Int,
    ): Observation = Observation(
        id = UUID.randomUUID(),
        timestamp = Instant.now(),
        sessionId = input.sessionId,
        traceId = sessionTraceId,
        modality = input.modality,
        payload = input.text,
        metadata = mapOf(
            "turnNumber" to turnNumber.toString(),
            "source" to "conversation",
        ),
    )
}
