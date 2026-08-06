package com.nova.runtime.execution.scheduler

import com.nova.runtime.execution.lifecycle.ExecutionNodeState
import com.nova.runtime.models.ActionNode
import com.nova.runtime.models.Nag
import com.nova.runtime.models.Priority
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DependencyResolverTest {

    private val resolver = DefaultDependencyResolver()

    @Test
    fun readyNodes_returnsRootNodesWhenNothingCompleted() {
        val nodeA = node("a", emptyList())
        val nodeB = node("b", listOf(nodeA.id))
        val graph = graphOf(nodeA, nodeB)

        val ready = resolver.readyNodes(graph, emptyMap())

        assertEquals(listOf(nodeA.id), ready.map { it.id })
    }

    @Test
    fun readyNodes_returnsDependentsAfterPrerequisiteCompletes() {
        val nodeA = node("a", emptyList())
        val nodeB = node("b", listOf(nodeA.id))
        val graph = graphOf(nodeA, nodeB)

        val ready = resolver.readyNodes(
            graph,
            mapOf(nodeA.id to ExecutionNodeState.COMPLETED),
        )

        assertEquals(listOf(nodeB.id), ready.map { it.id })
    }

    @Test
    fun readyNodes_excludesRunningOrCompletedNodes() {
        val nodeA = node("a", emptyList())
        val graph = graphOf(nodeA)

        val ready = resolver.readyNodes(
            graph,
            mapOf(nodeA.id to ExecutionNodeState.RUNNING),
        )

        assertTrue(ready.isEmpty())
    }

    @Test
    fun readyNodes_parallelBranchesBecomeReadyTogether() {
        val nodeA = node("a", emptyList())
        val nodeB = node("b", listOf(nodeA.id))
        val nodeC = node("c", listOf(nodeA.id))
        val graph = graphOf(nodeA, nodeB, nodeC)

        val ready = resolver.readyNodes(
            graph,
            mapOf(nodeA.id to ExecutionNodeState.COMPLETED),
        )

        assertEquals(setOf(nodeB.id, nodeC.id), ready.map { it.id }.toSet())
    }

    private fun node(key: String, dependencies: List<UUID>): ActionNode =
        ActionNode(
            id = UUID.nameUUIDFromBytes(key.toByteArray()),
            actionType = "execute_capability",
            inputs = mapOf("taskKey" to key),
            outputs = mapOf("taskKey" to key),
            dependencies = dependencies,
            timeoutMs = 1_000L,
            retryPolicy = "none",
            rollbackPolicy = "none",
            executionPriority = Priority.NORMAL,
        )

    private fun graphOf(vararg nodes: ActionNode): Nag =
        Nag(
            graphId = UUID.randomUUID(),
            metadata = emptyMap(),
            taskHierarchy = emptyList(),
            actionNodes = nodes.toList(),
            dependencies = nodes.associate { it.id to it.dependencies },
            executionPolicies = emptyMap(),
        )
}
