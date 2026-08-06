package com.nova.runtime.policy.evaluator

import com.nova.runtime.models.contracts.ActionPolicyRequest
import com.nova.runtime.models.contracts.PolicyVerdict
import com.nova.runtime.policy.context.PolicyEnvironment

/** Restricts heavy operations under low battery or thermal stress. */
class BatteryPolicyEvaluator(
    private val environment: PolicyEnvironment,
    private val lowBatteryThresholdPercent: Int = DEFAULT_LOW_BATTERY_THRESHOLD,
) : PolicyEvaluator {
    override val id: String = "battery"

    override suspend fun evaluate(request: ActionPolicyRequest): PolicyEvaluatorResult {
        if (!isHeavyOperation(request)) {
            return allow("Not a battery-intensive operation")
        }

        if (request.node.inputs["ignoreBatteryPolicy"]?.equals("true", ignoreCase = true) == true) {
            return allow("Battery policy bypassed for action")
        }

        val battery = environment.batteryLevelPercent()
        val lowPower = environment.isLowPowerMode()
        val thermal = environment.isThermalThrottled()

        if (thermal) {
            return PolicyEvaluatorResult(
                verdict = PolicyVerdict.DENY,
                rationale = "Heavy operation blocked due to thermal throttling",
            )
        }

        if (lowPower || battery <= lowBatteryThresholdPercent) {
            return PolicyEvaluatorResult(
                verdict = PolicyVerdict.DENY,
                rationale = "Heavy operation blocked: battery at $battery% (threshold $lowBatteryThresholdPercent%)",
            )
        }

        return allow("Battery and thermal conditions acceptable")
    }

    internal fun isHeavyOperation(request: ActionPolicyRequest): Boolean {
        val node = request.node
        if (node.inputs["heavyOperation"]?.equals("true", ignoreCase = true) == true) {
            return true
        }

        val simulatedDelay = node.inputs["simulateDelayMs"]?.toLongOrNull() ?: 0L
        if (simulatedDelay >= HEAVY_DELAY_MS) {
            return true
        }

        val actionKey = listOf(node.actionType, node.inputs["operation"])
            .filterNotNull()
            .joinToString(".")
            .lowercase()

        return HEAVY_PATTERNS.any { actionKey.contains(it) }
    }

    companion object {
        const val DEFAULT_LOW_BATTERY_THRESHOLD = 15
        private const val HEAVY_DELAY_MS = 5_000L

        private val HEAVY_PATTERNS = listOf(
            "transcode",
            "download_large",
            "sync_all",
            "bulk_upload",
            "model_inference",
            "scan_media",
        )
    }

    private fun allow(rationale: String) = PolicyEvaluatorResult(PolicyVerdict.ALLOW, rationale)
}
