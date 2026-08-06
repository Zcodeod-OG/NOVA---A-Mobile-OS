package com.nova.runtime.conversation.events

import com.nova.runtime.events.EventBus
import com.nova.runtime.events.RuntimeEvent
import com.nova.runtime.events.conversation.ConversationEvents
import com.nova.runtime.models.EventPriority
import com.nova.runtime.models.Observation
import com.nova.runtime.models.RuntimeModule
import java.util.UUID

class ConversationEventPublisher(
    private val eventBus: EventBus,
) {
    suspend fun publishStarted(sessionId: UUID, traceId: UUID) {
        publish(
            eventType = ConversationEvents.STARTED,
            traceId = traceId,
            payload = mapOf("sessionId" to sessionId.toString()),
        )
    }

    suspend fun publishObservationReceived(observation: Observation) {
        publish(
            eventType = ConversationEvents.OBSERVATION_RECEIVED,
            traceId = observation.traceId,
            correlationId = observation.id,
            payload = observation,
            priority = EventPriority.HIGH,
        )
    }

    suspend fun publishInterrupted(sessionId: UUID, traceId: UUID) {
        publish(
            eventType = ConversationEvents.INTERRUPTED,
            traceId = traceId,
            payload = mapOf("sessionId" to sessionId.toString()),
            priority = EventPriority.HIGH,
        )
    }

    suspend fun publishCompleted(sessionId: UUID, traceId: UUID, turnCount: Int) {
        publish(
            eventType = ConversationEvents.COMPLETED,
            traceId = traceId,
            payload = mapOf(
                "sessionId" to sessionId.toString(),
                "turnCount" to turnCount.toString(),
            ),
        )
    }

    private suspend fun publish(
        eventType: String,
        traceId: UUID,
        correlationId: UUID? = null,
        payload: Any? = null,
        priority: EventPriority = EventPriority.NORMAL,
    ) {
        eventBus.publish(
            RuntimeEvent(
                traceId = traceId,
                correlationId = correlationId,
                sourceModule = RuntimeModule.CONVERSATION,
                eventType = eventType,
                priority = priority,
                payload = payload,
            ),
        )
    }
}
