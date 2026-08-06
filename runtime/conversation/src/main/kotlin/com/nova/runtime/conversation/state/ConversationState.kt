package com.nova.runtime.conversation.state

/** Conversation lifecycle states per PRD/TDD requirements. */
enum class ConversationState {
    IDLE,
    LISTENING,
    PROCESSING,
    SPEAKING,
    INTERRUPTED,
}
