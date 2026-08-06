package com.nova.runtime.models.contracts

import com.nova.runtime.models.Nag
import com.nova.runtime.models.RuntimeError
import java.util.UUID

data class ExecutionRequest(
    val graph: Nag,
    val traceId: UUID,
)

sealed class ExecutionResult {
    data class Success(val completedNodes: Int) : ExecutionResult()
    data class Failure(val error: RuntimeError) : ExecutionResult()
}
