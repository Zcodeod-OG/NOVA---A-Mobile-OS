package com.nova.runtime.models.contracts

import com.nova.runtime.models.Nag
import com.nova.runtime.models.RuntimeError
import java.util.UUID

data class PolicyRequest(
    val graph: Nag,
    val traceId: UUID,
)

enum class PolicyDecisionType {
    APPROVED,
    REJECTED,
    REQUIRES_USER_CONFIRMATION,
}

data class PolicyDecision(
    val type: PolicyDecisionType,
    val rationale: String,
)

sealed class PolicyEvaluationResult {
    data class Success(val decision: PolicyDecision) : PolicyEvaluationResult()
    data class Failure(val error: RuntimeError) : PolicyEvaluationResult()
}
