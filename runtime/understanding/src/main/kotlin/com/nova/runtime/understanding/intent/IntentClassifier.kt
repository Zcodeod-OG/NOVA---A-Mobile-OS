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

        if (isNegatedCommand(payload)) {
            return DetectedIntent(
                goal = NEGATED_COMMAND_INTENT,
                intentType = NEGATED_COMMAND_INTENT,
                confidence = PLACEHOLDER_CONFIDENCE,
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
        const val NEGATED_COMMAND_INTENT = "negated_command"

        private const val PLACEHOLDER_CONFIDENCE = 0.85
        private const val FALLBACK_CONFIDENCE = 0.55

        /** Negation phrases that suppress command triggers ("do not open youtube"). */
        private val NEGATION_REGEX = Regex(
            """\b(?:do\s+not|don'?t|never|stop|do\s+nothing|cancel that)\b""",
        )

        /** Action keywords that would otherwise trigger a command intent. */
        private val NEGATABLE_ACTION_KEYWORDS = listOf(
            "open", "launch", "start", "search", "find", "play", "send", "set",
            "create", "call", "text", "message", "remind", "schedule", "share", "look",
        )

        /** True when a negation phrase precedes an action keyword — no trigger. */
        fun isNegatedCommand(payload: String): Boolean {
            val negation = NEGATION_REGEX.find(payload) ?: return false
            val remainder = payload.substring(negation.range.last + 1)
            return NEGATABLE_ACTION_KEYWORDS.any { keyword ->
                Regex("""\b${Regex.escape(keyword)}\b""").containsMatchIn(remainder)
            }
        }

        /**
         * Document / content-share cues for WhatsApp chaining.
         * Covers explicit nouns ("pdf", "invoice", "menu", "prospectus"), date-scoped
         * content ("send today's mess menu …"), and "send <multi-word file phrase> to <name>"
         * even when the channel word ("whatsapp") is omitted.
         */
        fun looksLikeDocumentShare(payload: String): Boolean {
            if (hasExplicitChatMessageCue(payload)) return false
            if (DOCUMENT_SHARE_NOUNS.any { noun ->
                    Regex("""\b${Regex.escape(noun)}\b""").containsMatchIn(payload)
                }
            ) {
                return true
            }
            val hasDate = DATE_SCOPED_SHARE_REGEX.containsMatchIn(payload)
            if (hasDate) return true
            return looksLikeFilePhraseSend(payload)
        }

        /**
         * "send bookly prospectus report to atharv sharma" → file send.
         * "send hello to atharv" / "send good morning to atharv" → text message.
         */
        fun looksLikeFilePhraseSend(payload: String): Boolean {
            if (hasExplicitChatMessageCue(payload)) return false
            val match = SEND_OBJECT_TO_RECIPIENT_REGEX.find(payload) ?: return false
            val obj = match.groupValues[1].trim()
            if (obj.isBlank()) return false
            if (isShortChattyMessage(obj)) return false
            val words = obj.split(Regex("\\s+")).filter { it.isNotBlank() }
            // Multi-word object before "to <name>" is treated as a file/document phrase.
            return words.size >= 2
        }

        fun looksLikeSendToContact(payload: String): Boolean {
            if (!SEND_OBJECT_TO_RECIPIENT_REGEX.containsMatchIn(payload)) return false
            if (specifiesNonWhatsAppChannel(payload)) return false
            return true
        }

        fun looksLikeDocumentExtract(payload: String): Boolean {
            if ("whatsapp" in payload || "share" in payload) return false
            if (EXTRACT_CONTENT_REGEX.containsMatchIn(payload)) return true
            return Regex("""\bextract\b.+\b(?:from|in)\b""").containsMatchIn(payload) &&
                DOCUMENT_SHARE_NOUNS.any { noun ->
                    Regex("""\b${Regex.escape(noun)}\b""").containsMatchIn(payload)
                }
        }

        fun looksLikeDocumentQuestion(payload: String): Boolean {
            if (looksLikeDocumentExtract(payload)) return false
            // Never steal WhatsApp / share send paths.
            if ("whatsapp" in payload || "share" in payload) return false
            if (SEND_TO_REGEX.containsMatchIn(payload) && "whatsapp" !in payload) {
                // "send X to Y" without question words is not an in-app document answer.
                val hasQuestionWord = DOCUMENT_QUESTION_WORDS.any { it in payload }
                if (!hasQuestionWord) return false
            }
            val questionCue = DOCUMENT_QUESTION_CUES.any { it in payload } ||
                DOCUMENT_QUESTION_REGEX.containsMatchIn(payload) ||
                FROM_TELL_REGEX.containsMatchIn(payload)
            if (!questionCue) return false
            val contentCue = DOCUMENT_SHARE_NOUNS.any { noun ->
                Regex("""\b${Regex.escape(noun)}\b""").containsMatchIn(payload)
            } || MEAL_WORDS.any { it in payload } ||
                "mess" in payload ||
                TIMETABLE_WORDS.any { it in payload }
            return contentCue
        }

        private fun hasExplicitChatMessageCue(payload: String): Boolean =
            listOf("saying", "that says", "message saying").any { it in payload }

        private fun specifiesNonWhatsAppChannel(payload: String): Boolean =
            Regex("""\b(?:sms|email|e-mail|mail|imessage)\b""").containsMatchIn(payload)

        private fun isShortChattyMessage(obj: String): Boolean {
            val normalized = obj.trim().lowercase()
            if (normalized in CHATTY_MESSAGES) return true
            val words = normalized.split(Regex("\\s+")).filter { it.isNotBlank() }
            return words.size <= 3 && words.all { it in CHATTY_WORDS }
        }

        private val DOCUMENT_SHARE_NOUNS = listOf(
            "document", "documents", "doc", "docs", "pdf", "file", "report", "invoice", "menu",
            "prospectus", "bookly", "timetable", "sheet", "slides", "presentation", "mess",
            "spreadsheet", "xlsx", "docx", "ppt", "pptx",
        )

        private val CHATTY_WORDS = setOf(
            "hello", "hi", "hey", "hola", "thanks", "thank", "you", "ok", "okay", "bye",
            "good", "morning", "afternoon", "evening", "night", "sup", "yo", "please",
            "love", "miss", "yes", "no", "sure", "later", "soon",
        )

        private val CHATTY_MESSAGES = setOf(
            "hello", "hi", "hey", "thanks", "thank you", "ok", "okay", "bye",
            "good morning", "good afternoon", "good evening", "good night",
            "miss you", "love you", "see you", "what's up", "whats up",
        )

        private val SEND_OBJECT_TO_RECIPIENT_REGEX = Regex(
            """\b(?:send|share)\s+(?:the\s+)?(.+?)\s+to\s+[a-z]""",
            RegexOption.IGNORE_CASE,
        )

        private val DOCUMENT_QUESTION_CUES = listOf(
            "what is", "what's", "whats", "show me", "tell me", "what's on", "whats on",
            "show ", "tell ",
        )

        private val DOCUMENT_QUESTION_WORDS = listOf(
            "tell", "what", "show", "from",
        )

        private val DOCUMENT_QUESTION_REGEX = Regex(
            """\b(?:today'?s?|tonight|tomorrow'?s?)\s+(?:dinner|lunch|breakfast|snack|menu|lec|lecture|slots?|classes)\b""",
        )

        /** "from timetable tell me…" / "from mess menu show breakfast" */
        private val FROM_TELL_REGEX = Regex(
            """\bfrom\s+\S+.*\b(?:tell|what|show)\b|\b(?:tell|what|show)\b.*\bfrom\s+\S+""",
            RegexOption.IGNORE_CASE,
        )

        private val SEND_TO_REGEX = Regex("""\bsend\b.+\bto\b""", RegexOption.IGNORE_CASE)

        private val MEAL_WORDS = listOf("breakfast", "lunch", "dinner", "snacks", "snack")

        private val TIMETABLE_WORDS = listOf(
            "timetable", "time table", "schedule", "lec", "lecture", "lectures",
            "slot", "slots", "class", "classes",
        )

        private val DATE_SCOPED_SHARE_REGEX = Regex(
            """\b(?:today'?s?|tonight|tomorrow'?s?|yesterday'?s?|this\s+month(?:'?s|s)?|month'?s)\b""",
        )

        private val EXTRACT_CONTENT_REGEX = Regex(
            """\b(?:extract\s+(?:content|text)|show\s+me\s+the\s+text\s+in|display\s+contents?\s+of|read\s+out\s+(?:the\s+)?file)\b""",
            RegexOption.IGNORE_CASE,
        )

        private val INTENT_PATTERNS = listOf(
            // Document/file → WhatsApp (channel optional; WhatsApp is the default when a recipient is present).
            // Question forms ("tell me…", "from X show…") stay in-app via document_question.
            IntentPattern("send_document_whatsapp") { payload ->
                !looksLikeDocumentQuestion(payload) &&
                    listOf("send", "share", "find", "search").any { it in payload } &&
                    looksLikeDocumentShare(payload) &&
                    !specifiesNonWhatsAppChannel(payload) &&
                    (
                        "whatsapp" in payload ||
                            looksLikeSendToContact(payload)
                        )
            },
            // Text to contact; WhatsApp default when channel unspecified.
            IntentPattern("send_whatsapp_message") { payload ->
                !looksLikeDocumentQuestion(payload) &&
                    !looksLikeDocumentShare(payload) &&
                    !specifiesNonWhatsAppChannel(payload) &&
                    (
                        (
                            "whatsapp" in payload &&
                                listOf("message", "send", "text", "saying", "share")
                                    .any { it in payload }
                            ) ||
                            looksLikeSendToContact(payload)
                        )
            },
            // "extract content from mess menu" / "show me the text in timetable.pdf"
            IntentPattern("extract_document_content") { payload ->
                looksLikeDocumentExtract(payload)
            },
            // "what is todays dinner menu" / "from timetable tell me…" → in-app answer
            IntentPattern("document_question") { payload ->
                looksLikeDocumentQuestion(payload)
            },
            IntentPattern("semantic_search") { payload ->
                "semantic" in payload && listOf("search", "find", "query").any { it in payload }
            },
            IntentPattern("search_photos") { payload ->
                listOf("photo", "photos", "picture", "pictures", "gallery").any { it in payload } &&
                    listOf("search", "find", "show", "look").any { it in payload }
            },
            IntentPattern("search_documents") { payload ->
                DOCUMENT_SHARE_NOUNS.any { it in payload } &&
                    listOf("search", "find", "look").any { it in payload } &&
                    "share" !in payload &&
                    "whatsapp" !in payload
            },
            IntentPattern("set_alarm") { payload ->
                ("alarm" in payload && listOf("set", "create", "for", "at").any { it in payload }) ||
                    WAKE_ME_REGEX.containsMatchIn(payload)
            },
            IntentPattern("read_calendar") { payload ->
                READ_CALENDAR_REGEX.containsMatchIn(payload) ||
                    (
                        ("calendar" in payload || "schedule" in payload) &&
                            listOf("what's on", "whats on", "show me", "read", "look at", "check").any { it in payload } &&
                            listOf("tomorrow", "today", "this week", "next week", "monday", "tuesday", "wednesday",
                                "thursday", "friday", "saturday", "sunday",
                            ).any { it in payload }
                        )
            },
            IntentPattern("schedule_from_message") { payload ->
                listOf(
                    "add this to my calendar",
                    "put this on my calendar",
                    "schedule the meeting from",
                    "schedule from that message",
                    "schedule from that email",
                    "add to calendar from",
                    "calendar from that email",
                    "calendar from that message",
                ).any { phrase -> phrase in payload }
            },
            IntentPattern("review_important") { payload ->
                listOf(
                    "what's important today",
                    "whats important today",
                    "what is important today",
                    "summarize urgent",
                    "urgent messages",
                    "important messages today",
                    "what's urgent",
                    "whats urgent",
                ).any { phrase -> phrase in payload }
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
            // "open youtube and search shape of you" / "search shape of you on youtube" /
            // "play shape of you on youtube"
            IntentPattern("app_action_search") { payload ->
                OPEN_AND_SEARCH_REGEX.containsMatchIn(payload) ||
                    SEARCH_ON_APP_REGEX.containsMatchIn(payload)
            },
            // "open youtube", "launch chrome" — real app launch, any app name.
            IntentPattern("open_application") { payload ->
                OPEN_APP_REGEX.containsMatchIn(payload)
            },
        )

        private val OPEN_AND_SEARCH_REGEX = Regex(
            """\b(?:open|launch|start)\s+\S+.*\b(?:search|play|find|look\s+up)\b""",
        )

        private val SEARCH_ON_APP_REGEX = Regex(
            """\b(?:search|play|find|look\s+up)\s+(?:for\s+)?.+\s+(?:on|in|using)\s+""" +
                """(?:youtube|yt|spotify|maps|google\s+maps|chrome|browser|google|the\s+web|internet)\b""",
        )

        private val OPEN_APP_REGEX = Regex("""^(?:please\s+)?(?:open|launch)\s+\S+""")

        /** "wake me at 6:30", "wake me up at 7am" — treat as set_alarm. */
        private val WAKE_ME_REGEX = Regex("""\bwake\s+me(?:\s+up)?\b""")

        private val READ_CALENDAR_REGEX = Regex(
            """\bwhat(?:'?s|s)?\s+on\s+my\s+calendar\b""",
            RegexOption.IGNORE_CASE,
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
