package com.nova.runtime.policy.evaluator

import com.nova.runtime.models.contracts.ActionPolicyRequest
import com.nova.runtime.models.contracts.PolicyVerdict
import com.nova.runtime.policy.context.PolicyEnvironment
import com.nova.runtime.policy.model.NovaPermissions

/** Evaluates required permissions declared on action nodes. */
class PermissionPolicyEvaluator(
    private val environment: PolicyEnvironment,
) : PolicyEvaluator {
    override val id: String = "permission"

    override suspend fun evaluate(request: ActionPolicyRequest): PolicyEvaluatorResult {
        val required = resolveRequiredPermissions(request)
        if (required.isEmpty()) {
            return allow("No permissions required")
        }

        val granted = environment.grantedPermissions()
        val missing = required.filter { it !in granted }
        if (missing.isEmpty()) {
            return allow("All required permissions granted")
        }

        return PolicyEvaluatorResult(
            verdict = PolicyVerdict.DENY,
            rationale = "Missing permissions: ${missing.joinToString(", ")}",
        )
    }

    companion object {
        private val INFERRED_PERMISSIONS = mapOf(
            "send_sms" to setOf(NovaPermissions.SMS_SEND),
            "make_call" to setOf(NovaPermissions.CALL),
            "read_contacts" to setOf(NovaPermissions.CONTACTS_READ),
            "write_contacts" to setOf(NovaPermissions.CONTACTS_WRITE),
            "access_location" to setOf(NovaPermissions.LOCATION),
            "record_audio" to setOf(NovaPermissions.MICROPHONE),
            "capture_photo" to setOf(NovaPermissions.CAMERA),
            "read_storage" to setOf(NovaPermissions.STORAGE_READ),
            "write_calendar" to setOf(NovaPermissions.CALENDAR_WRITE),
        )

        fun inferredPermissions(actionType: String, operation: String?): Set<String> {
            val key = operation?.lowercase()?.trim()?.takeIf { it.isNotEmpty() } ?: actionType.lowercase()
            return INFERRED_PERMISSIONS.entries
                .filter { (pattern, _) -> key.contains(pattern) }
                .flatMap { it.value }
                .toSet()
        }
    }

    internal fun resolveRequiredPermissions(request: ActionPolicyRequest): Set<String> {
        val explicit = parseList(request.node.inputs["requiredPermissions"]).toSet()
        val inferred = inferredPermissions(
            actionType = request.node.actionType,
            operation = request.node.inputs["operation"],
        )
        return explicit + inferred
    }

    private fun allow(rationale: String) = PolicyEvaluatorResult(PolicyVerdict.ALLOW, rationale)

    private fun parseList(raw: String?): List<String> =
        raw?.split(',')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?: emptyList()
}
