package com.nova.runtime.execution.monitor

import com.nova.runtime.execution.lifecycle.ExecutionNodeState
import com.nova.runtime.execution.lifecycle.GraphExecutionStatus
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/** Tracks in-flight executions and state transitions. */
interface ExecutionMonitor {
    fun initializeGraph(graphId: UUID, nodeIds: Collection<UUID>)
    fun transition(nodeId: UUID, newState: ExecutionNodeState)
    fun nodeState(nodeId: UUID): ExecutionNodeState?
    fun nodeStates(): Map<UUID, ExecutionNodeState>
    fun inFlightCount(): Int
    fun setGraphStatus(graphId: UUID, status: GraphExecutionStatus)
    fun graphStatus(graphId: UUID): GraphExecutionStatus?
    fun completedCount(): Int
    fun failedCount(): Int
    fun reset()
}

class DefaultExecutionMonitor : ExecutionMonitor {

    private val nodeStates = ConcurrentHashMap<UUID, ExecutionNodeState>()
    private val graphStatuses = ConcurrentHashMap<UUID, GraphExecutionStatus>()
    private val inFlight = AtomicInteger(0)

    override fun initializeGraph(graphId: UUID, nodeIds: Collection<UUID>) {
        nodeIds.forEach { nodeId ->
            nodeStates[nodeId] = ExecutionNodeState.PENDING
        }
        graphStatuses[graphId] = GraphExecutionStatus.RUNNING
    }

    override fun transition(nodeId: UUID, newState: ExecutionNodeState) {
        val previous = nodeStates.put(nodeId, newState)
        when (previous) {
            ExecutionNodeState.RUNNING -> inFlight.decrementAndGet()
            else -> Unit
        }
        if (newState == ExecutionNodeState.RUNNING) {
            inFlight.incrementAndGet()
        }
    }

    override fun nodeState(nodeId: UUID): ExecutionNodeState? = nodeStates[nodeId]

    override fun nodeStates(): Map<UUID, ExecutionNodeState> = nodeStates.toMap()

    override fun inFlightCount(): Int = inFlight.get()

    override fun setGraphStatus(graphId: UUID, status: GraphExecutionStatus) {
        graphStatuses[graphId] = status
    }

    override fun graphStatus(graphId: UUID): GraphExecutionStatus? = graphStatuses[graphId]

    override fun completedCount(): Int =
        nodeStates.values.count { it == ExecutionNodeState.COMPLETED }

    override fun failedCount(): Int =
        nodeStates.values.count { it == ExecutionNodeState.FAILED || it == ExecutionNodeState.ROLLED_BACK }

    override fun reset() {
        nodeStates.clear()
        graphStatuses.clear()
        inFlight.set(0)
    }
}
