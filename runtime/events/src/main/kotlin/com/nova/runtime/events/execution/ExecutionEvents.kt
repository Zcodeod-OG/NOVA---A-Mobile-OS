package com.nova.runtime.events.execution

/** Execution runtime events per EMS §7. */
object ExecutionEvents {
    const val STARTED = "ExecutionStarted"
    const val NODE_SCHEDULED = "NodeScheduled"
    const val NODE_RUNNING = "NodeRunning"
    const val NODE_COMPLETED = "NodeCompleted"
    const val NODE_FAILED = "NodeFailed"
    const val NODE_ROLLED_BACK = "NodeRolledBack"
    const val GRAPH_COMPLETED = "GraphCompleted"
    const val GRAPH_FAILED = "GraphFailed"
    const val GRAPH_CANCELLED = "GraphCancelled"
    const val GRAPH_PAUSED = "GraphPaused"
    const val GRAPH_RESUMED = "GraphResumed"
}
