package com.nova.runtime.capability.health

import com.nova.runtime.capability.lifecycle.DefaultCapabilityLifecycleManager
import com.nova.runtime.capability.model.CapabilityLifecycleState
import com.nova.runtime.capability.provider.StubCapabilityProvider
import com.nova.runtime.capability.registry.DefaultCapabilityRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class CapabilityHealthMonitorTest {

    @Test
    fun checkHealth_healthyProvider_recordsHeartbeat() = runTest {
        val registry = DefaultCapabilityRegistry(listOf(StubCommunicationProvider()))
        val lifecycle = DefaultCapabilityLifecycleManager(registry)
        val monitor = DefaultCapabilityHealthMonitor(registry, lifecycle)

        assertTrue(monitor.checkHealth("stub-communication", "1.0.0"))

        val record = monitor.getHealth("stub-communication", "1.0.0")
        assertNotNull(record)
        assertTrue(record.healthy)
        assertEquals(0, record.consecutiveFailures)
        assertNotNull(record.lastHeartbeatAt)
    }

    @Test
    fun recordFailure_incrementsConsecutiveFailures() = runTest {
        val registry = DefaultCapabilityRegistry(listOf(StubCommunicationProvider()))
        val lifecycle = DefaultCapabilityLifecycleManager(registry)
        val monitor = DefaultCapabilityHealthMonitor(registry, lifecycle, failureThreshold = 3)

        monitor.recordFailure("stub-communication", "1.0.0", "error-1")
        monitor.recordFailure("stub-communication", "1.0.0", "error-2")

        val record = monitor.getHealth("stub-communication", "1.0.0")
        assertNotNull(record)
        assertEquals(2, record.consecutiveFailures)
        assertTrue(record.healthy)
    }

    @Test
    fun recordFailure_atThreshold_marksUnhealthy() = runTest {
        val registry = DefaultCapabilityRegistry(listOf(StubCommunicationProvider()))
        val lifecycle = DefaultCapabilityLifecycleManager(registry)
        val monitor = DefaultCapabilityHealthMonitor(registry, lifecycle, failureThreshold = 2)

        monitor.recordFailure("stub-communication", "1.0.0", "error-1")
        monitor.recordFailure("stub-communication", "1.0.0", "error-2")

        assertEquals(
            CapabilityLifecycleState.UNHEALTHY,
            lifecycle.getState("stub-communication", "1.0.0"),
        )
        val record = monitor.getHealth("stub-communication", "1.0.0")
        assertNotNull(record)
        assertFalse(record.healthy)
    }

    @Test
    fun runHealthChecks_returnsAllActiveProviders() = runTest {
        val registry = DefaultCapabilityRegistry(listOf(StubCommunicationProvider()))
        val lifecycle = DefaultCapabilityLifecycleManager(registry)
        val monitor = DefaultCapabilityHealthMonitor(registry, lifecycle)

        val results = monitor.runHealthChecks()
        assertEquals(1, results.size)
        assertTrue(results.containsKey("stub-communication:1.0.0"))
    }

    private class StubCommunicationProvider :
        StubCapabilityProvider(
            providerId = "stub-communication",
            capabilityType = "communication",
            version = "1.0.0",
            operations = setOf("send"),
        )

    private class UnhealthyProvider :
        StubCapabilityProvider(
            providerId = "unhealthy-provider",
            capabilityType = "device",
            version = "1.0.0",
            operations = setOf("execute"),
        ) {
        override suspend fun health(): Boolean = false
    }

    @Test
    fun checkHealth_unhealthyProvider_recordsFailure() = runTest {
        val unhealthy = UnhealthyProvider()
        val registry = DefaultCapabilityRegistry(listOf(unhealthy))
        val lifecycle = DefaultCapabilityLifecycleManager(registry)
        val monitor = DefaultCapabilityHealthMonitor(registry, lifecycle, failureThreshold = 5)

        assertFalse(monitor.checkHealth("unhealthy-provider", "1.0.0"))
        val record = monitor.getHealth("unhealthy-provider", "1.0.0")
        assertNotNull(record)
        assertEquals(1, record.consecutiveFailures)
    }
}
