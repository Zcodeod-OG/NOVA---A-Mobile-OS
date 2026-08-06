package com.nova.runtime.policy

import com.nova.runtime.models.contracts.PolicyEvaluationResult
import com.nova.runtime.models.contracts.PolicyRequest

interface PolicyEngine {
    suspend fun evaluate(request: PolicyRequest): PolicyEvaluationResult
}
