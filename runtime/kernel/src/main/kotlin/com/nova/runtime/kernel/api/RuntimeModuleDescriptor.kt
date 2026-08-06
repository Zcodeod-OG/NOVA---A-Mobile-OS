package com.nova.runtime.kernel.api

import com.nova.runtime.models.RuntimeModule

/**
 * Describes a registrable runtime module and its service bindings.
 */
interface RuntimeModuleDescriptor {
    val module: RuntimeModule
    val version: Int

    /**
     * Registers services and event subscribers with the kernel infrastructure.
     */
    suspend fun register(context: ModuleRegistrationContext)
}

interface ModuleRegistrationContext {
    fun <T : NovaService> registerService(serviceType: Class<T>, instance: T)
    fun registerEventSubscriber(subscriber: com.nova.runtime.events.EventSubscriber)
}
