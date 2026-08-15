package com.nova.runtime.conversation.speech

/** Platform-agnostic text-to-speech contract. No Android APIs. */
interface TextToSpeech {
    suspend fun speak(sessionId: String, text: String)

    suspend fun stop(sessionId: String)
}
