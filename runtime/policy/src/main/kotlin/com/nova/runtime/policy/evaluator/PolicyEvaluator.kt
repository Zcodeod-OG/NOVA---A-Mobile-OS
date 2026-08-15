package com.nova.runtime.policy.evaluator

import com.nova.runtime.models.contracts.ActionPolicyRequest
import com.nova.runtime.models.contracts.PolicyVerdict

/** Composable policy evaluator — each checks one policy dimension. */
interface PolicyEvaluator {
    val id: String
    suspend fun evaluate(request: ActionPolicyRequest): PolicyEvaluatorResult
}

data class PolicyEvaluatorResult(
    val verdict: PolicyVerdict,
    val rationale: String,
)
