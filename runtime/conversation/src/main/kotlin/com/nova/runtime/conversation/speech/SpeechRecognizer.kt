package com.nova.runtime.conversation.speech

/** Platform-agnostic speech recognition contract. No Android APIs. */
interface SpeechRecognizer {
    suspend fun startListening(sessionId: String)

    suspend fun stopListening(sessionId: String)

    /** Transcribe raw audio bytes into text. Stub implementations return deterministic text. */
    suspend fun transcribe(sessionId: String, audioPayload: ByteArray): String?
}
