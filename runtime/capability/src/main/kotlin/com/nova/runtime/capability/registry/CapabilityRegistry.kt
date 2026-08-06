package com.nova.runtime.capability.registry

import com.nova.runtime.capability.model.CapabilityLifecycleState
import com.nova.runtime.capability.model.CapabilityMetadata
import com.nova.runtime.capability.model.CapabilityRegistration
import com.nova.runtime.capability.provider.CapabilityProvider
import java.util.concurrent.ConcurrentHashMap

interface CapabilityRegistry {
    fun register(provider: CapabilityProvider, state: CapabilityLifecycleState = CapabilityLifecycleState.ACTIVE): CapabilityRegistration
    fun lookup(name: String, version: String? = null): CapabilityRegistration?
    fun lookupByType(capabilityType: String): List<CapabilityRegistration>
    fun lookupByKey(key: String): CapabilityRegistration?
    fun updateState(name: String, version: String, state: CapabilityLifecycleState): Boolean
    fun deregister(name: String, version: String): Boolean
    fun all(): List<CapabilityRegistration>
}

class DefaultCapabilityRegistry(
    initialProviders: List<CapabilityProvider> = emptyList(),
) : CapabilityRegistry {

    private val registrations = ConcurrentHashMap<String, CapabilityRegistration>()

    init {
        initialProviders.forEach { register(it) }
    }

    override fun register(
        provider: CapabilityProvider,
        state: CapabilityLifecycleState,
    ): CapabilityRegistration {
        val metadata = provider.metadata()
        val registration = CapabilityRegistration(
            metadata = metadata,
            provider = provider,
            state = state,
        )
        registrations[metadata.key] = registration
        return registration
    }

    override fun lookup(name: String, version: String?): CapabilityRegistration? {
        if (version != null) {
            return registrations["$name:$version"]
        }
        return registrations.values.firstOrNull { it.metadata.name == name }
    }

    override fun lookupByType(capabilityType: String): List<CapabilityRegistration> =
        registrations.values.filter { it.metadata.capabilityType == capabilityType }

    override fun lookupByKey(key: String): CapabilityRegistration? = registrations[key]

    override fun updateState(name: String, version: String, state: CapabilityLifecycleState): Boolean {
        val key = "$name:$version"
        val existing = registrations[key] ?: return false
        registrations[key] = existing.copy(state = state)
        return true
    }

    override fun deregister(name: String, version: String): Boolean {
        val key = "$name:$version"
        val existing = registrations[key] ?: return false
        registrations[key] = existing.copy(state = CapabilityLifecycleState.DEREGISTERED)
        return true
    }

    override fun all(): List<CapabilityRegistration> = registrations.values.toList()

    fun metadataFor(name: String, version: String): CapabilityMetadata? = lookup(name, version)?.metadata
}
