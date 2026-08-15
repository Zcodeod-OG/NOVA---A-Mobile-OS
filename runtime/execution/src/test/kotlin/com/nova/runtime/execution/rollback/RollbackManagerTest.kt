package com.nova.runtime.execution.rollback

import com.nova.runtime.execution.worker.ActionExecutor
import com.nova.runtime.execution.worker.NodeExecutionOutcome
import com.nova.runtime.models.ActionNode
import com.nova.runtime.models.Priority
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

class RollbackManagerTest {

    @Test
    fun rollback_reversesCompletedNodesWithPolicy() = runTest {
        val rollbackManager = DefaultRollbackManager()
        val rolledBack = mutableListOf<UUID>()
        val executor = object : ActionExecutor {
            override suspend fun execute(node: ActionNode, traceId: UUID): NodeExecutionOutcome =
                NodeExecutionOutcome.Success(node.outputs)

            override suspend fun rollback(node: ActionNode, traceId: UUID): NodeExecutionOutcome {
                rolledBack.add(node.id)
                return NodeExecutionOutcome.Success(emptyMap())
            }
        }

        val nodeA = node("a", rollbackPolicy = "compensate")
        val nodeB = node("b", rollbackPolicy = "none")
        rollbackManager.recordCompletion(nodeA)
        rollbackManager.recordCompletion(nodeB)

        val result = rollbackManager.rollback(UUID.randomUUID(), executor)

        assertEquals(listOf(nodeA.id), result.rolledBackNodeIds)
        assertEquals(listOf(nodeA.id), rolledBack)
    }

    private fun node(key: String, rollbackPolicy: String): ActionNode =
        ActionNode(
            id = UUID.nameUUIDFromBytes(key.toByteArray()),
            actionType = "execute_capability",
            inputs = emptyMap(),
            outputs = emptyMap(),
            dependencies = emptyList(),
            timeoutMs = 1_000L,
            retryPolicy = "none",
            rollbackPolicy = rollbackPolicy,
            executionPriority = Priority.NORMAL,
        )
}
