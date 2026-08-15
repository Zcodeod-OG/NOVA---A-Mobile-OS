package com.nova.runtime.planner.graph

import com.nova.runtime.models.ActionNode
import com.nova.runtime.models.Nag
import com.nova.runtime.models.Priority
import com.nova.runtime.planner.model.PlanTask
import com.nova.runtime.planner.model.SubGoal
import com.nova.runtime.planner.util.DeterministicIds
import java.util.UUID

interface ActionGraphBuilder {
    fun build(
        graphId: UUID,
        subGoals: List<SubGoal>,
        tasks: List<PlanTask>,
        traceId: UUID,
    ): Nag
}

class DefaultActionGraphBuilder : ActionGraphBuilder {
    override fun build(
        graphId: UUID,
        subGoals: List<SubGoal>,
        tasks: List<PlanTask>,
        traceId: UUID,
    ): Nag {
        val sortedTasks = tasks.sortedWith(compareBy({ it.sortOrder }, { it.key }))

        val actionNodes = sortedTasks.map { task ->
            ActionNode(
                id = DeterministicIds.uuid("action", "${traceId}:${task.key}"),
                actionType = task.actionType,
                inputs = task.inputs,
                outputs = mapOf("taskKey" to task.key),
                dependencies = emptyList(),
                timeoutMs = defaultTimeoutMs(task.actionType),
                retryPolicy = "none",
                rollbackPolicy = "none",
                executionPriority = priorityFor(task.actionType),
            )
        }

        return Nag(
            graphId = graphId,
            metadata = mapOf(
                "traceId" to traceId.toString(),
                "version" to "1",
            ),
            taskHierarchy = subGoals.sortedBy { it.sortOrder }.map { it.key },
            actionNodes = actionNodes,
            dependencies = actionNodes.associate { node -> node.id to emptyList<UUID>() },
            executionPolicies = mapOf("scheduling" to "parallel_when_ready"),
        )
    }

    private fun defaultTimeoutMs(actionType: String): Long = when (actionType) {
        "execute_capability" -> 30_000L
        "prepare_capability" -> 10_000L
        "validate_assumption" -> 5_000L
        "enforce_constraint" -> 5_000L
        "complete_goal" -> 5_000L
        else -> 15_000L
    }

    private fun priorityFor(actionType: String): Priority = when (actionType) {
        "validate_assumption", "enforce_constraint" -> Priority.HIGH
        "complete_goal" -> Priority.NORMAL
        else -> Priority.NORMAL
    }
}
