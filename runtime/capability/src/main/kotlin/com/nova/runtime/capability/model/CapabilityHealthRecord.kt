package com.nova.runtime.capability.model

import java.time.Instant

data class CapabilityHealthRecord(
    val capabilityKey: String,
    val healthy: Boolean,
    val lastHeartbeatAt: Instant?,
    val consecutiveFailures: Int = 0,
    val lastError: String? = null,
)
