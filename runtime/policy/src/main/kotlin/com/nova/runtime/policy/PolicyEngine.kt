package com.nova.runtime.policy

import com.nova.runtime.models.contracts.ActionPolicyGate
import com.nova.runtime.models.contracts.ActionPolicyRequest
import com.nova.runtime.models.contracts.PolicyEvaluationResult
import com.nova.runtime.models.contracts.PolicyRequest

interface PolicyEngine : ActionPolicyGate {
    suspend fun evaluate(request: PolicyRequest): PolicyEvaluationResult
    override suspend fun evaluateAction(request: ActionPolicyRequest): PolicyEvaluationResult
}
