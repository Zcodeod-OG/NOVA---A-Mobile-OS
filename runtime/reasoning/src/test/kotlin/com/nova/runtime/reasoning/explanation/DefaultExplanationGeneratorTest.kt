package com.nova.runtime.reasoning.explanation

import com.nova.runtime.models.Nir
import com.nova.runtime.reasoning.ambiguity.DefaultAmbiguityResolver
import com.nova.runtime.reasoning.confidence.DefaultConfidenceScorer
import com.nova.runtime.reasoning.evidence.DefaultEvidenceRanker
import kotlin.test.Test
import kotlin.test.assertTrue

class DefaultExplanationGeneratorTest {

    private val generator = DefaultExplanationGenerator()

    @Test
    fun generate_producesHumanReadableExplanations() {
        val nir = Nir(
            version = 1,
            goal = "send message to john",
            entities = listOf("john"),
            constraints = emptyMap(),
            context = emptyMap(),
            requiredCapabilities = listOf("communication"),
            confidence = 0.8,
        )
        val evidence = DefaultEvidenceRanker().rank(
            nir,
            listOf(
                mapOf(
                    "id" to "1",
                    "source" to "contact",
                    "content" to "John Smith",
                    "timestamp" to "1000",
                    "confidence" to "0.8",
                ),
                mapOf(
                    "id" to "2",
                    "source" to "contact",
                    "content" to "John Doe",
                    "timestamp" to "2000",
                    "confidence" to "0.9",
                ),
            ),
        )
        val resolution = DefaultAmbiguityResolver().resolve(nir, evidence)
        val confidence = DefaultConfidenceScorer().score(nir, evidence, resolution)

        val explanations = generator.generate(nir, evidence, resolution, confidence)

        assertTrue(explanations.any { it.contains("Reasoned over goal") })
        assertTrue(explanations.any { it.contains("Evidence rank 1") })
        assertTrue(explanations.any { it.contains("Overall reasoning confidence") })
    }
}
