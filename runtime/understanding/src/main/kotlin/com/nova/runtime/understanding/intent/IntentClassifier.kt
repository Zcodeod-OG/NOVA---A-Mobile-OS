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
 * Deterministic intent classification for the eight NOVA user features plus legacy fallbacks.
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

        val match = INTENT_PATTERNS.firstOrNull { pattern -> pattern.matches(payload) }
            ?: LEGACY_KEYWORDS.firstOrNull { (keywords, _) ->
                keywords.any { keyword -> keyword in payload }
            }?.let { (_, intentType) ->
                IntentPattern(
                    intentType = intentType,
                    goal = intentType,
                    matcher = { true },
                )
            }

        return if (match != null) {
            DetectedIntent(
                goal = match.goal,
                intentType = match.intentType,
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

    private data class IntentPattern(
        val intentType: String,
        val goal: String = intentType,
        val matcher: (String) -> Boolean,
    ) {
        fun matches(payload: String): Boolean = matcher(payload)
    }

    companion object {
        private const val PLACEHOLDER_CONFIDENCE = 0.85
        private const val FALLBACK_CONFIDENCE = 0.55

        private val INTENT_PATTERNS = listOf(
            IntentPattern("send_document_whatsapp") { payload ->
                "whatsapp" in payload &&
                    (
                        (
                            listOf("send", "share").any { it in payload } &&
                                listOf("document", "documents", "doc", "docs", "pdf", "file", "report", "invoice")
                                    .any { it in payload }
                        ) ||
                            (
                                listOf("find", "search").any { it in payload } &&
                                    listOf("document", "documents", "doc", "docs", "pdf", "file", "report", "invoice")
                                        .any { it in payload }
                            )
                    )
            },
            IntentPattern("send_whatsapp_message") { payload ->
                "whatsapp" in payload &&
                    listOf("message", "send", "text", "saying", "share").any { it in payload } &&
                    listOf("document", "documents", "doc", "docs", "pdf", "file", "report", "invoice")
                        .none { it in payload }
            },
            IntentPattern("semantic_search") { payload ->
                "semantic" in payload && listOf("search", "find", "query").any { it in payload }
            },
            IntentPattern("search_photos") { payload ->
                listOf("photo", "photos", "picture", "pictures", "gallery").any { it in payload } &&
                    listOf("search", "find", "show", "look").any { it in payload }
            },
            IntentPattern("search_documents") { payload ->
                listOf("document", "documents", "doc", "docs", "pdf").any { it in payload } &&
                    listOf("search", "find", "look").any { it in payload } &&
                    "share" !in payload
            },
            IntentPattern("set_alarm") { payload ->
                "alarm" in payload && listOf("set", "create", "for", "at").any { it in payload }
            },
            IntentPattern("create_calendar_event") { payload ->
                ("calendar" in payload || "event" in payload || "meeting" in payload) &&
                    listOf("create", "schedule", "add", "set", "tomorrow").any { it in payload }
            },
            IntentPattern("lookup_contact") { payload ->
                "contact" in payload && listOf("lookup", "look up", "find", "search", "get").any { it in payload }
            },
            IntentPattern("share_file") { payload ->
                "share" in payload && listOf("file", "document", "pdf", "report").any { it in payload }
            },
        )

        private val LEGACY_KEYWORDS = listOf(
            listOf("remind", "reminder") to "set_reminder",
            listOf("call", "text", "message", "sms") to "send_message",
            listOf("search", "find", "look for", "where is") to "search",
            listOf("schedule", "calendar", "meeting", "event") to "manage_calendar",
            listOf("open", "launch", "start") to "open_application",
            listOf("play", "pause", "music", "song") to "control_media",
        )
    }
}
