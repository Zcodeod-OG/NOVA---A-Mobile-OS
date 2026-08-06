package com.nova.runtime.conversation.state

import com.nova.runtime.error.NovaError
import com.nova.runtime.error.NovaErrors
import com.nova.runtime.error.NovaException

/**
 * Deterministic state machine governing conversation turn lifecycle.
 * Invalid transitions throw [NovaException] with a structured error.
 */
class ConversationStateMachine(
    initialState: ConversationState = ConversationState.IDLE,
) {
    private var currentState: ConversationState = initialState

    fun current(): ConversationState = currentState

    fun transition(event: ConversationStateEvent): ConversationState {
        val next = resolveTransition(currentState, event)
        currentState = next
        return next
    }

    fun canAcceptInput(): Boolean =
        currentState in setOf(
            ConversationState.IDLE,
            ConversationState.LISTENING,
            ConversationState.SPEAKING,
            ConversationState.PROCESSING,
        )

    fun isActive(): Boolean = currentState != ConversationState.IDLE || currentState == ConversationState.INTERRUPTED

    private fun resolveTransition(
        from: ConversationState,
        event: ConversationStateEvent,
    ): ConversationState = when (from) {
        ConversationState.IDLE -> when (event) {
            ConversationStateEvent.START_LISTENING -> ConversationState.LISTENING
            ConversationStateEvent.RECEIVE_INPUT -> ConversationState.PROCESSING
            ConversationStateEvent.END_SESSION -> ConversationState.IDLE
            else -> invalidTransition(from, event)
        }

        ConversationState.LISTENING -> when (event) {
            ConversationStateEvent.RECEIVE_INPUT -> ConversationState.PROCESSING
            ConversationStateEvent.CANCEL_LISTENING -> ConversationState.IDLE
            ConversationStateEvent.END_SESSION -> ConversationState.IDLE
            else -> invalidTransition(from, event)
        }

        ConversationState.PROCESSING -> when (event) {
            ConversationStateEvent.RESPONSE_READY -> ConversationState.SPEAKING
            ConversationStateEvent.INTERRUPT -> ConversationState.INTERRUPTED
            ConversationStateEvent.END_SESSION -> ConversationState.IDLE
            else -> invalidTransition(from, event)
        }

        ConversationState.SPEAKING -> when (event) {
            ConversationStateEvent.RESPONSE_COMPLETE -> ConversationState.IDLE
            ConversationStateEvent.INTERRUPT -> ConversationState.INTERRUPTED
            ConversationStateEvent.END_SESSION -> ConversationState.IDLE
            else -> invalidTransition(from, event)
        }

        ConversationState.INTERRUPTED -> when (event) {
            ConversationStateEvent.ACKNOWLEDGE_INTERRUPT -> ConversationState.LISTENING
            ConversationStateEvent.RECEIVE_INPUT -> ConversationState.PROCESSING
            ConversationStateEvent.END_SESSION -> ConversationState.IDLE
            else -> invalidTransition(from, event)
        }
    }

    private fun invalidTransition(from: ConversationState, event: ConversationStateEvent): Nothing {
        val error: NovaError = NovaErrors.internal(
            "Invalid conversation transition from $from on $event",
            diagnostics = "from=$from, event=$event",
        )
        throw NovaException(error)
    }
}

enum class ConversationStateEvent {
    START_LISTENING,
    CANCEL_LISTENING,
    RECEIVE_INPUT,
    RESPONSE_READY,
    RESPONSE_COMPLETE,
    INTERRUPT,
    ACKNOWLEDGE_INTERRUPT,
    END_SESSION,
}
