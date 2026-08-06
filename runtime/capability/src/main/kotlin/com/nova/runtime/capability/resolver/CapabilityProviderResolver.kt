package com.nova.runtime.capability.resolver

import com.nova.runtime.capability.lifecycle.CapabilityLifecycleManager
import com.nova.runtime.capability.model.CapabilityLifecycleState
import com.nova.runtime.capability.model.CapabilityResolutionRequest
import com.nova.runtime.capability.model.CapabilityResolutionResult
import com.nova.runtime.capability.registry.CapabilityRegistry

interface CapabilityProviderResolver {
    suspend fun resolve(request: CapabilityResolutionRequest): CapabilityResolutionResult?
}

class DefaultCapabilityProviderResolver(
    private val registry: CapabilityRegistry,
    private val lifecycleManager: CapabilityLifecycleManager,
) : CapabilityProviderResolver {

    override suspend fun resolve(request: CapabilityResolutionRequest): CapabilityResolutionResult? {
        val candidates = registry.lookupByType(request.capabilityType)
            .filter { registration ->
                lifecycleManager.getState(registration.metadata.name, registration.metadata.version) ==
                    CapabilityLifecycleState.ACTIVE
            }
            .filter { registration ->
                request.operation in registration.provider.supportedOperations() ||
                    request.operation == "rollback"
            }

        if (candidates.isEmpty()) return null

        val preferredProviderId = request.constraints["providerId"]
        val selected = if (preferredProviderId != null) {
            candidates.firstOrNull { it.provider.providerId == preferredProviderId }
        } else {
            candidates.firstOrNull()
        } ?: return null

        return CapabilityResolutionResult(
            provider = selected.provider,
            metadata = selected.metadata,
        )
    }
}
