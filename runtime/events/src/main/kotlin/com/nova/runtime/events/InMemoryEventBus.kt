package com.nova.runtime.events

import com.nova.runtime.error.NovaError
import com.nova.runtime.error.NovaErrors
import com.nova.runtime.error.NovaException
import com.nova.runtime.utils.logging.NovaLogger
import com.nova.runtime.models.EventPriority
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.PriorityBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean

interface EventBus {
    fun subscribe(subscriber: EventSubscriber)
    fun unsubscribe(subscriberId: String)
    fun registerCommandHandler(handler: CommandHandler)
    suspend fun publish(event: RuntimeEvent)
    suspend fun send(command: RuntimeCommand): Any?
    fun publishedEvents(): List<RuntimeEvent>
    fun shutdown() = Unit
}

/**
 * In-memory event bus per EMS MVP scope. Contains no business logic.
 */
class InMemoryEventBus(
    private val logger: NovaLogger,
) : EventBus {

    private val subscribers = ConcurrentHashMap<String, EventSubscriber>()
    private val commandHandlers = ConcurrentHashMap<String, CommandHandler>()
    private val archive = CopyOnWriteArrayList<RuntimeEvent>()
    private val isShutdown = AtomicBoolean(false)

    override fun subscribe(subscriber: EventSubscriber) {
        subscribers[subscriber.subscriberId] = subscriber
        logger.debug(
            module = subscriber.subscriberId,
            message = "Subscribed to events: ${subscriber.eventTypes}",
        )
    }

    override fun unsubscribe(subscriberId: String) {
        subscribers.remove(subscriberId)
    }

    override fun registerCommandHandler(handler: CommandHandler) {
        check(commandHandlers.putIfAbsent(handler.commandType, handler) == null) {
            "Command handler already registered for ${handler.commandType}"
        }
    }

    override suspend fun publish(event: RuntimeEvent) {
        checkNotShutdown()
        archive.add(event)
        logger.info(
            module = event.sourceModule.name,
            message = "Published event ${event.eventType}",
            traceId = event.traceId,
        )
        dispatchEvent(event)
    }

    override suspend fun send(command: RuntimeCommand): Any? {
        checkNotShutdown()
        val handler = commandHandlers[command.commandType]
            ?: throw NovaException(NovaErrors.internal("No handler for command ${command.commandType}"))
        logger.info(
            module = command.sourceModule.name,
            message = "Dispatching command ${command.commandType}",
            traceId = command.traceId,
        )
        return handler.handle(command)
    }

    override fun publishedEvents(): List<RuntimeEvent> = archive.toList()

    override fun shutdown() {
        isShutdown.set(true)
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun dispatchEvent(event: RuntimeEvent) {
        val matching = subscribers.values
            .filter { subscriber -> subscriber.eventTypes.isEmpty() || event.eventType in subscriber.eventTypes }
            .sortedBy { subscriberPriority(event.priority) }

        for (subscriber in matching) {
            try {
                subscriber.onEvent(event)
            } catch (exception: Exception) {
                val error: NovaError = NovaErrors.eventHandlerFailed(event.eventType, subscriber.subscriberId)
                logger.error(
                    module = subscriber.subscriberId,
                    message = error.userVisibleMessage,
                    traceId = event.traceId,
                    throwable = exception,
                )
            }
        }
    }

    private fun checkNotShutdown() {
        check(!isShutdown.get()) { "Event bus is shut down" }
    }

    private fun subscriberPriority(priority: EventPriority): Int = when (priority) {
        EventPriority.CRITICAL -> 0
        EventPriority.HIGH -> 1
        EventPriority.NORMAL -> 2
        EventPriority.LOW -> 3
        EventPriority.IDLE -> 4
    }
}

/**
 * Priority queue helper for async event scheduling (reserved for future worker integration).
 */
class EventPriorityQueue {
    private val queue = PriorityBlockingQueue<RuntimeEvent>(11) { a, b ->
        a.priority.ordinal.compareTo(b.priority.ordinal)
    }

    fun offer(event: RuntimeEvent) {
        queue.offer(event)
    }

    fun poll(): RuntimeEvent? = queue.poll()

    fun size(): Int = queue.size
}
