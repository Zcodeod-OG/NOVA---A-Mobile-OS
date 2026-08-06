package com.nova.runtime.models.contracts

import com.nova.runtime.models.Nag
import com.nova.runtime.models.ReasoningContext
import com.nova.runtime.models.RuntimeError
import java.util.UUID

data class PlanningRequest(
    val reasoningContext: ReasoningContext,
    val traceId: UUID,
)

sealed class PlanningResult {
    data class Success(val graph: Nag) : PlanningResult()
    data class Failure(val error: RuntimeError) : PlanningResult()
}
