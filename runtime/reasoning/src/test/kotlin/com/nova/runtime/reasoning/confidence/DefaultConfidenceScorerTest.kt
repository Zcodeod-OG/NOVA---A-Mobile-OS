package com.nova.runtime.reasoning.confidence

import com.nova.runtime.models.Nir
import com.nova.runtime.reasoning.ambiguity.DefaultAmbiguityResolver
import com.nova.runtime.reasoning.evidence.DefaultEvidenceRanker
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DefaultConfidenceScorerTest {

    private val ranker = DefaultEvidenceRanker()
    private val resolver = DefaultAmbiguityResolver()
    private val scorer = DefaultConfidenceScorer()

    @Test
    fun score_includesExplainableFactors() {
        val nir = Nir(
            version = 1,
            goal = "call john tomorrow",
            entities = listOf("john"),
            constraints = mapOf("time" to "tomorrow"),
            context = emptyMap(),
            requiredCapabilities = emptyList(),
            confidence = 0.9,
        )
        val evidence = ranker.rank(
            nir,
            listOf(
                mapOf(
                    "id" to "1",
                    "source" to "conversation",
                    "content" to "call john tomorrow",
                    "timestamp" to "1000",
                    "confidence" to "0.9",
                ),
            ),
        )
        val resolution = resolver.resolve(nir, evidence)
        val factors = scorer.score(nir, evidence, resolution)

        assertEquals(0.9, factors.nirConfidence)
        assertTrue(factors.evidenceQuality > 0.0)
        assertTrue(factors.overall in 0.0..1.0)
        assertEquals(4, factors.explainableFactors().size)
    }

    @Test
    fun score_sameInputTwice_isDeterministic() {
        val nir = Nir(
            version = 1,
            goal = "john",
            entities = listOf("john"),
            constraints = emptyMap(),
            context = emptyMap(),
            requiredCapabilities = emptyList(),
            confidence = 0.5,
        )
        val evidence = ranker.rank(nir, emptyList())
        val resolution = resolver.resolve(nir, evidence)

        assertEquals(
            scorer.score(nir, evidence, resolution),
            scorer.score(nir, evidence, resolution),
        )
    }
}
