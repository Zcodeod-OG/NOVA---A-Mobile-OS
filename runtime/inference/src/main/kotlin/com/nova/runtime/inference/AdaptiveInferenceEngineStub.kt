package com.nova.runtime.inference

import com.nova.runtime.models.ErrorCategory
import com.nova.runtime.models.ErrorSeverity
import com.nova.runtime.models.RuntimeError
import com.nova.runtime.models.contracts.InferenceRequest
import com.nova.runtime.models.contracts.InferenceResult

class AdaptiveInferenceEngineStub : AdaptiveInferenceEngine {
    override suspend fun infer(request: InferenceRequest): InferenceResult =
        InferenceResult.Failure(
            RuntimeError(
                code = "AIE_NOT_IMPLEMENTED",
                category = ErrorCategory.INFRASTRUCTURE,
                severity = ErrorSeverity.LOW,
                recoverable = true,
                userVisibleMessage = "Inference engine not yet implemented.",
            ),
        )
}
