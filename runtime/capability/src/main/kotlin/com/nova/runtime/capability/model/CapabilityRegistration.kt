package com.nova.runtime.capability.model

import com.nova.runtime.capability.provider.CapabilityProvider
import java.time.Instant

data class CapabilityRegistration(
    val metadata: CapabilityMetadata,
    val provider: CapabilityProvider,
    val state: CapabilityLifecycleState = CapabilityLifecycleState.REGISTERED,
    val registeredAt: Instant = Instant.now(),
)
