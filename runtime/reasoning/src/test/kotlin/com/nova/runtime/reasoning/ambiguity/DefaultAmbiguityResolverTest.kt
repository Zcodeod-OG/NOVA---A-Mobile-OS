package com.nova.runtime.reasoning.ambiguity

import com.nova.runtime.models.Nir
import com.nova.runtime.reasoning.evidence.DefaultEvidenceRanker
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DefaultAmbiguityResolverTest {

    private val ranker = DefaultEvidenceRanker()
    private val resolver = DefaultAmbiguityResolver()

    @Test
    fun resolve_picksHighestRankedCandidate() {
        val nir = Nir(
            version = 1,
            goal = "message john",
            entities = listOf("john"),
            constraints = emptyMap(),
            context = emptyMap(),
            requiredCapabilities = listOf("communication"),
            confidence = 0.8,
        )
        val evidence = ranker.rank(
            nir,
            listOf(
                mapOf(
                    "id" to "1",
                    "source" to "contact",
                    "content" to "John Smith contact",
                    "timestamp" to "1000",
                    "confidence" to "0.7",
                ),
                mapOf(
                    "id" to "2",
                    "source" to "contact",
                    "content" to "John Doe contact",
                    "timestamp" to "9000",
                    "confidence" to "0.95",
                ),
            ),
        )

        val result = resolver.resolve(nir, evidence)
        assertTrue(result.resolvedEntities.values.any { it.contains("John Doe") })
        assertTrue(result.assumptions.isNotEmpty())
    }

    @Test
    fun resolve_sameInputTwice_isDeterministic() {
        val nir = Nir(
            version = 1,
            goal = "remind john",
            entities = listOf("john"),
            constraints = mapOf("time" to "tomorrow"),
            context = emptyMap(),
            requiredCapabilities = emptyList(),
            confidence = 0.7,
        )
        val evidence = ranker.rank(
            nir,
            listOf(
                mapOf(
                    "id" to "1",
                    "source" to "conversation",
                    "content" to "john reminder tomorrow",
                    "timestamp" to "5000",
                    "confidence" to "0.8",
                ),
            ),
        )

        assertEquals(resolver.resolve(nir, evidence), resolver.resolve(nir, evidence))
    }
}
