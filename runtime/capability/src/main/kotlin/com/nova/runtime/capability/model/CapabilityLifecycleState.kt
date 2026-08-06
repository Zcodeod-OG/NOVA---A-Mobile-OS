package com.nova.runtime.capability.model

/** Lifecycle states for a registered capability provider — MSP §11. */
enum class CapabilityLifecycleState {
    REGISTERED,
    ACTIVE,
    SUSPENDED,
    UNHEALTHY,
    DEREGISTERED,
}
