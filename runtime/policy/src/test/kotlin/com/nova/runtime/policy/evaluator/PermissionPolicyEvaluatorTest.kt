package com.nova.runtime.policy.evaluator

import com.nova.runtime.models.ActionNode
import com.nova.runtime.models.Priority
import com.nova.runtime.models.contracts.ActionPolicyRequest
import com.nova.runtime.models.contracts.PolicyVerdict
import com.nova.runtime.policy.context.DefaultPolicyEnvironment
import com.nova.runtime.policy.model.NovaPermissions
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

class PermissionPolicyEvaluatorTest {

    @Test
    fun allow_whenNoPermissionsRequired() = runTest {
        val evaluator = PermissionPolicyEvaluator(DefaultPolicyEnvironment())
        val result = evaluator.evaluate(request())
        assertEquals(PolicyVerdict.ALLOW, result.verdict)
    }

    @Test
    fun deny_whenRequiredPermissionMissing() = runTest {
        val evaluator = PermissionPolicyEvaluator(
            DefaultPolicyEnvironment(permissions = setOf(NovaPermissions.CONTACTS_READ)),
        )
        val result = evaluator.evaluate(
            request(inputs = mapOf("requiredPermissions" to NovaPermissions.LOCATION)),
        )
        assertEquals(PolicyVerdict.DENY, result.verdict)
    }

    @Test
    fun allow_whenRequiredPermissionGranted() = runTest {
        val evaluator = PermissionPolicyEvaluator(
            DefaultPolicyEnvironment(permissions = setOf(NovaPermissions.SMS_SEND)),
        )
        val result = evaluator.evaluate(
            request(
                actionType = "communication",
                inputs = mapOf("operation" to "send_sms"),
            ),
        )
        assertEquals(PolicyVerdict.ALLOW, result.verdict)
    }

    private fun request(
        actionType: String = "execute_capability",
        inputs: Map<String, String> = emptyMap(),
    ): ActionPolicyRequest =
        ActionPolicyRequest(
            node = ActionNode(
                id = UUID.randomUUID(),
                actionType = actionType,
                inputs = inputs,
                outputs = emptyMap(),
                dependencies = emptyList(),
                timeoutMs = 1_000L,
                retryPolicy = "none",
                rollbackPolicy = "none",
                executionPriority = Priority.NORMAL,
            ),
            traceId = UUID.randomUUID(),
        )
}
