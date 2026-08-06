package com.nova.runtime.execution

import com.nova.runtime.models.ErrorCategory
import com.nova.runtime.models.ErrorSeverity
import com.nova.runtime.models.RuntimeError
import com.nova.runtime.models.contracts.ExecutionRequest
import com.nova.runtime.models.contracts.ExecutionResult

class ExecutionRuntimeStub : ExecutionRuntime {
    override suspend fun execute(request: ExecutionRequest): ExecutionResult =
        ExecutionResult.Failure(
            RuntimeError(
                code = "EXECUTION_NOT_IMPLEMENTED",
                category = ErrorCategory.INFRASTRUCTURE,
                severity = ErrorSeverity.LOW,
                recoverable = true,
                userVisibleMessage = "Execution runtime not yet implemented.",
            ),
        )

    override suspend fun pause(graphId: String) { /* TODO */ }
    override suspend fun resume(graphId: String) { /* TODO */ }
    override suspend fun cancel(graphId: String) { /* TODO */ }
}
