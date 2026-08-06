package com.nova.runtime.events

import com.nova.runtime.utils.logging.StructuredLogger
import com.nova.runtime.models.RuntimeModule
import kotlinx.coroutines.test.runTest
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class InMemoryEventBusTest {

    private val logger = StructuredLogger()
    private val bus = InMemoryEventBus(logger)

    @Test
    fun publish_deliversToMatchingSubscribers() = runTest {
        val counter = AtomicInteger(0)
        bus.subscribe(
            object : EventSubscriber {
                override val subscriberId = "test-subscriber"
                override val eventTypes = setOf("GraphBuilt")
                override suspend fun onEvent(event: RuntimeEvent) {
                    counter.incrementAndGet()
                }
            },
        )

        val traceId = UUID.randomUUID()
        bus.publish(
            RuntimeEvent(
                traceId = traceId,
                sourceModule = RuntimeModule.PLANNER,
                eventType = "GraphBuilt",
            ),
        )

        assertEquals(1, counter.get())
        assertEquals(1, bus.publishedEvents().size)
    }

    @Test
    fun publish_continuesWhenSubscriberFails() = runTest {
        bus.subscribe(
            object : EventSubscriber {
                override val subscriberId = "failing"
                override val eventTypes = setOf("MemoryRetrieved")
                override suspend fun onEvent(event: RuntimeEvent) {
                    error("handler failure")
                }
            },
        )

        val successCounter = AtomicInteger(0)
        bus.subscribe(
            object : EventSubscriber {
                override val subscriberId = "success"
                override val eventTypes = setOf("MemoryRetrieved")
                override suspend fun onEvent(event: RuntimeEvent) {
                    successCounter.incrementAndGet()
                }
            },
        )

        bus.publish(
            RuntimeEvent(
                traceId = UUID.randomUUID(),
                sourceModule = RuntimeModule.MEMORY,
                eventType = "MemoryRetrieved",
            ),
        )

        assertEquals(1, successCounter.get())
    }

    @Test
    fun send_dispatchesToRegisteredCommandHandler() = runTest {
        bus.registerCommandHandler(
            object : CommandHandler {
                override val commandType = "RetrieveMemory"
                override suspend fun handle(command: RuntimeCommand): Any? = "ok"
            },
        )

        val result = bus.send(
            RuntimeCommand(
                traceId = UUID.randomUUID(),
                sourceModule = RuntimeModule.MEMORY,
                commandType = "RetrieveMemory",
            ),
        )

        assertEquals("ok", result)
    }

    @Test
    fun send_failsWhenNoHandlerRegistered() = runTest {
        assertFailsWith<com.nova.runtime.error.NovaException> {
            bus.send(
                RuntimeCommand(
                    traceId = UUID.randomUUID(),
                    sourceModule = RuntimeModule.KERNEL,
                    commandType = "UnknownCommand",
                ),
            )
        }
    }

    @Test
    fun unsubscribe_removesSubscriber() = runTest {
        val counter = AtomicInteger(0)
        bus.subscribe(
            object : EventSubscriber {
                override val subscriberId = "removable"
                override val eventTypes = setOf("PlanningStarted")
                override suspend fun onEvent(event: RuntimeEvent) {
                    counter.incrementAndGet()
                }
            },
        )
        bus.unsubscribe("removable")

        bus.publish(
            RuntimeEvent(
                traceId = UUID.randomUUID(),
                sourceModule = RuntimeModule.PLANNER,
                eventType = "PlanningStarted",
            ),
        )

        assertEquals(0, counter.get())
    }

    @Test
    fun publishedEvents_archivesAllEvents() = runTest {
        repeat(3) {
            bus.publish(
                RuntimeEvent(
                    traceId = UUID.randomUUID(),
                    sourceModule = RuntimeModule.KERNEL,
                    eventType = "RuntimeReady",
                ),
            )
        }
        assertTrue(bus.publishedEvents().size >= 3)
    }
}
