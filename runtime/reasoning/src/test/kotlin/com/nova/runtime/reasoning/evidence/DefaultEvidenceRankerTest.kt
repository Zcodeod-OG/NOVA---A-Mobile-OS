package com.nova.runtime.reasoning.evidence

import com.nova.runtime.models.Nir
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DefaultEvidenceRankerTest {

    private val ranker = DefaultEvidenceRanker()

    @Test
    fun rank_ordersByCompositeScoreDeterministically() {
        val nir = Nir(
            version = 1,
            goal = "call john tomorrow",
            entities = listOf("john", "tomorrow"),
            constraints = mapOf("time" to "tomorrow"),
            context = emptyMap(),
            requiredCapabilities = listOf("communication"),
            confidence = 0.8,
        )
        val entries = listOf(
            entry("b", "calendar", "Team meeting tomorrow", 2000L, 0.7),
            entry("a", "conversation", "Call john tomorrow afternoon", 5000L, 0.9),
            entry("c", "note", "Buy groceries", 1000L, 0.6),
        )

        val first = ranker.rank(nir, entries)
        val second = ranker.rank(nir, entries)

        assertEquals(first, second)
        assertEquals("a", first.first().id)
        assertTrue(first.first().compositeScore >= first[1].compositeScore)
    }

    @Test
    fun rank_tieBreaksById() {
        val nir = Nir(
            version = 1,
            goal = "john",
            entities = listOf("john"),
            constraints = emptyMap(),
            context = emptyMap(),
            requiredCapabilities = emptyList(),
            confidence = 0.5,
        )
        val entries = listOf(
            entry("z-id", "contact", "john z", 1000L, 0.5),
            entry("a-id", "contact", "john a", 1000L, 0.5),
        )

        val ranked = ranker.rank(nir, entries)
        assertEquals(listOf("a-id", "z-id"), ranked.map { it.id })
    }

    private fun entry(
        id: String,
        source: String,
        content: String,
        timestamp: Long,
        confidence: Double,
    ): Map<String, String> =
        mapOf(
            "id" to id,
            "source" to source,
            "content" to content,
            "timestamp" to timestamp.toString(),
            "confidence" to confidence.toString(),
        )
}
