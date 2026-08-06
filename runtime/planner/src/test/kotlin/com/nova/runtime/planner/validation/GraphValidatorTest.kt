package com.nova.runtime.planner.validation

import com.nova.runtime.models.ActionNode
import com.nova.runtime.models.Nag
import com.nova.runtime.models.Priority
import com.nova.runtime.planner.util.DeterministicIds
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GraphValidatorTest {

    private val validator = DefaultGraphValidator()

    @Test
    fun validate_detectsCycle() {
        val nodeA = node("a")
        val nodeB = node("b", listOf(nodeA.id))
        val nodeC = node("c", listOf(nodeB.id))
        val cyclicA = nodeA.copy(dependencies = listOf(nodeC.id))

        val result = validator.validate(
            nag(
                nodes = listOf(cyclicA, nodeB, nodeC),
                dependencies = mapOf(
                    cyclicA.id to listOf(nodeC.id),
                    nodeB.id to listOf(nodeA.id),
                    nodeC.id to listOf(nodeB.id),
                ),
            ),
        )

        assertFalse(result.isAcyclic)
        assertTrue(result.cyclesDetected > 0)
    }

    @Test
    fun validate_acceptsDag() {
        val nodeA = node("a")
        val nodeB = node("b", listOf(nodeA.id))
        val nodeC = node("c", listOf(nodeB.id))

        val result = validator.validate(
            nag(
                nodes = listOf(nodeA, nodeB, nodeC),
                dependencies = mapOf(
                    nodeA.id to emptyList(),
                    nodeB.id to listOf(nodeA.id),
                    nodeC.id to listOf(nodeB.id),
                ),
            ),
        )

        assertTrue(result.isAcyclic)
        assertTrue(result.isValid)
        assertEquals(2, result.depth)
    }

    @Test
    fun cycleBreaker_producesAcyclicGraph() {
        val nodeA = node("a")
        val nodeB = node("b", listOf(nodeA.id))
        val nodeC = node("c", listOf(nodeB.id))
        val cyclicA = nodeA.copy(dependencies = listOf(nodeC.id))

        val graph = nag(
            nodes = listOf(cyclicA, nodeB, nodeC),
            dependencies = mapOf(
                cyclicA.id to listOf(nodeC.id),
                nodeB.id to listOf(nodeA.id),
                nodeC.id to listOf(nodeB.id),
            ),
        )

        val breaker = DefaultCycleBreaker(validator)
        val result = breaker.ensureAcyclic(graph)

        assertTrue(result.cyclesBroken > 0)
        assertTrue(validator.validate(result.graph).isAcyclic)
    }

    private fun node(key: String, dependencies: List<UUID> = emptyList()): ActionNode =
        ActionNode(
            id = DeterministicIds.uuid("action", key),
            actionType = "test",
            inputs = mapOf("taskKey" to key),
            outputs = mapOf("taskKey" to key),
            dependencies = dependencies,
            timeoutMs = 1_000L,
            retryPolicy = "none",
            rollbackPolicy = "none",
            executionPriority = Priority.NORMAL,
        )

    private fun nag(nodes: List<ActionNode>, dependencies: Map<UUID, List<UUID>>): Nag =
        Nag(
            graphId = DeterministicIds.uuid("graph", "validator-test"),
            metadata = emptyMap(),
            taskHierarchy = emptyList(),
            actionNodes = nodes,
            dependencies = dependencies,
            executionPolicies = emptyMap(),
        )
}
