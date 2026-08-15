package com.nova.runtime.policy.events

import com.nova.runtime.events.EventBus
import com.nova.runtime.events.RuntimeEvent
import com.nova.runtime.events.policy.PolicyEvents
import com.nova.runtime.models.EventPriority
import com.nova.runtime.models.RuntimeModule
import java.util.UUID

class PolicyEventPublisher(
    private val eventBus: EventBus,
) {
    suspend fun publishEvaluationStarted(traceId: UUID, graphId: UUID?, nodeId: UUID?) {
        publish(
            eventType = PolicyEvents.EVALUATION_STARTED,
            traceId = traceId,
            payload = buildMap {
                graphId?.let { put("graphId", it.toString()) }
                nodeId?.let { put("nodeId", it.toString()) }
            },
        )
    }

    suspend fun publishEvaluationCompleted(
        traceId: UUID,
        decisionType: String,
        rationale: String,
        nodeId: UUID? = null,
    ) {
        publish(
            eventType = PolicyEvents.EVALUATION_COMPLETED,
            traceId = traceId,
            payload = buildMap {
                put("decisionType", decisionType)
                put("rationale", rationale)
                nodeId?.let { put("nodeId", it.toString()) }
            },
        )
    }

    suspend fun publishActionDenied(traceId: UUID, nodeId: UUID, rationale: String) {
        publish(
            eventType = PolicyEvents.ACTION_DENIED,
            traceId = traceId,
            payload = mapOf(
                "nodeId" to nodeId.toString(),
                "rationale" to rationale,
            ),
            priority = EventPriority.HIGH,
        )
    }

    suspend fun publishConfirmationRequired(traceId: UUID, nodeId: UUID, rationale: String) {
        publish(
            eventType = PolicyEvents.CONFIRMATION_REQUIRED,
            traceId = traceId,
            payload = mapOf(
                "nodeId" to nodeId.toString(),
                "rationale" to rationale,
            ),
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
                sourceModule = RuntimeModule.POLICY,
                eventType = eventType,
                priority = priority,
                payload = payload,
            ),
        )
    }
}
