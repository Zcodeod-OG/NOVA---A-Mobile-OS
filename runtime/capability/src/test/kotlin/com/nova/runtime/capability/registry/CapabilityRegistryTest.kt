package com.nova.runtime.capability.registry

import com.nova.runtime.capability.model.CapabilityLifecycleState
import com.nova.runtime.capability.provider.StubCommunicationProvider
import com.nova.runtime.capability.provider.StubTimeProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CapabilityRegistryTest {

    @Test
    fun registerAndLookup_byNameAndVersion() {
        val registry = DefaultCapabilityRegistry(listOf(StubCommunicationProvider()))

        val registration = registry.lookup("stub-communication", "1.0.0")
        assertNotNull(registration)
        assertEquals("communication", registration.metadata.capabilityType)
        assertEquals(CapabilityLifecycleState.ACTIVE, registration.state)
    }

    @Test
    fun lookupByType_returnsMatchingProviders() {
        val registry = DefaultCapabilityRegistry(
            listOf(StubCommunicationProvider(), StubTimeProvider()),
        )

        val communication = registry.lookupByType("communication")
        assertEquals(1, communication.size)
        assertEquals("stub-communication", communication.first().metadata.name)

        val time = registry.lookupByType("time")
        assertEquals(1, time.size)
    }

    @Test
    fun updateState_changesLifecycle() {
        val registry = DefaultCapabilityRegistry(listOf(StubCommunicationProvider()))

        assertTrue(registry.updateState("stub-communication", "1.0.0", CapabilityLifecycleState.SUSPENDED))
        assertEquals(
            CapabilityLifecycleState.SUSPENDED,
            registry.lookup("stub-communication", "1.0.0")?.state,
        )
    }

    @Test
    fun deregister_marksDeregistered() {
        val registry = DefaultCapabilityRegistry(listOf(StubCommunicationProvider()))

        assertTrue(registry.deregister("stub-communication", "1.0.0"))
        assertEquals(
            CapabilityLifecycleState.DEREGISTERED,
            registry.lookup("stub-communication", "1.0.0")?.state,
        )
    }

    @Test
    fun lookup_unknownProvider_returnsNull() {
        val registry = DefaultCapabilityRegistry()
        assertNull(registry.lookup("missing", "1.0.0"))
    }

    @Test
    fun all_returnsAllRegistrations() {
        val registry = DefaultCapabilityRegistry(
            listOf(StubCommunicationProvider(), StubTimeProvider()),
        )
        assertEquals(2, registry.all().size)
    }
}
