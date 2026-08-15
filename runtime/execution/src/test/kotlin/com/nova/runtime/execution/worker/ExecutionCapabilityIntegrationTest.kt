package com.nova.runtime.execution.worker

import com.nova.runtime.capability.CapabilityFrameworkImpl
import com.nova.runtime.capability.events.CapabilityEventPublisher
import com.nova.runtime.capability.health.DefaultCapabilityHealthMonitor
import com.nova.runtime.capability.lifecycle.DefaultCapabilityLifecycleManager
import com.nova.runtime.capability.provider.StubTimeProvider
import com.nova.runtime.capability.registry.DefaultCapabilityRegistry
import com.nova.runtime.capability.resolver.DefaultCapabilityProviderResolver
import com.nova.runtime.capability.transaction.DefaultCapabilityTransactionManager
import com.nova.runtime.events.EventSubscriber
import com.nova.runtime.events.InMemoryEventBus
import com.nova.runtime.events.capability.CapabilityEvents
import com.nova.runtime.models.ActionNode
import com.nova.runtime.models.Priority
import com.nova.runtime.utils.logging.StructuredLogger
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class ExecutionCapabilityIntegrationTest {

    private val logger = StructuredLogger()
    private val eventBus = InMemoryEventBus(logger)

    @Test
    fun stubExecutor_invokesCapabilityFramework() = runTest {
        val registry = DefaultCapabilityRegistry(listOf(StubTimeProvider()))
        val lifecycle = DefaultCapabilityLifecycleManager(registry)
        val framework = CapabilityFrameworkImpl(
            registry = registry,
            resolver = DefaultCapabilityProviderResolver(registry, lifecycle),
            lifecycleManager = lifecycle,
            healthMonitor = DefaultCapabilityHealthMonitor(registry, lifecycle),
            transactionManager = DefaultCapabilityTransactionManager(),
            eventPublisher = CapabilityEventPublisher(eventBus),
            logger = logger,
        )
        val executor = StubCapabilityActionExecutor(framework)
        val traceId = UUID.randomUUID()
        val events = mutableListOf<String>()
        eventBus.subscribe(
            object : EventSubscriber {
                override val subscriberId = "execution-capability-test"
                override val eventTypes = setOf(CapabilityEvents.EXECUTED)
                override suspend fun onEvent(event: com.nova.runtime.events.RuntimeEvent) {
                    if (event.traceId == traceId) events.add(event.eventType)
                }
            },
        )

        val node = ActionNode(
            id = UUID.randomUUID(),
            actionType = "execute_capability",
            inputs = mapOf(
                "capabilityType" to "time",
                "operation" to "calendar",
                "title" to "Meeting",
            ),
            outputs = emptyMap(),
            dependencies = emptyList(),
            timeoutMs = 5_000,
            retryPolicy = "default",
            rollbackPolicy = "none",
            executionPriority = Priority.NORMAL,
        )

        val outcome = executor.execute(node, traceId)

        assertIs<NodeExecutionOutcome.Failure>(outcome)
        assertEquals("CAPABILITY_STUB_ONLY", outcome.error.code)
        assertEquals(false, outcome.retryable)
        assertTrue(outcome.error.userVisibleMessage.contains("time"))
        assertTrue(CapabilityEvents.EXECUTED in events)
    }
}
