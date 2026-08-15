package com.nova.runtime.conversation.speech

/** No-op stub ASR — no microphone integration, deterministic transcription. */
class StubSpeechRecognizer : SpeechRecognizer {
    override suspend fun startListening(sessionId: String) = Unit

    override suspend fun stopListening(sessionId: String) = Unit

    override suspend fun transcribe(sessionId: String, audioPayload: ByteArray): String? {
        if (audioPayload.isEmpty()) return null
        return "voice input (${audioPayload.size} bytes)"
    }
}
