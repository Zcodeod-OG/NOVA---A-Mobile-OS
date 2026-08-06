package com.nova.runtime.capability.model

import com.nova.runtime.capability.provider.CapabilityProvider

data class CapabilityResolutionRequest(
    val capabilityType: String,
    val operation: String,
    val constraints: Map<String, String> = emptyMap(),
)

data class CapabilityResolutionResult(
    val provider: CapabilityProvider,
    val metadata: CapabilityMetadata,
)
