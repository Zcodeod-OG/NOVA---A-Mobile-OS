package com.nova.runtime.capability

import com.nova.runtime.capability.events.CapabilityEventPublisher
import com.nova.runtime.capability.health.DefaultCapabilityHealthMonitor
import com.nova.runtime.capability.lifecycle.DefaultCapabilityLifecycleManager
import com.nova.runtime.capability.provider.StubCommunicationProvider
import com.nova.runtime.capability.provider.defaultStubProviders
import com.nova.runtime.capability.registry.DefaultCapabilityRegistry
import com.nova.runtime.capability.resolver.DefaultCapabilityProviderResolver
import com.nova.runtime.capability.transaction.DefaultCapabilityTransactionManager
import com.nova.runtime.events.EventSubscriber
import com.nova.runtime.events.InMemoryEventBus
import com.nova.runtime.events.capability.CapabilityEvents
import com.nova.runtime.models.contracts.CapabilityRequest
import com.nova.runtime.models.contracts.CapabilityResult
import com.nova.runtime.utils.logging.StructuredLogger
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class CapabilityFrameworkImplTest {

    private val logger = StructuredLogger()
    private val eventBus = InMemoryEventBus(logger)

    @Test
    fun execute_resolvesAndExecutesStubProvider() = runTest {
        val framework = createFramework(listOf(StubCommunicationProvider()))
        val traceId = UUID.randomUUID()

        val result = framework.execute(
            CapabilityRequest(
                capabilityType = "communication",
                operation = "send",
                parameters = mapOf("recipient" to "rahul"),
                traceId = traceId,
            ),
        )

        assertIs<CapabilityResult.Success>(result)
        assertEquals("stub-communication", result.output["providerId"])
        assertEquals("rahul", result.output["recipient"])
    }

    @Test
    fun execute_unavailableCapability_returnsFailure() = runTest {
        val framework = createFramework(emptyList())
        val traceId = UUID.randomUUID()

        val result = framework.execute(
            CapabilityRequest(
                capabilityType = "communication",
                operation = "send",
                parameters = emptyMap(),
                traceId = traceId,
            ),
        )

        assertIs<CapabilityResult.Failure>(result)
        assertEquals("CAPABILITY_UNAVAILABLE", result.error.code)
    }

    @Test
    fun execute_publishesCapabilityEvents() = runTest {
        val framework = createFramework(listOf(StubCommunicationProvider()))
        val traceId = UUID.randomUUID()
        val received = mutableListOf<String>()
        eventBus.subscribe(
            object : EventSubscriber {
                override val subscriberId = "capability-test"
                override val eventTypes = setOf(
                    CapabilityEvents.RESOLVED,
                    CapabilityEvents.SELECTED,
                    CapabilityEvents.EXECUTED,
                    CapabilityEvents.FAILED,
                    CapabilityEvents.UNAVAILABLE,
                )
                override suspend fun onEvent(event: com.nova.runtime.events.RuntimeEvent) {
                    if (event.traceId == traceId) {
                        received.add(event.eventType)
                    }
                }
            },
        )

        framework.execute(
            CapabilityRequest(
                capabilityType = "communication",
                operation = "send",
                parameters = emptyMap(),
                traceId = traceId,
            ),
        )

        assertTrue(CapabilityEvents.RESOLVED in received)
        assertTrue(CapabilityEvents.SELECTED in received)
        assertTrue(CapabilityEvents.EXECUTED in received)
    }

    @Test
    fun health_activeCapabilityType_returnsTrue() = runTest {
        val framework = createFramework(defaultStubProviders())
        assertTrue(framework.health("communication"))
    }

    @Test
    fun discover_returnsActiveCapabilityKeys() = runTest {
        val framework = createFramework(defaultStubProviders())
        val discovered = framework.discover()
        assertEquals(12, discovered.size)
        assertTrue(discovered.any { it.startsWith("stub-communication:") })
    }

    @Test
    fun execute_suspendedProvider_returnsNotActive() = runTest {
        val registry = DefaultCapabilityRegistry(listOf(StubCommunicationProvider()))
        val lifecycle = DefaultCapabilityLifecycleManager(registry)
        lifecycle.suspend("stub-communication", "1.0.0")
        val framework = createFrameworkWith(registry, lifecycle)
        val traceId = UUID.randomUUID()

        val result = framework.execute(
            CapabilityRequest(
                capabilityType = "communication",
                operation = "send",
                parameters = emptyMap(),
                traceId = traceId,
            ),
        )

        assertIs<CapabilityResult.Failure>(result)
        assertEquals("CAPABILITY_UNAVAILABLE", result.error.code)
    }

    private fun createFramework(providers: List<com.nova.runtime.capability.provider.CapabilityProvider>): CapabilityFramework {
        val registry = DefaultCapabilityRegistry(providers)
        val lifecycle = DefaultCapabilityLifecycleManager(registry)
        return createFrameworkWith(registry, lifecycle)
    }

    private fun createFrameworkWith(
        registry: DefaultCapabilityRegistry,
        lifecycle: DefaultCapabilityLifecycleManager,
    ): CapabilityFramework {
        val resolver = DefaultCapabilityProviderResolver(registry, lifecycle)
        val healthMonitor = DefaultCapabilityHealthMonitor(registry, lifecycle)
        return CapabilityFrameworkImpl(
            registry = registry,
            resolver = resolver,
            lifecycleManager = lifecycle,
            healthMonitor = healthMonitor,
            transactionManager = DefaultCapabilityTransactionManager(),
            eventPublisher = CapabilityEventPublisher(eventBus),
            logger = logger,
        )
    }
}
