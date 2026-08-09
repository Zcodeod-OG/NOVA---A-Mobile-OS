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
        for (variant in CapabilityOperationAliases.variants(request.capabilityType, request.operation)) {
            resolveDirect(
                request = request,
                capabilityType = variant.capabilityType,
                operation = variant.operation,
            )?.let { return it }
        }
        return null
    }

    private suspend fun resolveDirect(
        request: CapabilityResolutionRequest,
        capabilityType: String,
        operation: String,
    ): CapabilityResolutionResult? {
        val candidates = registry.lookupByType(capabilityType)
            .filter { registration ->
                lifecycleManager.getState(registration.metadata.name, registration.metadata.version) ==
                    CapabilityLifecycleState.ACTIVE
            }
            .filter { registration ->
                operation in registration.provider.supportedOperations() ||
                    operation == "rollback"
            }

        if (candidates.isEmpty()) return null

        val preferredProviderId = request.constraints["providerId"]
        val selected = if (preferredProviderId != null) {
            candidates.firstOrNull { it.provider.providerId == preferredProviderId }
        } else {
            candidates.sortedBy { if (it.provider.providerId.startsWith("android-")) 0 else 1 }
                .firstOrNull()
        } ?: return null

        return CapabilityResolutionResult(
            provider = selected.provider,
            metadata = selected.metadata,
            resolvedOperation = operation,
        )
    }
}
