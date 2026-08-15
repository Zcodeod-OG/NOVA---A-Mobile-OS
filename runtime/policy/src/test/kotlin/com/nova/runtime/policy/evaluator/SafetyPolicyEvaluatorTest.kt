package com.nova.runtime.policy.evaluator

import com.nova.runtime.models.ActionNode
import com.nova.runtime.models.Priority
import com.nova.runtime.models.contracts.ActionPolicyRequest
import com.nova.runtime.models.contracts.PolicyVerdict
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

class SafetyPolicyEvaluatorTest {

    private val evaluator = SafetyPolicyEvaluator()

    @Test
    fun allow_safeAction() = runTest {
        val result = evaluator.evaluate(request(actionType = "read_status"))
        assertEquals(PolicyVerdict.ALLOW, result.verdict)
    }

    @Test
    fun deny_blockedSafetyLevel() = runTest {
        val result = evaluator.evaluate(
            request(inputs = mapOf("safetyLevel" to "blocked")),
        )
        assertEquals(PolicyVerdict.DENY, result.verdict)
    }

    @Test
    fun deny_dangerousOperation() = runTest {
        val result = evaluator.evaluate(
            request(actionType = "device", inputs = mapOf("operation" to "shell_execute")),
        )
        assertEquals(PolicyVerdict.DENY, result.verdict)
    }

    @Test
    fun allow_dangerousWhenExplicitlyAllowed() = runTest {
        val result = evaluator.evaluate(
            request(
                actionType = "device",
                inputs = mapOf(
                    "operation" to "shell_execute",
                    "allowDangerous" to "true",
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
