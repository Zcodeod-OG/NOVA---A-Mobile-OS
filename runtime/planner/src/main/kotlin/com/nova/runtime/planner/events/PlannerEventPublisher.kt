package com.nova.runtime.planner.events

import com.nova.runtime.events.EventBus
import com.nova.runtime.events.RuntimeEvent
import com.nova.runtime.events.planner.PlannerEvents
import com.nova.runtime.models.EventPriority
import com.nova.runtime.models.RuntimeModule
import java.util.UUID

class PlannerEventPublisher(
    private val eventBus: EventBus,
) {
    suspend fun publishStarted(traceId: UUID, goal: String) {
        publish(
            eventType = PlannerEvents.PLANNING_STARTED,
            traceId = traceId,
            payload = mapOf("goal" to goal),
        )
    }

    suspend fun publishTasksGenerated(traceId: UUID, taskCount: Int, subGoalCount: Int) {
        publish(
            eventType = PlannerEvents.TASKS_GENERATED,
            traceId = traceId,
            payload = mapOf(
                "taskCount" to taskCount.toString(),
                "subGoalCount" to subGoalCount.toString(),
            ),
        )
    }

    suspend fun publishGraphBuilt(traceId: UUID, graphId: UUID, nodeCount: Int) {
        publish(
            eventType = PlannerEvents.GRAPH_BUILT,
            traceId = traceId,
            payload = mapOf(
                "graphId" to graphId.toString(),
                "nodeCount" to nodeCount.toString(),
            ),
        )
    }

    suspend fun publishGraphOptimized(traceId: UUID, nodesMerged: Int, nodesPruned: Int) {
        publish(
            eventType = PlannerEvents.GRAPH_OPTIMIZED,
            traceId = traceId,
            payload = mapOf(
                "nodesMerged" to nodesMerged.toString(),
                "nodesPruned" to nodesPruned.toString(),
            ),
        )
    }

    suspend fun publishCompleted(traceId: UUID, graphId: UUID, metrics: Map<String, String>) {
        publish(
            eventType = PlannerEvents.PLANNING_COMPLETED,
            traceId = traceId,
            payload = metrics + ("graphId" to graphId.toString()),
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
                sourceModule = RuntimeModule.PLANNER,
                eventType = eventType,
                priority = priority,
                payload = payload,
            ),
        )
    }
}
