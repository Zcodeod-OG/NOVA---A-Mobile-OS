package com.nova.runtime.kernel.registry

import com.nova.runtime.error.NovaException
import com.nova.runtime.kernel.api.NovaService
import com.nova.runtime.models.RuntimeModule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DefaultServiceRegistryTest {

    private val registry = DefaultServiceRegistry()

    private class TestService : NovaService {
        override val module = RuntimeModule.CONVERSATION
    }

    @Test
    fun register_andGet_returnsService() {
        val service = TestService()
        registry.register(TestService::class, service)
        assertEquals(service, registry.get(TestService::class))
    }

    @Test
    fun register_duplicateService_throws() {
        registry.register(TestService::class, TestService())
        assertFailsWith<NovaException> {
            registry.register(TestService::class, TestService())
        }
    }

    @Test
    fun get_unregisteredService_throws() {
        assertFailsWith<NovaException> {
            registry.get(TestService::class)
        }
    }

    @Test
    fun contains_reflectsRegistrationState() {
        assertTrue(!registry.contains(TestService::class))
        registry.register(TestService::class, TestService())
        assertTrue(registry.contains(TestService::class))
    }

    @Test
    fun clear_removesAllServices() {
        registry.register(TestService::class, TestService())
        registry.clear()
        assertEquals(0, registry.registeredTypes().size)
    }

    @Test
    fun register_notifiesRegistrationListeners() {
        var notified = false
        registry.addRegistrationListener { _, _ -> notified = true }
        registry.register(TestService::class, TestService())
        assertTrue(notified)
    }
}
