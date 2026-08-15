package com.nova.runtime.inference.events

import com.nova.runtime.events.EventBus
import com.nova.runtime.events.RuntimeEvent
import com.nova.runtime.events.inference.InferenceEvents
import com.nova.runtime.inference.tier.InferenceTier
import com.nova.runtime.models.EventPriority
import com.nova.runtime.models.RuntimeModule
import java.util.UUID

class InferenceEventPublisher(
    private val eventBus: EventBus,
) {
    suspend fun publishStarted(traceId: UUID, promptLength: Int) {
        publish(
            eventType = InferenceEvents.INFERENCE_STARTED,
            traceId = traceId,
            payload = mapOf("promptLength" to promptLength.toString()),
            priority = EventPriority.HIGH,
        )
    }

    suspend fun publishTierSelected(traceId: UUID, tier: InferenceTier, reason: String) {
        publish(
            eventType = InferenceEvents.TIER_SELECTED,
            traceId = traceId,
            payload = mapOf(
                "tier" to tier.level.toString(),
                "tierLabel" to tier.label,
                "reason" to reason,
            ),
        )
    }

    suspend fun publishCompleted(traceId: UUID, tier: InferenceTier, latencyMs: Long) {
        publish(
            eventType = InferenceEvents.INFERENCE_COMPLETED,
            traceId = traceId,
            payload = mapOf(
                "tier" to tier.level.toString(),
                "latencyMs" to latencyMs.toString(),
            ),
            priority = EventPriority.HIGH,
        )
    }

    suspend fun publishFailed(traceId: UUID, code: String, tier: InferenceTier?) {
        publish(
            eventType = InferenceEvents.INFERENCE_FAILED,
            traceId = traceId,
            payload = buildMap {
                put("code", code)
                tier?.let { put("tier", it.level.toString()) }
            },
            priority = EventPriority.HIGH,
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
                sourceModule = RuntimeModule.INFERENCE,
                eventType = eventType,
                priority = priority,
                payload = payload,
            ),
        )
    }
}
