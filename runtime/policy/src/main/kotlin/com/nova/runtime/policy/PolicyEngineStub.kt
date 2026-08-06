package com.nova.runtime.policy

import com.nova.runtime.models.ErrorCategory
import com.nova.runtime.models.ErrorSeverity
import com.nova.runtime.models.RuntimeError
import com.nova.runtime.models.contracts.ActionPolicyRequest
import com.nova.runtime.models.contracts.PolicyEvaluationResult
import com.nova.runtime.models.contracts.PolicyRequest

class PolicyEngineStub : PolicyEngine {
    override suspend fun evaluate(request: PolicyRequest): PolicyEvaluationResult =
        notImplemented()

    override suspend fun evaluateAction(request: ActionPolicyRequest): PolicyEvaluationResult =
        notImplemented()

    private fun notImplemented(): PolicyEvaluationResult =
        PolicyEvaluationResult.Failure(
            RuntimeError(
                code = "POLICY_NOT_IMPLEMENTED",
                category = ErrorCategory.INFRASTRUCTURE,
                severity = ErrorSeverity.LOW,
                recoverable = true,
                userVisibleMessage = "Policy engine not yet implemented.",
            ),
        )
}
