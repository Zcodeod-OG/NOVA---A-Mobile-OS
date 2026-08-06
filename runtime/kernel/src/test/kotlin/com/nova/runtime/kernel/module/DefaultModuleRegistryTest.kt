package com.nova.runtime.kernel.module

import com.nova.runtime.events.EventSubscriber
import com.nova.runtime.events.InMemoryEventBus
import com.nova.runtime.events.RuntimeEvent
import com.nova.runtime.kernel.api.ModuleRegistrationContext
import com.nova.runtime.kernel.api.NovaService
import com.nova.runtime.kernel.api.RuntimeModuleDescriptor
import com.nova.runtime.utils.logging.StructuredLogger
import com.nova.runtime.kernel.registry.DefaultServiceRegistry
import com.nova.runtime.models.RuntimeModule
import kotlinx.coroutines.test.runTest
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DefaultModuleRegistryTest {

    private val logger = StructuredLogger()
    private val serviceRegistry = DefaultServiceRegistry()
    private val eventBus = InMemoryEventBus(logger)
    private val moduleRegistry = DefaultModuleRegistry()
    private val context = DefaultModuleRegistrationContext(serviceRegistry, eventBus, logger)

    private class StubService : NovaService {
        override val module = RuntimeModule.MEMORY
    }

    @Test
    fun installAll_registersServicesAndSubscribers() = runTest {
        val serviceRegistered = AtomicBoolean(false)
        val subscriberRegistered = AtomicBoolean(false)

        moduleRegistry.register(
            object : RuntimeModuleDescriptor {
                override val module = RuntimeModule.MEMORY
                override val version = 1

                override suspend fun register(context: ModuleRegistrationContext) {
                    context.registerService(StubService::class.java, StubService())
                    serviceRegistered.set(true)
                    context.registerEventSubscriber(
                        object : EventSubscriber {
                            override val subscriberId = "memory-sub"
                            override val eventTypes = setOf("MemoryRetrieved")
                            override suspend fun onEvent(event: RuntimeEvent) {
                                subscriberRegistered.set(true)
                            }
                        },
                    )
                }
            },
        )

        moduleRegistry.installAll(context)

        assertTrue(serviceRegistered.get())
        assertTrue(serviceRegistry.contains(StubService::class))
        assertEquals(listOf(RuntimeModule.MEMORY), moduleRegistry.installedModules())
    }
}
