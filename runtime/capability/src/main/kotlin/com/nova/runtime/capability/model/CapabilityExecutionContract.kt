package com.nova.runtime.capability.model

import com.nova.runtime.models.RuntimeError
import java.util.UUID

/** Typed execution request aligned with IAS §7 capability contracts. */
data class CapabilityExecutionRequest(
    val operation: String,
    val parameters: Map<String, String>,
    val traceId: UUID,
    val transactionId: UUID? = null,
)

sealed class CapabilityExecutionResponse {
    data class Success(val output: Map<String, String>) : CapabilityExecutionResponse()
    data class Failure(val error: RuntimeError) : CapabilityExecutionResponse()
}

sealed class CapabilityValidationResult {
    data object Valid : CapabilityValidationResult()
    data class Invalid(val error: RuntimeError) : CapabilityValidationResult()
}
