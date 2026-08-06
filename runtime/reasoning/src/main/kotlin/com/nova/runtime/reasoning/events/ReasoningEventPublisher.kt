package com.nova.runtime.reasoning.events

import com.nova.runtime.events.EventBus
import com.nova.runtime.events.RuntimeEvent
import com.nova.runtime.events.reasoning.ReasoningEvents
import com.nova.runtime.models.EventPriority
import com.nova.runtime.models.RuntimeModule
import java.util.UUID

class ReasoningEventPublisher(
    private val eventBus: EventBus,
) {
    suspend fun publishStarted(traceId: UUID, goal: String) {
        publish(
            eventType = ReasoningEvents.STARTED,
            traceId = traceId,
            payload = mapOf("goal" to goal),
            priority = EventPriority.NORMAL,
        )
    }

    suspend fun publishEvidenceCollected(traceId: UUID, evidenceCount: Int, topSource: String?) {
        publish(
            eventType = ReasoningEvents.EVIDENCE_COLLECTED,
            traceId = traceId,
            payload = buildMap {
                put("evidenceCount", evidenceCount.toString())
                topSource?.let { put("topSource", it) }
            },
        )
    }

    suspend fun publishAmbiguityResolved(
        traceId: UUID,
        resolvedCount: Int,
        assumptionCount: Int,
    ) {
        publish(
            eventType = ReasoningEvents.AMBIGUITY_RESOLVED,
            traceId = traceId,
            payload = mapOf(
                "resolvedCount" to resolvedCount.toString(),
                "assumptionCount" to assumptionCount.toString(),
            ),
        )
    }

    suspend fun publishCompleted(traceId: UUID, confidence: Double, evidenceCount: Int) {
        publish(
            eventType = ReasoningEvents.COMPLETED,
            traceId = traceId,
            payload = mapOf(
                "confidence" to confidence.toString(),
                "evidenceCount" to evidenceCount.toString(),
            ),
            priority = EventPriority.NORMAL,
        )
    }

    private suspend fun publish(
        eventType: String,
        traceId: UUID,
        payload: Map<String, String>,
        priority: EventPriority = EventPriority.NORMAL,
    ) {
        eventBus.publish(
            RuntimeEvent(
                traceId = traceId,
                sourceModule = RuntimeModule.REASONING,
                eventType = eventType,
                priority = priority,
                payload = payload,
            ),
        )
    }
}
