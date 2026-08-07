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
            extractChannel(normalized.normalizedPayload)?.let { put("channel", it) }
            extractRecipient(normalized.normalizedPayload)?.let { put("recipient", it) }
            extractFileName(normalized.normalizedPayload)?.let { put("fileName", it) }
            extractMessage(normalized.normalizedPayload, intent.intentType)?.let { put("message", it) }
            enrichTimeConstraints(intent.intentType, normalized.normalizedPayload, this)
            enrichCompoundFlowConstraints(intent.intentType, normalized.normalizedPayload, this)
            if (resolved != null) {
                put("capabilityOperation", resolved.qualifiedName)
                put("capabilityType", resolved.capabilityType)
                put("operation", resolved.operation)
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
            "set_alarm", "set_reminder" ->
                NaturalLanguageTimeParser.parseAlarmTriggerMillis(payload)?.let {
                    constraints["triggerAtMillis"] = it.toString()
                }
            "create_calendar_event", "manage_calendar" ->
                NaturalLanguageTimeParser.parseCalendarEvent(payload)?.let { event ->
                    constraints["title"] = event.title
                    constraints["startTime"] = event.startTime.toString()
                    constraints["endTime"] = event.endTime.toString()
                }
        }
    }

    private fun enrichCompoundFlowConstraints(
        intentType: String,
        payload: String,
        constraints: MutableMap<String, String>,
    ) {
        if (intentType != "send_document_whatsapp") return
        constraints["compoundFlow"] = "search_document_whatsapp"
        extractDocumentQuery(payload)?.let { query ->
            constraints["documentQuery"] = query
            constraints["query"] = query
        }
    }

    private fun extractDocumentQuery(payload: String): String? {
        val lower = payload.lowercase()
        DOCUMENT_QUERY_REGEX.find(lower)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() }?.let {
            return it
        }
        return lower.split(Regex("\\s+"))
            .filter { token ->
                token !in DOCUMENT_QUERY_STOP_WORDS &&
                    token.length > 2 &&
                    !token.contains("@")
            }
            .take(4)
            .joinToString(" ")
            .takeIf { it.isNotBlank() }
    }

    private fun requiredCapabilitiesFor(intentType: String, resolvedType: String?): List<String> {
        when (intentType) {
            "send_document_whatsapp" -> return listOf("search.documents", "whatsapp")
        }
        resolvedType?.let { return listOf(it) }
        return when (intentType) {
            "set_reminder", "set_alarm" -> listOf("alarm")
            "send_message", "send_whatsapp_message" -> listOf("whatsapp")
            "search", "search_photos", "search_documents", "semantic_search" -> listOf("search")
            "manage_calendar", "create_calendar_event" -> listOf("calendar")
            "lookup_contact" -> listOf("contacts")
            "share_file" -> listOf("share")
            "open_application" -> listOf("device")
            "control_media" -> listOf("media")
            else -> listOf("search")
        }
    }

    private fun extractChannel(payload: String): String? =
        when {
            "whatsapp" in payload.lowercase() -> "whatsapp"
            "sms" in payload.lowercase() -> "sms"
            else -> null
        }

    private fun extractRecipient(payload: String): String? {
        val lower = payload.lowercase()
        val match = RECIPIENT_REGEX.find(lower) ?: return null
        var name = match.groupValues[1].trim()
        name = name.removeSuffix(" on whatsapp").removeSuffix(" on whats app").trim()
        if (name.isBlank()) return null
        return name.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
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
        private val RECIPIENT_REGEX = Regex(
            "(?:to|for)\\s+([a-z][a-z0-9\\s]{0,30}?)(?:\\s+(?:saying|about|at|from|on\\s+whatsapp)|$)",
            RegexOption.IGNORE_CASE,
        )
        private val SAYING_REGEX = Regex("saying\\s+(.+)", RegexOption.IGNORE_CASE)
        private val SEND_TO_REGEX = Regex("send\\s+(.+?)\\s+to\\s+", RegexOption.IGNORE_CASE)
        private val FILE_NAME_REGEX = Regex("\\b([\\w.-]+\\.(?:pdf|doc|docx|txt|png|jpg|jpeg))\\b", RegexOption.IGNORE_CASE)
        private val DOCUMENT_QUERY_REGEX = Regex(
            """(?:find|search|send|share)\s+(?:the\s+)?(.+?)\s+(?:document|doc|pdf|file|report|invoice|to|on|via|through)""",
            RegexOption.IGNORE_CASE,
        )
        private val DOCUMENT_QUERY_STOP_WORDS = setOf(
            "find", "search", "send", "share", "the", "document", "documents", "doc", "docs",
            "pdf", "file", "report", "invoice", "whatsapp", "whats", "app", "via", "through", "on", "to", "for",
        )
    }
}
