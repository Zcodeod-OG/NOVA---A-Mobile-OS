package com.nova.runtime.understanding.normalization

import com.nova.runtime.models.Modality
import com.nova.runtime.models.Observation
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DefaultObservationNormalizerTest {

    private val normalizer = DefaultObservationNormalizer()

    @Test
    fun normalize_trimsPayloadAndCollapsesWhitespace() {
        val observation = sampleObservation(payload = "  Remind   me   tomorrow  ")

        val normalized = normalizer.normalize(observation)

        assertEquals("Remind me tomorrow", normalized.normalizedPayload)
        assertEquals(listOf("remind", "me", "tomorrow"), normalized.tokens)
    }

    @Test
    fun normalize_canonicalizesMetadataKeys() {
        val observation = sampleObservation(
            payload = "hello",
            metadata = mapOf(" TurnNumber " to " 2 ", "Source" to "conversation"),
        )

        val normalized = normalizer.normalize(observation)

        assertEquals("2", normalized.metadata["turnnumber"])
        assertEquals("conversation", normalized.metadata["source"])
        assertEquals("text", normalized.metadata["modality"])
    }

    @Test
    fun normalize_emptyPayloadProducesEmptyTokens() {
        val observation = sampleObservation(payload = "   ")

        val normalized = normalizer.normalize(observation)

        assertTrue(normalized.normalizedPayload.isEmpty())
        assertTrue(normalized.tokens.isEmpty())
    }

    private fun sampleObservation(
        payload: String,
        metadata: Map<String, String> = emptyMap(),
    ): Observation = Observation(
        id = UUID.randomUUID(),
        timestamp = Instant.parse("2026-08-06T10:00:00Z"),
        sessionId = UUID.randomUUID(),
        traceId = UUID.randomUUID(),
        modality = Modality.TEXT,
        payload = payload,
        metadata = metadata,
    )
}
