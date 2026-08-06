package com.nova.runtime.policy.evaluator

import com.nova.runtime.models.contracts.ActionPolicyRequest
import com.nova.runtime.models.contracts.PolicyVerdict

/** Restricts access to personally identifiable information. */
class PrivacyPolicyEvaluator : PolicyEvaluator {
    override val id: String = "privacy"

    override suspend fun evaluate(request: ActionPolicyRequest): PolicyEvaluatorResult {
        val node = request.node
        val accessesPii = node.inputs["accessesPii"]?.equals("true", ignoreCase = true) == true ||
            node.inputs["piiFields"]?.isNotBlank() == true ||
            inferPiiAccess(node.actionType, node.inputs["operation"])

        if (!accessesPii) {
            return allow("No PII access detected")
        }

        if (request.executionPolicies["allowPiiAccess"]?.equals("true", ignoreCase = true) == true) {
            return allow("PII access allowed by graph policy")
        }

        if (node.inputs["piiConsent"]?.equals("true", ignoreCase = true) == true) {
            return allow("PII consent provided on action")
        }

        return PolicyEvaluatorResult(
            verdict = PolicyVerdict.DENY,
            rationale = "PII access restricted without consent",
        )
    }

    companion object {
        private val PII_PATTERNS = listOf(
            "read_contacts",
            "read_messages",
            "read_call_log",
            "read_calendar",
            "access_health",
            "read_clipboard",
        )

        fun inferPiiAccess(actionType: String, operation: String?): Boolean {
            val key = listOf(actionType, operation).filterNotNull().joinToString(".").lowercase()
            return PII_PATTERNS.any { key.contains(it) }
        }
    }

    private fun allow(rationale: String) = PolicyEvaluatorResult(PolicyVerdict.ALLOW, rationale)
}
