package com.nova.runtime.inference.tier

/**
 * MVP inference tiers per TDD §7 and MSP §5.
 *
 * Tier 0 — deterministic rules
 * Tier 1 — lightweight rule engine
 * Tier 2 — full placeholder model (lightweight model slot for MVP)
 */
enum class InferenceTier(val level: Int, val label: String) {
    DETERMINISTIC(0, "deterministic"),
    LIGHT(1, "light"),
    FULL(2, "full"),
    ;

    companion object {
        fun fromLevel(level: Int): InferenceTier? = entries.find { it.level == level }

        fun fromLevelOrDefault(level: Int, default: InferenceTier = DETERMINISTIC): InferenceTier =
            fromLevel(level) ?: default
    }
}
