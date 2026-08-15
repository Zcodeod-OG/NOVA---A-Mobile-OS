package com.nova.runtime.planner.serialization

import com.nova.runtime.models.ActionNode
import com.nova.runtime.models.Nag
import com.nova.runtime.models.Priority
import com.nova.runtime.planner.util.DeterministicIds
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class NagSerializerTest {

    private val serializer = CanonicalNagSerializer()

    @Test
    fun serializeDeserialize_roundTripPreservesGraph() {
        val graph = sampleGraph()
        val payload = serializer.serialize(graph)
        val restored = serializer.deserialize(payload)

        assertEquals(graph, restored)
    }

    @Test
    fun serialize_isDeterministicForSameGraph() {
        val graph = sampleGraph()
        assertEquals(serializer.serialize(graph), serializer.serialize(graph))
    }

    private fun sampleGraph(): Nag {
        val nodeA = actionNode("prepare:communication")
        val nodeB = actionNode("execute:communication", listOf(nodeA.id))
        return Nag(
            graphId = DeterministicIds.uuid("graph", "serialization-test"),
            metadata = mapOf("traceId" to "00000000-0000-0000-0000-000000000101"),
            taskHierarchy = listOf("execute_capability:communication", "complete_goal"),
            actionNodes = listOf(nodeA, nodeB),
            dependencies = mapOf(
                nodeA.id to emptyList(),
                nodeB.id to listOf(nodeA.id),
            ),
            executionPolicies = mapOf("scheduling" to "parallel_when_ready"),
        )
    }

    private fun actionNode(key: String, dependencies: List<UUID> = emptyList()): ActionNode =
        ActionNode(
            id = DeterministicIds.uuid("action", key),
            actionType = if (key.startsWith("prepare")) "prepare_capability" else "execute_capability",
            inputs = mapOf("capability" to "communication", "taskKey" to key),
            outputs = mapOf("taskKey" to key),
            dependencies = dependencies,
            timeoutMs = 10_000L,
            retryPolicy = "none",
            rollbackPolicy = "none",
            executionPriority = Priority.NORMAL,
        )
}
