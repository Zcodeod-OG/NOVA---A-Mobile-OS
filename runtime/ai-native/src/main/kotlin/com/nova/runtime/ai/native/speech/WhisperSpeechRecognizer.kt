package com.nova.runtime.ai.native.speech

import com.nova.runtime.ai.native.onnx.WhisperOnnxAsrEngine
import com.nova.runtime.conversation.speech.SpeechRecognizer
import com.nova.runtime.conversation.speech.StubSpeechRecognizer

/**
 * On-device ASR backed by ONNX Whisper with graceful fallback to [StubSpeechRecognizer].
 * All transcription runs locally — no cloud speech APIs.
 */
class WhisperSpeechRecognizer(
    private val asrEngine: WhisperOnnxAsrEngine,
    private val fallback: SpeechRecognizer = StubSpeechRecognizer(),
) : SpeechRecognizer {
    override suspend fun startListening(sessionId: String) = Unit

    override suspend fun stopListening(sessionId: String) = Unit

    override suspend fun transcribe(sessionId: String, audioPayload: ByteArray): String? {
        if (audioPayload.isEmpty()) return null
        return asrEngine.transcribe(audioPayload) ?: fallback.transcribe(sessionId, audioPayload)
    }
}
