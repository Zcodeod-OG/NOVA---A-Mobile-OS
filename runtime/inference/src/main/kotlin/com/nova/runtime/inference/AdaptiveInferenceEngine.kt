package com.nova.runtime.inference

import com.nova.runtime.models.contracts.InferenceRequest
import com.nova.runtime.models.contracts.InferenceResult

/** MSP §5 — Adaptive Inference Engine public contract */
interface AdaptiveInferenceEngine {
    suspend fun infer(request: InferenceRequest): InferenceResult
}
