package com.nova.runtime.models.contracts

import com.nova.runtime.models.RuntimeError
import java.util.UUID

data class CapabilityRequest(
    val capabilityType: String,
    val operation: String,
    val parameters: Map<String, String>,
    val traceId: UUID,
)

sealed class CapabilityResult {
    data class Success(val output: Map<String, String>) : CapabilityResult()
    data class Failure(val error: RuntimeError) : CapabilityResult()
}
