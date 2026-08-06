package com.nova.runtime.understanding.events

import com.nova.runtime.events.EventBus
import com.nova.runtime.events.RuntimeEvent
import com.nova.runtime.events.understanding.UnderstandingEvents
import com.nova.runtime.models.EventPriority
import com.nova.runtime.models.Nir
import com.nova.runtime.models.Observation
import com.nova.runtime.models.RuntimeModule
import com.nova.runtime.understanding.entity.ExtractedEntity
import com.nova.runtime.understanding.intent.DetectedIntent
import com.nova.runtime.understanding.normalization.NormalizedObservation
import java.util.UUID

class UnderstandingEventPublisher(
    private val eventBus: EventBus,
) {
    suspend fun publishObservationNormalized(normalized: NormalizedObservation) {
        publish(
            eventType = UnderstandingEvents.OBSERVATION_NORMALIZED,
            traceId = normalized.observation.traceId,
            correlationId = normalized.observation.id,
            payload = mapOf(
                "observationId" to normalized.observation.id.toString(),
                "tokenCount" to normalized.tokens.size.toString(),
                "modality" to normalized.observation.modality.name,
            ),
        )
    }

    suspend fun publishIntentDetected(intent: DetectedIntent, observation: Observation) {
        publish(
            eventType = UnderstandingEvents.INTENT_DETECTED,
            traceId = observation.traceId,
            correlationId = observation.id,
            payload = mapOf(
                "goal" to intent.goal,
                "intentType" to intent.intentType,
                "confidence" to intent.confidence.toString(),
            ),
            priority = EventPriority.HIGH,
        )
    }

    suspend fun publishEntityResolved(entities: List<ExtractedEntity>, observation: Observation) {
        publish(
            eventType = UnderstandingEvents.ENTITY_RESOLVED,
            traceId = observation.traceId,
            correlationId = observation.id,
            payload = mapOf(
                "entityCount" to entities.size.toString(),
                "entities" to entities.map { "${it.type}:${it.value}" },
            ),
        )
    }

    suspend fun publishConstraintExtracted(constraints: Map<String, String>, observation: Observation) {
        publish(
            eventType = UnderstandingEvents.CONSTRAINT_EXTRACTED,
            traceId = observation.traceId,
            correlationId = observation.id,
            payload = constraints,
        )
    }

    suspend fun publishNirGenerated(nir: Nir, observation: Observation) {
        publish(
            eventType = UnderstandingEvents.NIR_GENERATED,
            traceId = observation.traceId,
            correlationId = observation.id,
            payload = nir,
            priority = EventPriority.HIGH,
        )
    }

    private suspend fun publish(
        eventType: String,
        traceId: UUID,
        correlationId: UUID? = null,
        payload: Any? = null,
        priority: EventPriority = EventPriority.NORMAL,
    ) {
        eventBus.publish(
            RuntimeEvent(
                traceId = traceId,
                correlationId = correlationId,
                sourceModule = RuntimeModule.UNDERSTANDING,
                eventType = eventType,
                priority = priority,
                payload = payload,
            ),
        )
    }
}
