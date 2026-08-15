package com.nova.runtime.policy.evaluator

import com.nova.runtime.models.ActionNode
import com.nova.runtime.models.Priority
import com.nova.runtime.models.contracts.ActionPolicyRequest
import com.nova.runtime.models.contracts.PolicyVerdict
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

class ConfirmationPolicyEvaluatorTest {

    private val evaluator = ConfirmationPolicyEvaluator()

    @Test
    fun allow_whenNotSensitive() = runTest {
        val result = evaluator.evaluate(request())
        assertEquals(PolicyVerdict.ALLOW, result.verdict)
    }

    @Test
    fun requireConfirmation_forSensitiveAction() = runTest {
        val result = evaluator.evaluate(
            request(actionType = "communication", inputs = mapOf("operation" to "send_sms")),
        )
        assertEquals(PolicyVerdict.REQUIRE_CONFIRMATION, result.verdict)
    }

    @Test
    fun allow_whenUserConfirmed() = runTest {
        val result = evaluator.evaluate(
            request(
                actionType = "communication",
                inputs = mapOf(
                    "operation" to "send_sms",
                    "userConfirmed" to "true",
                ),
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
