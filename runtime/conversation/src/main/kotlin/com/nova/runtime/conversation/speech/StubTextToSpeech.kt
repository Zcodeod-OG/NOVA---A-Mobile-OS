package com.nova.runtime.conversation.speech

/** No-op stub TTS — records speak/stop calls without audio output. */
class StubTextToSpeech : TextToSpeech {
    private val spoken = mutableMapOf<String, String>()

    override suspend fun speak(sessionId: String, text: String) {
        spoken[sessionId] = text
    }

    override suspend fun stop(sessionId: String) {
        spoken.remove(sessionId)
    }

    fun lastSpoken(sessionId: String): String? = spoken[sessionId]
}
