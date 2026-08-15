package com.nova.runtime.policy.evaluator

import com.nova.runtime.models.contracts.ActionPolicyRequest
import com.nova.runtime.models.contracts.PolicyVerdict

/** Blocks dangerous or disallowed operations. */
class SafetyPolicyEvaluator : PolicyEvaluator {
    override val id: String = "safety"

    override suspend fun evaluate(request: ActionPolicyRequest): PolicyEvaluatorResult {
        val node = request.node
        val safetyLevel = node.inputs["safetyLevel"]?.lowercase()?.trim()
        if (safetyLevel == "blocked") {
            return deny("Action explicitly blocked by safety level")
        }

        val actionKey = listOf(node.actionType, node.inputs["operation"])
            .filterNotNull()
            .joinToString(".")
            .lowercase()

        val blockedPattern = BLOCKED_PATTERNS.firstOrNull { actionKey.contains(it) }
        if (blockedPattern != null) {
            return deny("Blocked dangerous operation: $blockedPattern")
        }

        if (node.inputs["allowDangerous"]?.equals("true", ignoreCase = true) == true) {
            return allow("Dangerous operation explicitly allowed")
        }

        val dangerousPattern = DANGEROUS_PATTERNS.firstOrNull { actionKey.contains(it) }
        if (dangerousPattern != null) {
            return deny("Dangerous operation not permitted: $dangerousPattern")
        }

        return allow("No safety violations detected")
    }

    companion object {
        private val BLOCKED_PATTERNS = listOf(
            "factory_reset",
            "wipe_data",
            "delete_all",
            "format_storage",
            "disable_security",
        )

        private val DANGEROUS_PATTERNS = listOf(
            "uninstall_system",
            "modify_system_settings",
            "root_access",
            "shell_execute",
        )
    }

    private fun allow(rationale: String) = PolicyEvaluatorResult(PolicyVerdict.ALLOW, rationale)
    private fun deny(rationale: String) = PolicyEvaluatorResult(PolicyVerdict.DENY, rationale)
}
