package com.nova.runtime.policy.context

/** Abstract device/runtime state used by policy evaluators (no Android APIs). */
interface PolicyEnvironment {
    fun grantedPermissions(): Set<String>
    fun batteryLevelPercent(): Int
    fun isThermalThrottled(): Boolean
    fun isLowPowerMode(): Boolean
}

/** Placeholder environment with configurable values for tests and Sprint 0 wiring. */
class DefaultPolicyEnvironment(
    private val permissions: Set<String> = emptySet(),
    private val batteryPercent: Int = 100,
    private val thermalThrottled: Boolean = false,
    private val lowPowerMode: Boolean = false,
) : PolicyEnvironment {
    override fun grantedPermissions(): Set<String> = permissions
    override fun batteryLevelPercent(): Int = batteryPercent
    override fun isThermalThrottled(): Boolean = thermalThrottled
    override fun isLowPowerMode(): Boolean = lowPowerMode
}
