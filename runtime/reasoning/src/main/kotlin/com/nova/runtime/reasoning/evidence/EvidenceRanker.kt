package com.nova.runtime.reasoning.evidence

import com.nova.runtime.models.Nir
import com.nova.runtime.reasoning.model.EvidenceItem
import kotlin.math.round

interface EvidenceRanker {
    fun rank(nir: Nir, memoryEntries: List<Map<String, String>>): List<EvidenceItem>
}

/**
 * Deterministic evidence ranking by relevance, recency, and confidence — TDD §10.
 */
class DefaultEvidenceRanker : EvidenceRanker {
    override fun rank(nir: Nir, memoryEntries: List<Map<String, String>>): List<EvidenceItem> {
        if (memoryEntries.isEmpty()) return emptyList()

        val nirTokens = collectNirTokens(nir)
        val recencyById = computeRecencyScores(memoryEntries)

        return memoryEntries
            .mapNotNull { entry -> toEvidenceItem(entry, nirTokens, recencyById) }
            .sortedWith(
                compareByDescending<EvidenceItem> { it.compositeScore }
                    .thenBy { it.id },
            )
    }

    private fun toEvidenceItem(
        entry: Map<String, String>,
        nirTokens: Set<String>,
        recencyById: Map<String, Double>,
    ): EvidenceItem? {
        val id = entry["id"]?.takeIf { it.isNotBlank() } ?: return null
        val content = entry["content"] ?: entry["text"] ?: ""
        val source = entry["source"] ?: "memory"
        val relevance = computeRelevance(nirTokens, content, entry["entityValue"])
        val recency = recencyById[id] ?: DEFAULT_RECENCY
        val confidence = entry["confidence"]?.toDoubleOrNull()?.coerceIn(0.0, 1.0) ?: DEFAULT_CONFIDENCE
        val composite = roundScore(
            RELEVANCE_WEIGHT * relevance +
                RECENCY_WEIGHT * recency +
                CONFIDENCE_WEIGHT * confidence,
        )

        return EvidenceItem(
            id = id,
            source = source,
            content = content,
            relevanceScore = roundScore(relevance),
            recencyScore = roundScore(recency),
            confidenceScore = roundScore(confidence),
            compositeScore = composite,
        )
    }

    private fun collectNirTokens(nir: Nir): Set<String> {
        val tokens = mutableSetOf<String>()
        tokens += tokenize(nir.goal)
        nir.entities.forEach { tokens += tokenize(it) }
        nir.constraints.values.forEach { tokens += tokenize(it) }
        return tokens
    }

    private fun computeRelevance(
        nirTokens: Set<String>,
        content: String,
        entityValue: String?,
    ): Double {
        if (nirTokens.isEmpty()) return 0.0

        val entryTokens = tokenize(content) + tokenize(entityValue.orEmpty())
        if (entryTokens.isEmpty()) return 0.0

        val overlap = nirTokens.intersect(entryTokens).size
        return overlap.toDouble() / nirTokens.size
    }

    private fun computeRecencyScores(entries: List<Map<String, String>>): Map<String, Double> {
        val timestamps = entries.mapNotNull { entry ->
            val id = entry["id"] ?: return@mapNotNull null
            val timestamp = entry["timestamp"]?.toLongOrNull()
                ?: entry["recencyRank"]?.toDoubleOrNull()?.let { rankToTimestamp(it) }
            if (timestamp != null) id to timestamp else null
        }

        if (timestamps.isEmpty()) {
            return entries.mapNotNull { entry ->
                entry["id"]?.let { id ->
                    id to (entry["recencyRank"]?.toDoubleOrNull()?.coerceIn(0.0, 1.0) ?: DEFAULT_RECENCY)
                }
            }.toMap()
        }

        val maxTs = timestamps.maxOf { it.second }
        val minTs = timestamps.minOf { it.second }
        val range = (maxTs - minTs).coerceAtLeast(1)

        return timestamps.associate { (id, ts) ->
            id to ((ts - minTs).toDouble() / range)
        }
    }

    private fun tokenize(text: String): Set<String> =
        text.lowercase()
            .split(TOKEN_SPLIT_REGEX)
            .filter { it.length > 1 }
            .toSet()

    private fun roundScore(value: Double): Double =
        round(value * SCORE_PRECISION) / SCORE_PRECISION

    private fun rankToTimestamp(recencyRank: Double): Long =
        (recencyRank.coerceIn(0.0, 1.0) * MAX_SYNTHETIC_TIMESTAMP).toLong()

    companion object {
        private const val RELEVANCE_WEIGHT = 0.5
        private const val RECENCY_WEIGHT = 0.3
        private const val CONFIDENCE_WEIGHT = 0.2
        private const val DEFAULT_RECENCY = 0.5
        private const val DEFAULT_CONFIDENCE = 0.6
        private const val SCORE_PRECISION = 10_000.0
        private const val MAX_SYNTHETIC_TIMESTAMP = 1_000_000L
        private val TOKEN_SPLIT_REGEX = Regex("\\W+")
    }
}
