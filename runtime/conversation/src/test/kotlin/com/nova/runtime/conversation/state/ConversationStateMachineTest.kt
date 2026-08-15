package com.nova.runtime.conversation.state

import com.nova.runtime.error.NovaException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ConversationStateMachineTest {

    @Test
    fun idle_to_processing_on_text_input() {
        val machine = ConversationStateMachine()
        assertEquals(ConversationState.IDLE, machine.current())

        val next = machine.transition(ConversationStateEvent.RECEIVE_INPUT)
        assertEquals(ConversationState.PROCESSING, next)
    }

    @Test
    fun voice_flow_idle_listening_processing_speaking_idle() {
        val machine = ConversationStateMachine()

        machine.transition(ConversationStateEvent.START_LISTENING)
        assertEquals(ConversationState.LISTENING, machine.current())

        machine.transition(ConversationStateEvent.RECEIVE_INPUT)
        assertEquals(ConversationState.PROCESSING, machine.current())

        machine.transition(ConversationStateEvent.RESPONSE_READY)
        assertEquals(ConversationState.SPEAKING, machine.current())

        machine.transition(ConversationStateEvent.RESPONSE_COMPLETE)
        assertEquals(ConversationState.IDLE, machine.current())
    }

    @Test
    fun speaking_can_be_interrupted() {
        val machine = ConversationStateMachine(ConversationState.SPEAKING)

        machine.transition(ConversationStateEvent.INTERRUPT)
        assertEquals(ConversationState.INTERRUPTED, machine.current())

        machine.transition(ConversationStateEvent.ACKNOWLEDGE_INTERRUPT)
        assertEquals(ConversationState.LISTENING, machine.current())
    }

    @Test
    fun invalid_transition_throws() {
        val machine = ConversationStateMachine(ConversationState.IDLE)
        assertFailsWith<NovaException> {
            machine.transition(ConversationStateEvent.RESPONSE_READY)
        }
    }
}
