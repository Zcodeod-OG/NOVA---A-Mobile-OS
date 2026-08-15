package com.nova.runtime.capability.lifecycle

import com.nova.runtime.capability.model.CapabilityLifecycleState
import com.nova.runtime.capability.provider.StubCommunicationProvider
import com.nova.runtime.capability.registry.DefaultCapabilityRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CapabilityLifecycleManagerTest {

    private val registry = DefaultCapabilityRegistry(listOf(StubCommunicationProvider()))
    private val lifecycle = DefaultCapabilityLifecycleManager(registry)

    @Test
    fun getState_registeredProvider_isActive() {
        assertEquals(
            CapabilityLifecycleState.ACTIVE,
            lifecycle.getState("stub-communication", "1.0.0"),
        )
    }

    @Test
    fun suspend_changesStateToSuspended() {
        assertTrue(lifecycle.suspend("stub-communication", "1.0.0"))
        assertEquals(
            CapabilityLifecycleState.SUSPENDED,
            lifecycle.getState("stub-communication", "1.0.0"),
        )
    }

    @Test
    fun activate_reactivatesSuspendedProvider() {
        lifecycle.suspend("stub-communication", "1.0.0")
        assertTrue(lifecycle.activate("stub-communication", "1.0.0"))
        assertEquals(
            CapabilityLifecycleState.ACTIVE,
            lifecycle.getState("stub-communication", "1.0.0"),
        )
    }

    @Test
    fun markUnhealthy_setsUnhealthyState() {
        assertTrue(lifecycle.markUnhealthy("stub-communication", "1.0.0"))
        assertEquals(
            CapabilityLifecycleState.UNHEALTHY,
            lifecycle.getState("stub-communication", "1.0.0"),
        )
    }

    @Test
    fun deregister_setsDeregisteredState() {
        assertTrue(lifecycle.deregister("stub-communication", "1.0.0"))
        assertEquals(
            CapabilityLifecycleState.DEREGISTERED,
            lifecycle.getState("stub-communication", "1.0.0"),
        )
    }

    @Test
    fun getState_unknownProvider_returnsNull() {
        assertNull(lifecycle.getState("missing", "1.0.0"))
    }

    @Test
    fun activate_unknownProvider_returnsFalse() {
        assertFalse(lifecycle.activate("missing", "1.0.0"))
    }
}
