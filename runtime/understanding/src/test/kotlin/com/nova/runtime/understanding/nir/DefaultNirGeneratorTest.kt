package com.nova.runtime.understanding.nir

import com.nova.runtime.models.Modality
import com.nova.runtime.models.Observation
import com.nova.runtime.understanding.entity.ExtractedEntity
import com.nova.runtime.understanding.intent.DetectedIntent
import com.nova.runtime.understanding.normalization.NormalizedObservation
import com.nova.runtime.understanding.routing.InferenceRouteDecision
import com.nova.runtime.understanding.routing.StubInferenceRoutingHook
import kotlinx.coroutines.test.runTest
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DefaultNirGeneratorTest {

    private val generator = DefaultNirGenerator()

    @Test
    fun generate_mapsIntentEntitiesAndCapabilities() = runTest {
        val normalized = normalizedObservation("Remind me tomorrow")
        val intent = DetectedIntent(
            goal = "set_reminder",
            intentType = "set_reminder",
            confidence = 0.8,
        )
        val entities = listOf(
            ExtractedEntity(type = "time_expression", value = "tomorrow", confidence = 0.75),
        )
        val constraints = mapOf("modality" to "text", "time_0" to "tomorrow")
        val route = InferenceRouteDecision(
            tier = StubInferenceRoutingHook.TIER_DETERMINISTIC,
            reason = "short_utterance",
            useDeterministicPath = true,
            confidence = 0.9,
        )

        val nir = generator.generate(normalized, intent, entities, constraints, route)

        assertEquals(DefaultNirGenerator.NIR_VERSION, nir.version)
        assertEquals("set_reminder", nir.goal)
        assertEquals(listOf("tomorrow"), nir.entities)
        assertEquals(listOf("alarm"), nir.requiredCapabilities)
        assertTrue(nir.confidence in 0.0..1.0)
        assertEquals("0", nir.context["inferenceTier"])
    }

    private fun normalizedObservation(payload: String): NormalizedObservation {
        val observation = Observation(
            id = UUID.randomUUID(),
            timestamp = Instant.parse("2026-08-06T10:00:00Z"),
            sessionId = UUID.randomUUID(),
            traceId = UUID.randomUUID(),
            modality = Modality.TEXT,
            payload = payload,
            metadata = mapOf("source" to "test"),
        )
        return NormalizedObservation(
            observation = observation,
            normalizedPayload = payload.trim(),
            tokens = payload.lowercase().split("\\s+".toRegex()),
            metadata = mapOf("modality" to "text", "source" to "test"),
        )
    }
}
