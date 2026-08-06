package com.nova.runtime.kernel.module

import com.nova.runtime.events.EventBus
import com.nova.runtime.events.EventSubscriber
import com.nova.runtime.kernel.api.ModuleRegistrationContext
import com.nova.runtime.kernel.api.NovaService
import com.nova.runtime.kernel.api.RuntimeModuleDescriptor
import com.nova.runtime.utils.logging.NovaLogger
import com.nova.runtime.kernel.registry.ServiceRegistry
import com.nova.runtime.models.RuntimeModule
import java.util.concurrent.CopyOnWriteArrayList

interface ModuleRegistry {
    fun register(descriptor: RuntimeModuleDescriptor)
    suspend fun installAll(context: ModuleRegistrationContext)
    fun installedModules(): List<RuntimeModule>
}

class DefaultModuleRegistry : ModuleRegistry {
    private val descriptors = CopyOnWriteArrayList<RuntimeModuleDescriptor>()

    override fun register(descriptor: RuntimeModuleDescriptor) {
        check(descriptors.none { it.module == descriptor.module }) {
            "Module already registered: ${descriptor.module}"
        }
        descriptors.add(descriptor)
    }

    override suspend fun installAll(context: ModuleRegistrationContext) {
        for (descriptor in descriptors.sortedBy { it.module.ordinal }) {
            descriptor.register(context)
        }
    }

    override fun installedModules(): List<RuntimeModule> =
        descriptors.map { it.module }
}

class DefaultModuleRegistrationContext(
    private val serviceRegistry: ServiceRegistry,
    private val eventBus: EventBus,
    private val logger: NovaLogger,
) : ModuleRegistrationContext {

    override fun <T : NovaService> registerService(serviceType: Class<T>, instance: T) {
        serviceRegistry.register(serviceType, instance)
        logger.debug(
            module = instance.module.name,
            message = "Registered service ${serviceType.simpleName}",
        )
    }

    override fun registerEventSubscriber(subscriber: EventSubscriber) {
        eventBus.subscribe(subscriber)
        logger.debug(
            module = subscriber.subscriberId,
            message = "Registered event subscriber",
        )
    }
}
