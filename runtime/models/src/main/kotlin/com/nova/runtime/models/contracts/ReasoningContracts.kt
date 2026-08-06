package com.nova.runtime.models.contracts

import com.nova.runtime.models.Nir
import com.nova.runtime.models.ReasoningContext
import com.nova.runtime.models.RuntimeError
import com.nova.runtime.models.contracts.MemoryResult
import java.util.UUID

data class ReasoningRequest(
    val nir: Nir,
    val memoryResults: MemoryResult,
    val traceId: UUID,
)

sealed class ReasoningEngineResult {
    data class Success(val context: ReasoningContext) : ReasoningEngineResult()
    data class Failure(val error: RuntimeError) : ReasoningEngineResult()
}
