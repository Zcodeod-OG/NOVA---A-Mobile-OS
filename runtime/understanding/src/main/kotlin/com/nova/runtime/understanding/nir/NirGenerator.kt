package com.nova.runtime.understanding.nir

import com.nova.runtime.models.Nir
import com.nova.runtime.models.NovaCapabilityOperationResolver
import com.nova.runtime.models.NovaCapabilityOperations
import com.nova.runtime.understanding.entity.ExtractedEntity
import com.nova.runtime.understanding.intent.DetectedIntent
import com.nova.runtime.understanding.normalization.NormalizedObservation
import com.nova.runtime.understanding.routing.InferenceRouteDecision

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

    private fun requiredCapabilitiesFor(intentType: String, resolvedType: String?): List<String> {
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
        val match = RECIPIENT_REGEX.find(payload.lowercase()) ?: return null
        return match.groupValues[1].trim().replaceFirstChar { it.uppercase() }
    }

    private fun extractFileName(payload: String): String? =
        FILE_NAME_REGEX.find(payload)?.groupValues?.get(1)

    companion object {
        const val NIR_VERSION = 1
        private const val FALLBACK_ENTITY_CONFIDENCE = 0.5
        private val RECIPIENT_REGEX = Regex("(?:to|for)\\s+([a-z][a-z0-9\\s]{0,30}?)(?:\\s+(?:saying|about|at|for|from)|$)")
        private val FILE_NAME_REGEX = Regex("\\b([\\w.-]+\\.(?:pdf|doc|docx|txt|png|jpg|jpeg))\\b", RegexOption.IGNORE_CASE)
    }
}
