package com.nova.runtime.capability.lifecycle

import com.nova.runtime.capability.model.CapabilityLifecycleState
import com.nova.runtime.capability.registry.CapabilityRegistry

interface CapabilityLifecycleManager {
    fun activate(name: String, version: String): Boolean
    fun suspend(name: String, version: String): Boolean
    fun markUnhealthy(name: String, version: String): Boolean
    fun deregister(name: String, version: String): Boolean
    fun getState(name: String, version: String): CapabilityLifecycleState?
}

class DefaultCapabilityLifecycleManager(
    private val registry: CapabilityRegistry,
) : CapabilityLifecycleManager {

    override fun activate(name: String, version: String): Boolean =
        registry.updateState(name, version, CapabilityLifecycleState.ACTIVE)

    override fun suspend(name: String, version: String): Boolean =
        registry.updateState(name, version, CapabilityLifecycleState.SUSPENDED)

    override fun markUnhealthy(name: String, version: String): Boolean =
        registry.updateState(name, version, CapabilityLifecycleState.UNHEALTHY)

    override fun deregister(name: String, version: String): Boolean =
        registry.deregister(name, version)

    override fun getState(name: String, version: String): CapabilityLifecycleState? =
        registry.lookup(name, version)?.state
}
