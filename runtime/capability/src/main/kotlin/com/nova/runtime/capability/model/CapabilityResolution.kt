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
    /**
     * Operation name accepted by [provider], which may differ from the pipeline's short form
     * after [com.nova.runtime.capability.resolver.CapabilityOperationAliases] remapping
     * (e.g. pipeline `create` → provider `alarm.create`).
     */
    val resolvedOperation: String,
)
