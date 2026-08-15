package com.nova.runtime.understanding.entity

import com.nova.runtime.understanding.normalization.NormalizedObservation

data class ExtractedEntity(
    val type: String,
    val value: String,
    val confidence: Double,
)

interface EntityExtractor {
    suspend fun extract(normalized: NormalizedObservation): List<ExtractedEntity>
}

/**
 * Placeholder entity extraction — deterministic rules only, no NLP/ML.
 * Recognizes quoted phrases, @mentions, and simple time expressions.
 */
class PlaceholderEntityExtractor : EntityExtractor {
    override suspend fun extract(normalized: NormalizedObservation): List<ExtractedEntity> {
        val payload = normalized.normalizedPayload
        if (payload.isBlank()) return emptyList()

        val entities = mutableListOf<ExtractedEntity>()

        QUOTED_ENTITY_REGEX.findAll(payload).forEach { match ->
            val value = match.groupValues.drop(1).firstOrNull { it.isNotEmpty() } ?: return@forEach
            entities += ExtractedEntity(
                type = "quoted_phrase",
                value = value,
                confidence = PLACEHOLDER_CONFIDENCE,
            )
        }

        MENTION_REGEX.findAll(payload).forEach { match ->
            entities += ExtractedEntity(
                type = "mention",
                value = match.value.removePrefix("@"),
                confidence = PLACEHOLDER_CONFIDENCE,
            )
        }

        TIME_EXPRESSION_REGEX.findAll(payload.lowercase()).forEach { match ->
            entities += ExtractedEntity(
                type = "time_expression",
                value = match.value,
                confidence = PLACEHOLDER_CONFIDENCE,
            )
        }

        if (entities.isEmpty() && normalized.tokens.isNotEmpty()) {
            entities += ExtractedEntity(
                type = "topic",
                value = normalized.tokens.take(MAX_TOPIC_TOKENS).joinToString(" "),
                confidence = FALLBACK_CONFIDENCE,
            )
        }

        return entities.distinctBy { "${it.type}:${it.value}" }
    }

    companion object {
        private const val PLACEHOLDER_CONFIDENCE = 0.75
        private const val FALLBACK_CONFIDENCE = 0.5
        private const val MAX_TOPIC_TOKENS = 3
        private val QUOTED_ENTITY_REGEX = Regex("\"([^\"]+)\"|'([^']+)'")
        private val MENTION_REGEX = Regex("@\\w+")
        private val TIME_EXPRESSION_REGEX = Regex(
            "\\b(tomorrow|today|tonight|next week|at \\d{1,2}(?::\\d{2})?\\s*(?:am|pm)?)\\b",
        )
    }
}
