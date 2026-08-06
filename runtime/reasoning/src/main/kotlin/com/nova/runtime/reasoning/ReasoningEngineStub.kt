package com.nova.runtime.reasoning

import com.nova.runtime.models.ErrorCategory
import com.nova.runtime.models.ErrorSeverity
import com.nova.runtime.models.RuntimeError
import com.nova.runtime.models.contracts.ReasoningEngineResult
import com.nova.runtime.models.contracts.ReasoningRequest

class ReasoningEngineStub : ReasoningEngine {
    override suspend fun reason(request: ReasoningRequest): ReasoningEngineResult =
        ReasoningEngineResult.Failure(
            RuntimeError(
                code = "REASONING_NOT_IMPLEMENTED",
                category = ErrorCategory.INFRASTRUCTURE,
                severity = ErrorSeverity.LOW,
                recoverable = true,
                userVisibleMessage = "Reasoning engine not yet implemented.",
            ),
        )
}
