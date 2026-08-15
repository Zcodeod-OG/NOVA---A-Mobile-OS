package com.nova.runtime.conversation.input

import com.nova.runtime.models.Modality
import java.util.UUID

/** Unified input envelope supporting voice and text modalities. */
sealed class ConversationInput {
    abstract val sessionId: UUID

    data class Text(
        override val sessionId: UUID,
        val text: String,
    ) : ConversationInput()

    data class Voice(
        override val sessionId: UUID,
        val audioPayload: ByteArray,
    ) : ConversationInput() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Voice) return false
            return sessionId == other.sessionId && audioPayload.contentEquals(other.audioPayload)
        }

        override fun hashCode(): Int = 31 * sessionId.hashCode() + audioPayload.contentHashCode()
    }

    val modality: Modality
        get() = when (this) {
            is Text -> Modality.TEXT
            is Voice -> Modality.VOICE
        }
}
