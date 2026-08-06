package com.nova.runtime.understanding.intent

import com.nova.runtime.understanding.normalization.NormalizedObservation

data class DetectedIntent(
    val goal: String,
    val intentType: String,
    val confidence: Double,
)

interface IntentClassifier {
    suspend fun classify(normalized: NormalizedObservation): DetectedIntent
}

/**
 * Placeholder intent classification — deterministic keyword routing only.
 */
class PlaceholderIntentClassifier : IntentClassifier {
    override suspend fun classify(normalized: NormalizedObservation): DetectedIntent {
        val payload = normalized.normalizedPayload.lowercase()
        if (payload.isBlank()) {
            return DetectedIntent(
                goal = "unknown",
                intentType = "unknown",
                confidence = 0.0,
            )
        }

        val match = INTENT_KEYWORDS.firstOrNull { (keywords, _) ->
            keywords.any { keyword -> keyword in payload }
        }

        return if (match != null) {
            DetectedIntent(
                goal = match.second,
                intentType = match.second,
                confidence = PLACEHOLDER_CONFIDENCE,
            )
        } else {
            DetectedIntent(
                goal = normalized.normalizedPayload,
                intentType = "general_query",
                confidence = FALLBACK_CONFIDENCE,
            )
        }
    }

    companion object {
        private const val PLACEHOLDER_CONFIDENCE = 0.8
        private const val FALLBACK_CONFIDENCE = 0.55

        private val INTENT_KEYWORDS = listOf(
            listOf("remind", "reminder", "alarm") to "set_reminder",
            listOf("call", "text", "message", "whatsapp", "sms") to "send_message",
            listOf("search", "find", "look for", "where is") to "search",
            listOf("schedule", "calendar", "meeting", "event") to "manage_calendar",
            listOf("open", "launch", "start") to "open_application",
            listOf("play", "pause", "music", "song") to "control_media",
        )
    }
}
