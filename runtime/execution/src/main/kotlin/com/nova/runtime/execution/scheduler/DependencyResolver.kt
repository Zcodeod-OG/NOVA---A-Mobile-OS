package com.nova.runtime.execution.scheduler

import com.nova.runtime.execution.lifecycle.ExecutionNodeState
import com.nova.runtime.models.ActionNode
import com.nova.runtime.models.Nag
import java.util.UUID

/** Determines which action nodes are ready given completed dependencies. */
interface DependencyResolver {
    fun readyNodes(
        graph: Nag,
        nodeStates: Map<UUID, ExecutionNodeState>,
    ): List<ActionNode>
}

class DefaultDependencyResolver : DependencyResolver {

    override fun readyNodes(
        graph: Nag,
        nodeStates: Map<UUID, ExecutionNodeState>,
    ): List<ActionNode> {
        val completed = nodeStates
            .filterValues { it == ExecutionNodeState.COMPLETED }
            .keys

        return graph.actionNodes.filter { node ->
            val state = nodeStates[node.id] ?: ExecutionNodeState.PENDING
            state == ExecutionNodeState.PENDING &&
                node.dependencies.all { dependencyId -> dependencyId in completed }
        }
    }
}
