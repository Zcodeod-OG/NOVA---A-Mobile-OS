package com.nova.runtime.models.contracts

import com.nova.runtime.models.RuntimeError

data class InferenceRequest(
    val prompt: String,
    val tierHint: Int? = null,
    val traceId: String,
)

sealed class InferenceResult {
    data class Success(val output: String, val tierUsed: Int) : InferenceResult()
    data class Failure(val error: RuntimeError) : InferenceResult()
}
