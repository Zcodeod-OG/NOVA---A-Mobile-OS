package com.nova.runtime.execution.monitor

import com.nova.runtime.execution.lifecycle.ExecutionNodeState
import com.nova.runtime.execution.lifecycle.GraphExecutionStatus
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class ExecutionLifecycleTest {

    private val monitor = DefaultExecutionMonitor()

    @Test
    fun transitions_trackNodeAndGraphLifecycle() {
        val graphId = UUID.randomUUID()
        val nodeId = UUID.randomUUID()

        monitor.initializeGraph(graphId, listOf(nodeId))
        assertEquals(ExecutionNodeState.PENDING, monitor.nodeState(nodeId))
        assertEquals(GraphExecutionStatus.RUNNING, monitor.graphStatus(graphId))

        monitor.transition(nodeId, ExecutionNodeState.RUNNING)
        assertEquals(1, monitor.inFlightCount())

        monitor.transition(nodeId, ExecutionNodeState.COMPLETED)
        assertEquals(0, monitor.inFlightCount())
        assertEquals(1, monitor.completedCount())

        monitor.transition(nodeId, ExecutionNodeState.FAILED)
        assertEquals(1, monitor.failedCount())

        monitor.setGraphStatus(graphId, GraphExecutionStatus.COMPLETED)
        assertEquals(GraphExecutionStatus.COMPLETED, monitor.graphStatus(graphId))
    }
}
