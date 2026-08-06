package com.nova.runtime.kernel.lifecycle

import com.nova.runtime.events.InMemoryEventBus
import com.nova.runtime.events.system.SystemEvents
import com.nova.runtime.kernel.api.LifecycleAware
import com.nova.runtime.kernel.api.NovaService
import com.nova.runtime.utils.logging.StructuredLogger
import com.nova.runtime.kernel.registry.DefaultServiceRegistry
import com.nova.runtime.models.RuntimeLifecycleState
import com.nova.runtime.models.RuntimeModule
import kotlinx.coroutines.test.runTest
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals

class DefaultLifecycleManagerTest {

    private val logger = StructuredLogger()
    private val eventBus = InMemoryEventBus(logger)
    private val serviceRegistry = DefaultServiceRegistry()

    private class CountingService : NovaService, LifecycleAware {
        override val module = RuntimeModule.CONVERSATION
        val starts = AtomicInteger(0)
        val stops = AtomicInteger(0)

        override suspend fun onStart() {
            starts.incrementAndGet()
        }

        override suspend fun onStop() {
            stops.incrementAndGet()
        }
    }

    @Test
    fun initialize_transitionsToReadyAndPublishesEvents() = runTest {
        val lifecycleManager = DefaultLifecycleManager(serviceRegistry, eventBus, logger)
        val traceId = UUID.randomUUID()

        lifecycleManager.initialize(traceId)

        assertEquals(RuntimeLifecycleState.READY, lifecycleManager.state.value)
        val types = eventBus.publishedEvents().map { it.eventType }
        assertEquals(true, SystemEvents.RUNTIME_STARTED in types)
        assertEquals(true, SystemEvents.RUNTIME_READY in types)
    }

    @Test
    fun startAndStop_invokesLifecycleAwareServices() = runTest {
        val service = CountingService()
        serviceRegistry.register(CountingService::class, service)

        val lifecycleManager = DefaultLifecycleManager(serviceRegistry, eventBus, logger)
        lifecycleManager.initialize(UUID.randomUUID())
        lifecycleManager.start(UUID.randomUUID())
        lifecycleManager.stop(UUID.randomUUID())

        assertEquals(1, service.starts.get())
        assertEquals(1, service.stops.get())
        assertEquals(RuntimeLifecycleState.STOPPED, lifecycleManager.state.value)
    }
}
