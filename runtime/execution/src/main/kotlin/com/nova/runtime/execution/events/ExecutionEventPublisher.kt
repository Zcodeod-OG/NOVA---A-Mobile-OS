package com.nova.runtime.execution.events

import com.nova.runtime.events.EventBus
import com.nova.runtime.events.RuntimeEvent
import com.nova.runtime.events.execution.ExecutionEvents
import com.nova.runtime.models.EventPriority
import com.nova.runtime.models.RuntimeModule
import java.util.UUID

class ExecutionEventPublisher(
    private val eventBus: EventBus,
) {
    suspend fun publishStarted(traceId: UUID, graphId: UUID, nodeCount: Int) {
        publish(
            eventType = ExecutionEvents.STARTED,
            traceId = traceId,
            payload = mapOf(
                "graphId" to graphId.toString(),
                "nodeCount" to nodeCount.toString(),
            ),
        )
    }

    suspend fun publishNodeScheduled(traceId: UUID, nodeId: UUID) {
        publish(
            eventType = ExecutionEvents.NODE_SCHEDULED,
            traceId = traceId,
            payload = mapOf("nodeId" to nodeId.toString()),
            priority = EventPriority.NORMAL,
        )
    }

    suspend fun publishNodeRunning(traceId: UUID, nodeId: UUID) {
        publish(
            eventType = ExecutionEvents.NODE_RUNNING,
            traceId = traceId,
            payload = mapOf("nodeId" to nodeId.toString()),
        )
    }

    suspend fun publishNodeCompleted(traceId: UUID, nodeId: UUID, latencyMs: Long) {
        publish(
            eventType = ExecutionEvents.NODE_COMPLETED,
            traceId = traceId,
            payload = mapOf(
                "nodeId" to nodeId.toString(),
                "latencyMs" to latencyMs.toString(),
            ),
        )
    }

    suspend fun publishNodeFailed(traceId: UUID, nodeId: UUID, errorCode: String) {
        publish(
            eventType = ExecutionEvents.NODE_FAILED,
            traceId = traceId,
            payload = mapOf(
                "nodeId" to nodeId.toString(),
                "errorCode" to errorCode,
            ),
            priority = EventPriority.HIGH,
        )
    }

    suspend fun publishNodeRolledBack(traceId: UUID, nodeId: UUID) {
        publish(
            eventType = ExecutionEvents.NODE_ROLLED_BACK,
            traceId = traceId,
            payload = mapOf("nodeId" to nodeId.toString()),
        )
    }

    suspend fun publishGraphCompleted(traceId: UUID, graphId: UUID, completedNodes: Int, durationMs: Long) {
        publish(
            eventType = ExecutionEvents.GRAPH_COMPLETED,
            traceId = traceId,
            payload = mapOf(
                "graphId" to graphId.toString(),
                "completedNodes" to completedNodes.toString(),
                "durationMs" to durationMs.toString(),
            ),
        )
    }

    suspend fun publishGraphFailed(
        traceId: UUID,
        graphId: UUID,
        errorCode: String,
        userVisibleMessage: String? = null,
    ) {
        publish(
            eventType = ExecutionEvents.GRAPH_FAILED,
            traceId = traceId,
            payload = buildMap {
                put("graphId", graphId.toString())
                put("errorCode", errorCode)
                userVisibleMessage?.takeIf { it.isNotBlank() }?.let { put("userVisibleMessage", it) }
            },
            priority = EventPriority.HIGH,
        )
    }

    suspend fun publishGraphCancelled(traceId: UUID, graphId: UUID) {
        publish(
            eventType = ExecutionEvents.GRAPH_CANCELLED,
            traceId = traceId,
            payload = mapOf("graphId" to graphId.toString()),
        )
    }

    suspend fun publishGraphPaused(traceId: UUID, graphId: UUID) {
        publish(
            eventType = ExecutionEvents.GRAPH_PAUSED,
            traceId = traceId,
            payload = mapOf("graphId" to graphId.toString(),
            ),
        )
    }

    suspend fun publishGraphResumed(traceId: UUID, graphId: UUID) {
        publish(
            eventType = ExecutionEvents.GRAPH_RESUMED,
            traceId = traceId,
            payload = mapOf("graphId" to graphId.toString()),
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
                sourceModule = RuntimeModule.EXECUTION,
                eventType = eventType,
                priority = priority,
                payload = payload,
            ),
        )
    }
}
