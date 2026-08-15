package com.nova.runtime.policy.evaluator

import com.nova.runtime.models.contracts.ActionPolicyRequest
import com.nova.runtime.models.contracts.PolicyVerdict

/** Requires user confirmation for sensitive actions. */
class ConfirmationPolicyEvaluator : PolicyEvaluator {
    override val id: String = "confirmation"

    override suspend fun evaluate(request: ActionPolicyRequest): PolicyEvaluatorResult {
        if (!requiresConfirmation(request)) {
            return allow("No confirmation required")
        }

        if (request.node.inputs["userConfirmed"]?.equals("true", ignoreCase = true) == true) {
            return allow("User confirmation provided")
        }

        return PolicyEvaluatorResult(
            verdict = PolicyVerdict.REQUIRE_CONFIRMATION,
            rationale = "Sensitive action requires user confirmation",
        )
    }

    internal fun requiresConfirmation(request: ActionPolicyRequest): Boolean {
        val node = request.node
        if (node.inputs["requiresConfirmation"]?.equals("true", ignoreCase = true) == true) {
            return true
        }
        if (request.executionPolicies["requireConfirmation"]?.equals("true", ignoreCase = true) == true) {
            return true
        }

        val actionKey = listOf(node.actionType, node.inputs["operation"])
            .filterNotNull()
            .joinToString(".")
            .lowercase()

        return SENSITIVE_PATTERNS.any { actionKey.contains(it) }
    }

    companion object {
        private val SENSITIVE_PATTERNS = listOf(
            "send_payment",
            "transfer_money",
            "delete",
            "share_location",
            "publish",
            "purchase",
            "send_sms",
            "make_call",
        )
    }

    private fun allow(rationale: String) = PolicyEvaluatorResult(PolicyVerdict.ALLOW, rationale)
}
