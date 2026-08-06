package com.nova.runtime.events

import com.nova.runtime.models.EventPriority
import com.nova.runtime.models.RuntimeModule
import java.time.Instant
import java.util.UUID

/**
 * Standard event envelope per EMS §6 and IAS §4.
 * Events are immutable after publication.
 */
data class RuntimeEvent(
    val eventId: UUID = UUID.randomUUID(),
    val traceId: UUID,
    val correlationId: UUID? = null,
    val timestamp: Instant = Instant.now(),
    val sourceModule: RuntimeModule,
    val destination: RuntimeModule? = null,
    val priority: EventPriority = EventPriority.NORMAL,
    val eventType: String,
    val version: Int = 1,
    val payload: Any? = null,
) {
    init {
        require(eventType.isNotBlank()) { "eventType must not be blank" }
        require(version > 0) { "version must be positive" }
    }
}

/**
 * Command envelope for exactly-once handler dispatch per EMS §4.
 */
data class RuntimeCommand(
    val commandId: UUID = UUID.randomUUID(),
    val traceId: UUID,
    val correlationId: UUID? = null,
    val timestamp: Instant = Instant.now(),
    val sourceModule: RuntimeModule,
    val commandType: String,
    val version: Int = 1,
    val payload: Any? = null,
) {
    init {
        require(commandType.isNotBlank()) { "commandType must not be blank" }
    }
}

interface EventSubscriber {
    val subscriberId: String
    val eventTypes: Set<String>
    suspend fun onEvent(event: RuntimeEvent)
}

interface CommandHandler {
    val commandType: String
    suspend fun handle(command: RuntimeCommand): Any?
}
