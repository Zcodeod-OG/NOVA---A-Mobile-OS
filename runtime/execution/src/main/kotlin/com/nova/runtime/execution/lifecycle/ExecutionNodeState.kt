package com.nova.runtime.execution.lifecycle

/** Node lifecycle states per TDD §13. */
enum class ExecutionNodeState {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED,
    ROLLED_BACK,
    CANCELLED,
}

enum class GraphExecutionStatus {
    RUNNING,
    COMPLETED,
    FAILED,
    ROLLED_BACK,
    CANCELLED,
    PAUSED,
}
