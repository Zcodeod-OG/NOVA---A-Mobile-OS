package com.nova.runtime.understanding.nir

import com.nova.runtime.models.Nir
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
        }

        val confidence = listOf(
            intent.confidence,
            entities.maxOfOrNull { it.confidence } ?: FALLBACK_ENTITY_CONFIDENCE,
            routeDecision.confidence,
        ).average()

        return Nir(
            version = NIR_VERSION,
            goal = intent.goal,
            entities = entities.map { it.value }.distinct(),
            constraints = constraints,
            context = context,
            requiredCapabilities = requiredCapabilitiesFor(intent.intentType),
            confidence = confidence.coerceIn(0.0, 1.0),
        )
    }

    private fun requiredCapabilitiesFor(intentType: String): List<String> = when (intentType) {
        "set_reminder" -> listOf("time", "notifications")
        "send_message" -> listOf("communication")
        "search" -> listOf("knowledge", "device")
        "manage_calendar" -> listOf("time", "calendar")
        "open_application" -> listOf("device")
        "control_media" -> listOf("media")
        else -> listOf("knowledge")
    }

    companion object {
        const val NIR_VERSION = 1
        private const val FALLBACK_ENTITY_CONFIDENCE = 0.5
    }
}
