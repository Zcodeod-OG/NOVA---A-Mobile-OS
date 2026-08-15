package com.nova.runtime.execution.queue

import com.nova.runtime.models.ActionNode
import com.nova.runtime.models.Priority
import java.util.PriorityQueue
import java.util.UUID

/** Priority/readiness queue for pending action nodes. */
interface QueueManager {
    fun enqueue(nodes: List<ActionNode>)
    fun dequeue(): ActionNode?
    fun peek(): ActionNode?
    fun remove(nodeId: UUID): Boolean
    fun size(): Int
    fun clear()
    fun snapshot(): List<ActionNode>
}

class DefaultQueueManager : QueueManager {

    private val queue = PriorityQueue(compareBy<ActionNode> { priorityOrder(it.executionPriority) }.thenBy { it.id })

    override fun enqueue(nodes: List<ActionNode>) {
        nodes.forEach { queue.offer(it) }
    }

    override fun dequeue(): ActionNode? = queue.poll()

    override fun peek(): ActionNode? = queue.peek()

    override fun remove(nodeId: UUID): Boolean {
        val node = queue.firstOrNull { it.id == nodeId } ?: return false
        return queue.remove(node)
    }

    override fun size(): Int = queue.size

    override fun clear() {
        queue.clear()
    }

    override fun snapshot(): List<ActionNode> = queue.sortedWith(
        compareBy<ActionNode> { priorityOrder(it.executionPriority) }.thenBy { it.id },
    )

    private fun priorityOrder(priority: Priority): Int = when (priority) {
        Priority.CRITICAL -> 0
        Priority.HIGH -> 1
        Priority.NORMAL -> 2
        Priority.LOW -> 3
        Priority.IDLE -> 4
    }
}
