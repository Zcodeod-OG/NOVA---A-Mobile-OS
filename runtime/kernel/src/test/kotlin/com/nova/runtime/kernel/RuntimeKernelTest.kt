package com.nova.runtime.kernel

import com.nova.runtime.events.system.SystemEvents
import com.nova.runtime.kernel.api.ModuleRegistrationContext
import com.nova.runtime.kernel.api.RuntimeModuleDescriptor
import com.nova.runtime.models.RuntimeLifecycleState
import com.nova.runtime.models.RuntimeModule
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class RuntimeKernelTest {

    @Test
    fun bootstrap_initializesKernelInfrastructure() = runTest {
        val kernel = RuntimeKernel.create()
        kernel.bootstrap(
            modules = listOf(
                object : RuntimeModuleDescriptor {
                    override val module = RuntimeModule.KERNEL
                    override val version = 1
                    override suspend fun register(context: ModuleRegistrationContext) = Unit
                },
            ),
        )

        assertEquals(RuntimeLifecycleState.READY, kernel.lifecycleManager.state.value)
        val eventTypes = kernel.eventBus.publishedEvents().map { it.eventType }
        assertEquals(true, SystemEvents.RUNTIME_READY in eventTypes)
    }

    @Test
    fun shutdown_transitionsToStopped() = runTest {
        val kernel = RuntimeKernel.create()
        kernel.bootstrap()
        kernel.shutdown()
        assertEquals(RuntimeLifecycleState.STOPPED, kernel.lifecycleManager.state.value)
    }
}
