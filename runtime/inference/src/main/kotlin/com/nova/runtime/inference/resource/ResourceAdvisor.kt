package com.nova.runtime.inference.resource

import com.nova.runtime.inference.tier.InferenceTier

data class DeviceResourceSnapshot(
    val batteryLow: Boolean = false,
    val thermalThrottled: Boolean = false,
    val highCpuLoad: Boolean = false,
)

interface ResourceAdvisor {
    fun adviseTier(requested: InferenceTier, resources: DeviceResourceSnapshot): InferenceTier
}

/** Downgrades tiers under resource pressure per TDD §7. */
class DefaultResourceAdvisor : ResourceAdvisor {
    override fun adviseTier(requested: InferenceTier, resources: DeviceResourceSnapshot): InferenceTier {
        if (resources.batteryLow || resources.thermalThrottled) {
            return when (requested) {
                InferenceTier.FULL -> InferenceTier.LIGHT
                InferenceTier.LIGHT -> InferenceTier.DETERMINISTIC
                InferenceTier.DETERMINISTIC -> InferenceTier.DETERMINISTIC
            }
        }
        if (resources.highCpuLoad && requested == InferenceTier.FULL) {
            return InferenceTier.LIGHT
        }
        return requested
    }
}
