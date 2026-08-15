package com.nova.runtime.policy.evaluator

import com.nova.runtime.models.ActionNode
import com.nova.runtime.models.Priority
import com.nova.runtime.models.contracts.ActionPolicyRequest
import com.nova.runtime.models.contracts.PolicyVerdict
import com.nova.runtime.policy.context.DefaultPolicyEnvironment
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

class BatteryPolicyEvaluatorTest {

    @Test
    fun allow_lightOperation() = runTest {
        val evaluator = BatteryPolicyEvaluator(DefaultPolicyEnvironment(batteryPercent = 5))
        val result = evaluator.evaluate(request())
        assertEquals(PolicyVerdict.ALLOW, result.verdict)
    }

    @Test
    fun deny_heavyOperationOnLowBattery() = runTest {
        val evaluator = BatteryPolicyEvaluator(
            DefaultPolicyEnvironment(batteryPercent = 10, lowPowerMode = false),
        )
        val result = evaluator.evaluate(
            request(inputs = mapOf("heavyOperation" to "true")),
        )
        assertEquals(PolicyVerdict.DENY, result.verdict)
    }

    @Test
    fun deny_heavyOperationWhenThermalThrottled() = runTest {
        val evaluator = BatteryPolicyEvaluator(
            DefaultPolicyEnvironment(batteryPercent = 80, thermalThrottled = true),
        )
        val result = evaluator.evaluate(
            request(inputs = mapOf("heavyOperation" to "true")),
        )
        assertEquals(PolicyVerdict.DENY, result.verdict)
    }

    @Test
    fun allow_heavyOperationWhenBatteryOk() = runTest {
        val evaluator = BatteryPolicyEvaluator(DefaultPolicyEnvironment(batteryPercent = 80))
        val result = evaluator.evaluate(
            request(actionType = "media", inputs = mapOf("operation" to "transcode_video")),
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
