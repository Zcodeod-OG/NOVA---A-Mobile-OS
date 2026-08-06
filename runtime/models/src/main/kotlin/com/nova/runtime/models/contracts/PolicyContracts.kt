package com.nova.runtime.models.contracts

import com.nova.runtime.models.ActionNode
import com.nova.runtime.models.Nag
import com.nova.runtime.models.RuntimeError
import java.util.UUID

data class PolicyRequest(
    val graph: Nag,
    val traceId: UUID,
)

/** Per-action policy evaluation input for execution-time gating. */
data class ActionPolicyRequest(
    val node: ActionNode,
    val traceId: UUID,
    val graphId: UUID? = null,
    val executionPolicies: Map<String, String> = emptyMap(),
)

enum class PolicyVerdict {
    ALLOW,
    DENY,
    REQUIRE_CONFIRMATION,
}

enum class PolicyDecisionType {
    APPROVED,
    REJECTED,
    REQUIRES_USER_CONFIRMATION,
}

data class PolicyDecision(
    val type: PolicyDecisionType,
    val rationale: String,
    val evaluatorId: String? = null,
)

sealed class PolicyEvaluationResult {
    data class Success(val decision: PolicyDecision) : PolicyEvaluationResult()
    data class Failure(val error: RuntimeError) : PolicyEvaluationResult()
}

/** Narrow contract for execution runtime — avoids policy→execution dependency. */
interface ActionPolicyGate {
    suspend fun evaluateAction(request: ActionPolicyRequest): PolicyEvaluationResult
}
