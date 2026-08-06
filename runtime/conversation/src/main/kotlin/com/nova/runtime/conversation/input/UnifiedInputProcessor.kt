package com.nova.runtime.conversation.input

import com.nova.runtime.conversation.speech.SpeechRecognizer
import com.nova.runtime.models.Modality
import java.util.UUID

class UnifiedInputProcessor(
    private val speechRecognizer: SpeechRecognizer,
) {
    suspend fun normalize(input: ConversationInput): NormalizedInput? = when (input) {
        is ConversationInput.Text -> NormalizedInput(
            sessionId = input.sessionId,
            text = input.text.trim(),
            modality = Modality.TEXT,
        ).takeIf { it.text.isNotEmpty() }

        is ConversationInput.Voice -> {
            speechRecognizer.startListening(input.sessionId.toString())
            val text = speechRecognizer.transcribe(input.sessionId.toString(), input.audioPayload)
            speechRecognizer.stopListening(input.sessionId.toString())
            text?.trim()?.takeIf { it.isNotEmpty() }?.let { transcript ->
                NormalizedInput(
                    sessionId = input.sessionId,
                    text = transcript,
                    modality = Modality.VOICE,
                )
            }
        }
    }
}

data class NormalizedInput(
    val sessionId: UUID,
    val text: String,
    val modality: Modality,
)
