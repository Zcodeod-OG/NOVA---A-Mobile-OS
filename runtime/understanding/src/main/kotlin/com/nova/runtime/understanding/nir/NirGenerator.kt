package com.nova.runtime.understanding.nir

import com.nova.runtime.models.Nir
import com.nova.runtime.models.NovaCapabilityOperationResolver
import com.nova.runtime.models.NovaCapabilityOperations
import com.nova.runtime.understanding.entity.ExtractedEntity
import com.nova.runtime.understanding.intent.DetectedIntent
import com.nova.runtime.understanding.normalization.NormalizedObservation
import com.nova.runtime.understanding.routing.InferenceRouteDecision
import com.nova.runtime.understanding.time.NaturalLanguageTimeParser

interface NirGenerator {
    suspend fun generate(
        normalized: NormalizedObservation,
        intent: DetectedIntent,
        entities: List<ExtractedEntity>,
        constraints: Map<String, String>,
        routeDecision: InferenceRouteDecision,
    ): Nir
}

class DefaultNirGenerator : NirGenerator {
    override suspend fun generate(
        normalized: NormalizedObservation,
        intent: DetectedIntent,
        entities: List<ExtractedEntity>,
        constraints: Map<String, String>,
        routeDecision: InferenceRouteDecision,
    ): Nir {
        val context = buildMap {
            putAll(normalized.metadata)
            put("sessionId", normalized.observation.sessionId.toString())
            put("observationId", normalized.observation.id.toString())
            put("inferenceTier", routeDecision.tier.toString())
            put("routingReason", routeDecision.reason)
            put("rawPayload", normalized.normalizedPayload)
        }

        val confidence = listOf(
            intent.confidence,
            entities.maxOfOrNull { it.confidence } ?: FALLBACK_ENTITY_CONFIDENCE,
            routeDecision.confidence,
        ).average()

        val capabilityOperation = NovaCapabilityOperations.forIntent(intent.intentType)
        val resolved = capabilityOperation?.let { NovaCapabilityOperationResolver.resolve(it) }

        val enrichedConstraints = buildMap {
            putAll(constraints)
            extractChannel(normalized.normalizedPayload, intent.intentType)?.let { put("channel", it) }
            extractRecipient(normalized.normalizedPayload)?.let { put("recipient", it) }
            extractFileName(normalized.normalizedPayload)?.let { put("fileName", it) }
            // Document/file sends must never put the command (or file phrase) into message.
            if (intent.intentType != "send_document_whatsapp") {
                extractMessage(normalized.normalizedPayload, intent.intentType)?.let { put("message", it) }
            }
            enrichTimeConstraints(intent.intentType, normalized.normalizedPayload, this)
            enrichCompoundFlowConstraints(intent.intentType, normalized.normalizedPayload, this)
            enrichAppControlConstraints(intent.intentType, normalized.normalizedPayload, this)
            if (resolved != null) {
                put("capabilityOperation", resolved.qualifiedName)
                put("capabilityType", resolved.capabilityType)
                put("operation", resolved.operation)
            }
            // WhatsApp is the default channel for send-to-contact when unspecified.
            if (
                !containsKey("channel") &&
                intent.intentType in setOf(
                    "send_document_whatsapp",
                    "send_whatsapp_message",
                    "send_message",
                )
            ) {
                put("channel", "whatsapp")
            }
        }

        return Nir(
            version = NIR_VERSION,
            goal = intent.goal,
            entities = entities.map { it.value }.distinct(),
            constraints = enrichedConstraints,
            context = context,
            requiredCapabilities = requiredCapabilitiesFor(intent.intentType, resolved?.capabilityType),
            confidence = confidence.coerceIn(0.0, 1.0),
        )
    }

    private fun enrichTimeConstraints(
        intentType: String,
        payload: String,
        constraints: MutableMap<String, String>,
    ) {
        when (intentType) {
            "set_alarm", "set_reminder" -> {
                NaturalLanguageTimeParser.parseAlarmTriggerMillis(payload)?.let {
                    constraints["triggerAtMillis"] = it.toString()
                }
                constraints["alarmKind"] = if (intentType == "set_reminder") "reminder" else "clock"
                constraints["intentType"] = intentType
                when (intentType) {
                    "set_reminder" -> extractReminderLabel(payload)?.let { constraints["label"] = it }
                    "set_alarm" -> extractAlarmLabel(payload)?.let { constraints["label"] = it }
                }
            }
            "create_calendar_event", "manage_calendar" ->
                NaturalLanguageTimeParser.parseCalendarEvent(payload)?.let { event ->
                    constraints["title"] = event.title
                    constraints["startTime"] = event.startTime.toString()
                    constraints["endTime"] = event.endTime.toString()
                }
            "read_calendar" -> {
                parseCalendarReadWindow(payload)?.let { (start, end) ->
                    constraints["startTime"] = start.toString()
                    constraints["endTime"] = end.toString()
                }
                constraints["intentType"] = intentType
            }
            "schedule_from_message" ->
                constraints["intentType"] = intentType
        }
    }

    private fun parseCalendarReadWindow(payload: String): Pair<Long, Long>? {
        val zone = java.time.ZoneId.systemDefault()
        val lower = payload.lowercase()
        val day = when {
            "tomorrow" in lower -> java.time.LocalDate.now(zone).plusDays(1)
            "today" in lower || "tonight" in lower -> java.time.LocalDate.now(zone)
            else -> java.time.LocalDate.now(zone)
        }
        val start = day.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
        return start to end
    }

    private fun enrichCompoundFlowConstraints(
        intentType: String,
        payload: String,
        constraints: MutableMap<String, String>,
    ) {
        when (intentType) {
            "send_document_whatsapp" -> {
                constraints["compoundFlow"] = "search_document_whatsapp"
                extractDocumentQuery(payload)?.let { query ->
                    constraints["documentQuery"] = query
                    constraints["query"] = query
                }
            }
            "document_question", "search_documents" -> {
                extractDocumentQuestionQuery(payload)?.let { query ->
                    constraints["documentQuery"] = query
                    constraints["query"] = query
                }
                if (intentType == "document_question") {
                    enrichDocumentQuestionConstraints(payload, constraints)
                }
            }
            "extract_document_content" -> {
                extractDocumentExtractQuery(payload)?.let { query ->
                    constraints["documentQuery"] = query
                    constraints["query"] = query
                }
                enrichDocumentQuestionConstraints(payload, constraints)
                constraints["answerMode"] = "extract"
                constraints["maxDisplayChars"] = DEFAULT_EXTRACT_DISPLAY_CHARS.toString()
                constraints["displayMode"] = resolveExtractDisplayMode(payload, constraints)
                constraints["intentType"] = intentType
            }
        }
    }

    private fun resolveExtractDisplayMode(
        payload: String,
        constraints: Map<String, String>,
    ): String {
        val lower = payload.lowercase()
        if (VERBATIM_EXTRACT_REGEX.containsMatchIn(lower)) return DISPLAY_MODE_VERBATIM
        if (
            constraints.containsKey("dateScope") ||
            constraints.containsKey("topic") ||
            constraints.containsKey("timeRangeStart") ||
            DocumentQuestionTime.detectTopic(lower) != null ||
            DocumentQuestionTime.extractFromSubject(lower) != null
        ) {
            return DISPLAY_MODE_SCOPED
        }
        return DISPLAY_MODE_VERBATIM
    }

    private fun extractDocumentExtractQuery(payload: String): String? {
        val lower = payload.lowercase().trim()
        EXTRACT_SUBJECT_REGEX.find(lower)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() }?.let {
            return it
        }
        return extractDocumentQuestionQuery(lower)
    }

    /**
     * Adds dateScope / timeRange / topic for in-app document answers
     * ("from timetable tell me my todays lec slots between 12pm to 3pm").
     */
    private fun enrichDocumentQuestionConstraints(
        payload: String,
        constraints: MutableMap<String, String>,
    ) {
        val lower = payload.lowercase()
        when {
            Regex("""\btoday'?s?\b|\btonight\b""").containsMatchIn(lower) ->
                constraints["dateScope"] = "today"
            Regex("""\btomorrow'?s?\b""").containsMatchIn(lower) ->
                constraints["dateScope"] = "tomorrow"
            Regex("""\byesterday'?s?\b""").containsMatchIn(lower) ->
                constraints["dateScope"] = "yesterday"
            Regex("""\bthis\s+month(?:'?s|s)?\b|\bmonth'?s\b""").containsMatchIn(lower) ->
                constraints["dateScope"] = "this_month"
        }
        DocumentQuestionTime.parseRange(lower)?.let { (start, end) ->
            constraints["timeRangeStart"] = start
            constraints["timeRangeEnd"] = end
        }
        DocumentQuestionTime.detectTopic(lower)?.let { constraints["topic"] = it }
        DocumentQuestionTime.extractFromSubject(lower)?.let { subject ->
            constraints["documentSubject"] = subject
        }
    }

    private fun extractDocumentQuestionQuery(payload: String): String? {
        val lower = payload.lowercase().trim()
        // Keep subject + remainder so date/time words survive for snippet filtering.
        DocumentQuestionTime.extractFromTellQuery(lower)?.let { return it }
        // Drop leading question phrases so search focuses on "todays dinner menu" / "mess menu".
        val stripped = lower
            .replace(Regex("""^(?:please\s+)?(?:what(?:'?s|s)?|show\s+me|tell\s+me)\s+(?:is\s+|on\s+)?"""), "")
            .trim()
        return stripped.ifBlank { lower }.takeIf { it.isNotBlank() }
    }

    /**
     * Lightweight clock/topic helpers kept inside understanding so NIR enrichment
     * does not depend on the storage module.
     */
    private object DocumentQuestionTime {
        private val BETWEEN = Regex(
            """\bbetween\s+(\d{1,2}(?::\d{2})?\s*(?:am|pm)?)\s+(?:and|to)\s+(\d{1,2}(?::\d{2})?\s*(?:am|pm)?)\b""",
            RegexOption.IGNORE_CASE,
        )
        private val FROM_TELL = Regex(
            """\bfrom\s+([a-z][a-z0-9 _-]{1,40}?)\s+(?:tell|what|show|give)\s+(?:me\s+)?(?:is\s+|on\s+)?(.+)$""",
            RegexOption.IGNORE_CASE,
        )
        private val FROM_SUBJECT = Regex(
            """\bfrom\s+([a-z][a-z0-9 _-]{1,40}?)\s+(?:tell|what|show|give)\b""",
            RegexOption.IGNORE_CASE,
        )
        private val LECTURE = Regex("""\b(?:lec(?:ture)?s?|slots?|classes)\b""", RegexOption.IGNORE_CASE)

        fun parseRange(lower: String): Pair<String, String>? {
            val match = BETWEEN.find(lower) ?: return null
            val start = normalizeClock(match.groupValues[1]) ?: return null
            val end = normalizeClock(match.groupValues[2]) ?: return null
            return start to end
        }

        fun detectTopic(lower: String): String? = when {
            LECTURE.containsMatchIn(lower) -> "lec slots"
            "timetable" in lower || "schedule" in lower -> "timetable"
            "breakfast" in lower -> "breakfast"
            "lunch" in lower -> "lunch"
            "dinner" in lower -> "dinner"
            else -> null
        }

        fun extractFromSubject(lower: String): String? =
            FROM_SUBJECT.find(lower)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() }

        /** "from timetable tell me my todays lec slots between 12pm to 3pm" → "timetable my todays …" */
        fun extractFromTellQuery(lower: String): String? {
            val match = FROM_TELL.find(lower) ?: return null
            val subject = match.groupValues[1].trim()
            val rest = match.groupValues[2].trim()
            if (subject.isBlank()) return null
            return listOf(subject, rest).filter { it.isNotBlank() }.joinToString(" ")
        }

        private fun normalizeClock(raw: String): String? {
            val token = raw.trim().lowercase().replace(Regex("""\s+"""), "")
            Regex("""^(\d{1,2})(?::(\d{2}))?(am|pm)$""").matchEntire(token)?.let { m ->
                var hour = m.groupValues[1].toIntOrNull() ?: return null
                val minute = m.groupValues[2].ifBlank { "0" }.toIntOrNull() ?: 0
                val meridiem = m.groupValues[3]
                if (meridiem == "am") {
                    if (hour == 12) hour = 0
                } else if (hour != 12) {
                    hour += 12
                }
                return "%02d:%02d".format(hour, minute)
            }
            Regex("""^(\d{1,2}):(\d{2})$""").matchEntire(token)?.let { m ->
                val hour = m.groupValues[1].toIntOrNull() ?: return null
                val minute = m.groupValues[2].toIntOrNull() ?: return null
                return "%02d:%02d".format(hour, minute)
            }
            return null
        }
    }

    private fun enrichAppControlConstraints(
        intentType: String,
        payload: String,
        constraints: MutableMap<String, String>,
    ) {
        when (intentType) {
            "open_application" -> {
                extractAppName(payload)?.let { constraints["appName"] = it }
            }
            "app_action_search" -> {
                extractAppSearch(payload)?.let { (appName, query) ->
                    constraints["appName"] = appName
                    constraints["searchQuery"] = query
                }
            }
            "negated_command" -> {
                constraints["negated"] = "true"
                extractNegatedAction(payload)?.let { constraints["negatedAction"] = it }
            }
        }
    }

    private fun extractAppName(payload: String): String? {
        val match = OPEN_APP_NAME_REGEX.find(payload.lowercase()) ?: return null
        return cleanAppName(match.groupValues[1])
    }

    private fun extractAppSearch(payload: String): Pair<String, String>? {
        val lower = payload.lowercase()
        OPEN_AND_SEARCH_QUERY_REGEX.find(lower)?.let { match ->
            val app = cleanAppName(match.groupValues[1])
            val query = match.groupValues[2].trim().removePrefix("for ").trim()
            if (app.isNotBlank() && query.isNotBlank()) return app to query
        }
        SEARCH_ON_APP_QUERY_REGEX.find(lower)?.let { match ->
            val query = match.groupValues[1].trim().removePrefix("for ").trim()
            val app = cleanAppName(match.groupValues[2])
            if (app.isNotBlank() && query.isNotBlank()) return app to query
        }
        return null
    }

    private fun extractNegatedAction(payload: String): String? =
        NEGATED_ACTION_REGEX.find(payload.lowercase())
            ?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() }

    private fun extractReminderLabel(payload: String): String? =
        REMINDER_LABEL_REGEX.find(payload.lowercase())
            ?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() }

    private fun extractAlarmLabel(payload: String): String? {
        val lower = payload.lowercase()
        ALARM_LABEL_REGEX.find(lower)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() }?.let {
            return it.replaceFirstChar { ch -> ch.titlecase() }
        }
        return null
    }

    private fun cleanAppName(raw: String): String =
        raw.trim()
            .removePrefix("the ")
            .removeSuffix(" app")
            .removeSuffix(" application")
            .trim()

    private fun extractDocumentQuery(payload: String): String? {
        val lower = payload.lowercase()
        // Prefer "send/share <file phrase> to <recipient>" so "bookly prospectus report" survives.
        SEND_FILE_TO_REGEX.find(lower)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() }?.let {
            return cleanDocumentQuery(it)
        }
        DOCUMENT_QUERY_REGEX.find(lower)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() }?.let {
            return cleanDocumentQuery(it)
        }
        return lower.split(Regex("\\s+"))
            .filter { token ->
                token !in DOCUMENT_QUERY_STOP_WORDS &&
                    token.length > 2 &&
                    !token.contains("@")
            }
            .take(6)
            .joinToString(" ")
            .takeIf { it.isNotBlank() }
    }

    private fun cleanDocumentQuery(raw: String): String =
        raw.trim()
            .removePrefix("the ")
            .replace(Regex("""\s+(?:document|doc|pdf|file)$"""), "")
            .replace(Regex("""\s+on\s+whatsapp$"""), "")
            .trim()

    private fun requiredCapabilitiesFor(intentType: String, resolvedType: String?): List<String> {
        when (intentType) {
            "send_document_whatsapp" -> return listOf("search.documents", "whatsapp")
            "document_question", "extract_document_content" -> return listOf("search.documents")
            // Negated commands execute nothing; "none" keeps the NIR valid for the
            // orchestrator's early exit.
            "negated_command" -> return listOf("none")
            "review_important" -> return listOf("none")
            "read_calendar" -> return listOf("calendar.read")
            "schedule_from_message" -> return listOf("calendar.read", "calendar")
        }
        resolvedType?.let {
            return when (intentType) {
                "read_calendar" -> listOf("calendar.read")
                else -> listOf(it)
            }
        }
        return when (intentType) {
            "set_reminder", "set_alarm" -> listOf("alarm")
            "send_message", "send_whatsapp_message" -> listOf("whatsapp")
            "search_photos" -> listOf("search.photos")
            "search_documents" -> listOf("search.documents")
            "search", "semantic_search" -> listOf("search.semantic")
            "manage_calendar", "create_calendar_event" -> listOf("calendar")
            "read_calendar" -> listOf("calendar.read")
            "schedule_from_message" -> listOf("calendar.read", "calendar")
            "review_important" -> listOf("none")
            "lookup_contact" -> listOf("contacts")
            "share_file" -> listOf("share")
            "open_application" -> listOf("device")
            "control_media" -> listOf("media")
            else -> listOf("search")
        }
    }

    private fun extractChannel(payload: String, intentType: String): String? {
        val lower = payload.lowercase()
        return when {
            "whatsapp" in lower -> "whatsapp"
            Regex("""\b(?:sms|text\s+message)\b""").containsMatchIn(lower) -> "sms"
            Regex("""\b(?:email|e-mail|mail)\b""").containsMatchIn(lower) -> "email"
            intentType in setOf(
                "send_document_whatsapp",
                "send_whatsapp_message",
                "send_message",
            ) -> "whatsapp"
            else -> null
        }
    }

    private fun extractRecipient(payload: String): String? {
        val lower = payload.lowercase()
        val match = RECIPIENT_REGEX.find(lower) ?: return null
        var name = match.groupValues[1].trim()
        name = name
            .removeSuffix(" on whatsapp")
            .removeSuffix(" on whats app")
            .trim()
        // Drop trailing channel words if the regex captured them with a two-word name.
        name = name.replace(Regex("""\s+on\s+whatsapp$"""), "").trim()
        if (name.isBlank()) return null
        // Title-case each word so "atharv sharma" → "Atharv Sharma".
        return name.split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .joinToString(" ") { token ->
                token.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            }
    }

    private fun extractMessage(payload: String, intentType: String): String? {
        if (intentType != "send_whatsapp_message" && intentType != "send_message") return null
        SAYING_REGEX.find(payload)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() }?.let { return it }
        SEND_TO_REGEX.find(payload)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() }?.let { return it }
        return null
    }

    private fun extractFileName(payload: String): String? =
        FILE_NAME_REGEX.find(payload)?.groupValues?.get(1)

    companion object {
        const val NIR_VERSION = 1
        private const val FALLBACK_ENTITY_CONFIDENCE = 0.5
        private const val DEFAULT_EXTRACT_DISPLAY_CHARS = 4_000
        const val DISPLAY_MODE_SCOPED = "scoped"
        const val DISPLAY_MODE_VERBATIM = "verbatim"
        // Captures one to four name tokens before "on whatsapp" / "saying" / end.
        private val RECIPIENT_REGEX = Regex(
            """(?:to|for)\s+([a-z][a-z0-9]+(?:\s+[a-z][a-z0-9]+){0,3}?)(?:\s+(?:saying|about|at|from|on\s+whatsapp)|$)""",
            RegexOption.IGNORE_CASE,
        )
        private val SAYING_REGEX = Regex("saying\\s+(.+)", RegexOption.IGNORE_CASE)
        private val SEND_TO_REGEX = Regex("send\\s+(.+?)\\s+to\\s+", RegexOption.IGNORE_CASE)
        /** Captures the file phrase in "send bookly prospectus report to atharv sharma". */
        private val SEND_FILE_TO_REGEX = Regex(
            """(?:send|share)\s+(?:the\s+)?(.+?)\s+to\s+""",
            RegexOption.IGNORE_CASE,
        )
        private val FILE_NAME_REGEX = Regex("\\b([\\w.-]+\\.(?:pdf|doc|docx|txt|png|jpg|jpeg))\\b", RegexOption.IGNORE_CASE)
        private val DOCUMENT_QUERY_REGEX = Regex(
            """(?:find|search|send|share)\s+(?:the\s+)?(.+?)\s+(?:document|doc|pdf|file|report|invoice|prospectus|menu|to|on|via|through)""",
            RegexOption.IGNORE_CASE,
        )
        private val OPEN_APP_NAME_REGEX = Regex(
            """\b(?:open|launch|start)\s+(.+?)(?:\s+(?:and|then)\b.*)?$""",
        )
        private val OPEN_AND_SEARCH_QUERY_REGEX = Regex(
            """\b(?:open|launch|start)\s+(.+?)\s+(?:and|then)\s+(?:search|play|find|look\s+up)\s+(.+)$""",
        )
        private val SEARCH_ON_APP_QUERY_REGEX = Regex(
            """\b(?:search|play|find|look\s+up)\s+(.+?)\s+(?:on|in|using)\s+(.+)$""",
        )
        private val NEGATED_ACTION_REGEX = Regex(
            """\b(?:do\s+not|don'?t|never|stop|do\s+nothing|cancel that)\s+(.+)$""",
        )
        private val REMINDER_LABEL_REGEX = Regex(
            """\bremind(?:er)?\s+(?:me\s+)?(?:to\s+|about\s+|of\s+)?(.+?)(?:\s+(?:at|on|by|tomorrow|tonight|today|next)\b.*)?$""",
        )
        private val ALARM_LABEL_REGEX = Regex(
            """\b(?:alarm|wake)\s+(?:me\s+)?(?:up\s+)?(?:for|to|about)\s+(.+?)(?:\s+(?:at|on|by|tomorrow|tonight|today|next|\d)\b.*)?$""",
        )
        private val DOCUMENT_QUERY_STOP_WORDS = setOf(
            "find", "search", "send", "share", "the", "document", "documents", "doc", "docs",
            "pdf", "file", "whatsapp", "whats", "app", "via", "through",
            "on", "to", "for",
        )
        private val VERBATIM_EXTRACT_REGEX = Regex(
            """\b(?:full|entire|whole|all)\s+(?:text|content|file|document)\b""",
            RegexOption.IGNORE_CASE,
        )
        private val EXTRACT_SUBJECT_REGEX = Regex(
            """\b(?:extract\s+(?:content|text)|show\s+me\s+the\s+text\s+in|display\s+contents?\s+of|read\s+out\s+(?:the\s+)?file)\s+(?:from\s+|in\s+|of\s+)?(.+)$""",
            RegexOption.IGNORE_CASE,
        )
    }
}
