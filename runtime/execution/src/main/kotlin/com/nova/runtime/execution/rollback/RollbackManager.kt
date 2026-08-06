package com.nova.runtime.execution.rollback

import com.nova.runtime.execution.worker.ActionExecutor
import com.nova.runtime.execution.worker.NodeExecutionOutcome
import com.nova.runtime.models.ActionNode
import java.util.UUID

/** Compensates completed nodes on failure when rollback policy is enabled. */
interface RollbackManager {
    fun recordCompletion(node: ActionNode)
    fun rollbackCandidates(): List<ActionNode>
    suspend fun rollback(
        traceId: UUID,
        actionExecutor: ActionExecutor,
    ): RollbackResult
    fun clear()
}

data class RollbackResult(
    val rolledBackNodeIds: List<UUID>,
    val failedRollbacks: List<UUID>,
)

class DefaultRollbackManager : RollbackManager {

    private val completedStack = ArrayDeque<ActionNode>()

    override fun recordCompletion(node: ActionNode) {
        if (supportsRollback(node)) {
            completedStack.addLast(node)
        }
    }

    override fun rollbackCandidates(): List<ActionNode> = completedStack.reversed()

    override suspend fun rollback(
        traceId: UUID,
        actionExecutor: ActionExecutor,
    ): RollbackResult {
        val rolledBack = mutableListOf<UUID>()
        val failed = mutableListOf<UUID>()

        while (completedStack.isNotEmpty()) {
            val node = completedStack.removeLast()
            if (!supportsRollback(node)) continue

            when (val outcome = actionExecutor.rollback(node, traceId)) {
                is NodeExecutionOutcome.Success -> rolledBack.add(node.id)
                is NodeExecutionOutcome.Failure -> failed.add(node.id)
            }
        }

        return RollbackResult(
            rolledBackNodeIds = rolledBack,
            failedRollbacks = failed,
        )
    }

    override fun clear() {
        completedStack.clear()
    }

    private fun supportsRollback(node: ActionNode): Boolean {
        val policy = node.rollbackPolicy.trim().lowercase()
        return policy.isNotEmpty() && policy != "none"
    }
}
