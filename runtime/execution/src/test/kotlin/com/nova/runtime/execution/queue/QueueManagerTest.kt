package com.nova.runtime.execution.queue

import com.nova.runtime.models.ActionNode
import com.nova.runtime.models.Priority
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class QueueManagerTest {

    private val queue = DefaultQueueManager()

    @Test
    fun dequeue_returnsHighestPriorityFirst() {
        val low = node("low", Priority.LOW)
        val high = node("high", Priority.HIGH)
        val critical = node("critical", Priority.CRITICAL)

        queue.enqueue(listOf(low, high, critical))

        assertEquals(critical.id, queue.dequeue()?.id)
        assertEquals(high.id, queue.dequeue()?.id)
        assertEquals(low.id, queue.dequeue()?.id)
        assertNull(queue.dequeue())
    }

    @Test
    fun remove_dropsSpecificNode() {
        val first = node("first", Priority.NORMAL)
        val second = node("second", Priority.NORMAL)
        queue.enqueue(listOf(first, second))

        assertEquals(true, queue.remove(first.id))
        assertEquals(second.id, queue.dequeue()?.id)
    }

    private fun node(key: String, priority: Priority): ActionNode =
        ActionNode(
            id = UUID.nameUUIDFromBytes(key.toByteArray()),
            actionType = "execute_capability",
            inputs = emptyMap(),
            outputs = emptyMap(),
            dependencies = emptyList(),
            timeoutMs = 1_000L,
            retryPolicy = "none",
            rollbackPolicy = "none",
            executionPriority = priority,
        )
}
