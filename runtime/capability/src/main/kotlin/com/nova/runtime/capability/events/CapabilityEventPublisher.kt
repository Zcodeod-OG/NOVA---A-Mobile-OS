package com.nova.runtime.capability.events

import com.nova.runtime.events.EventBus
import com.nova.runtime.events.RuntimeEvent
import com.nova.runtime.events.capability.CapabilityEvents
import com.nova.runtime.models.EventPriority
import com.nova.runtime.models.RuntimeModule
import java.util.UUID

class CapabilityEventPublisher(
    private val eventBus: EventBus,
) {
    suspend fun publishResolved(
        traceId: UUID,
        capabilityType: String,
        providerId: String,
        operation: String,
    ) {
        publish(
            eventType = CapabilityEvents.RESOLVED,
            traceId = traceId,
            payload = mapOf(
                "capabilityType" to capabilityType,
                "providerId" to providerId,
                "operation" to operation,
            ),
        )
    }

    suspend fun publishSelected(
        traceId: UUID,
        capabilityType: String,
        providerId: String,
        providerVersion: String,
    ) {
        publish(
            eventType = CapabilityEvents.SELECTED,
            traceId = traceId,
            payload = mapOf(
                "capabilityType" to capabilityType,
                "providerId" to providerId,
                "providerVersion" to providerVersion,
            ),
        )
    }

    suspend fun publishExecuted(
        traceId: UUID,
        capabilityType: String,
        providerId: String,
        operation: String,
        latencyMs: Long,
        transactionId: UUID? = null,
    ) {
        publish(
            eventType = CapabilityEvents.EXECUTED,
            traceId = traceId,
            payload = buildMap {
                put("capabilityType", capabilityType)
                put("providerId", providerId)
                put("operation", operation)
                put("latencyMs", latencyMs.toString())
                transactionId?.let { put("transactionId", it.toString()) }
            },
        )
    }

    suspend fun publishFailed(
        traceId: UUID,
        capabilityType: String,
        errorCode: String,
        providerId: String? = null,
    ) {
        publish(
            eventType = CapabilityEvents.FAILED,
            traceId = traceId,
            payload = buildMap {
                put("capabilityType", capabilityType)
                put("errorCode", errorCode)
                providerId?.let { put("providerId", it) }
            },
            priority = EventPriority.HIGH,
        )
    }

    suspend fun publishUnavailable(
        traceId: UUID,
        capabilityType: String,
        operation: String,
    ) {
        publish(
            eventType = CapabilityEvents.UNAVAILABLE,
            traceId = traceId,
            payload = mapOf(
                "capabilityType" to capabilityType,
                "operation" to operation,
            ),
            priority = EventPriority.HIGH,
        )
    }

    private suspend fun publish(
        eventType: String,
        traceId: UUID,
        payload: Map<String, String>,
        priority: EventPriority = EventPriority.NORMAL,
    ) {
        eventBus.publish(
            RuntimeEvent(
                traceId = traceId,
                sourceModule = RuntimeModule.CAPABILITY,
                eventType = eventType,
                priority = priority,
                payload = payload,
            ),
        )
    }
}
