package com.nova.runtime.understanding.constraint

import com.nova.runtime.understanding.entity.ExtractedEntity
import com.nova.runtime.understanding.normalization.NormalizedObservation

interface ConstraintExtractor {
    suspend fun extract(
        normalized: NormalizedObservation,
        entities: List<ExtractedEntity>,
    ): Map<String, String>
}

/**
 * Placeholder constraint extraction — keyword and entity heuristics only.
 */
class PlaceholderConstraintExtractor : ConstraintExtractor {
    override suspend fun extract(
        normalized: NormalizedObservation,
        entities: List<ExtractedEntity>,
    ): Map<String, String> {
        val payload = normalized.normalizedPayload.lowercase()
        if (payload.isBlank()) return emptyMap()

        val constraints = linkedMapOf<String, String>()

        constraints["modality"] = normalized.metadata["modality"].orEmpty()

        NEGATION_REGEX.find(payload)?.let { match ->
            constraints["negation"] = match.groupValues[1].trim()
        }

        DEADLINE_REGEX.find(payload)?.let { match ->
            constraints["deadline"] = match.groupValues[1].trim()
        }

        entities.filter { it.type == "time_expression" }.forEachIndexed { index, entity ->
            constraints["time_$index"] = entity.value
        }

        if (payload.contains("urgent") || payload.contains("asap")) {
            constraints["priority"] = "high"
        }

        return constraints.filterValues { it.isNotBlank() }
    }

    companion object {
        private val NEGATION_REGEX = Regex("\\b(?:don't|do not|never|without)\\s+([\\w\\s]{1,40})")
        private val DEADLINE_REGEX = Regex("\\b(?:by|before|until)\\s+([\\w\\s:]{1,30})")
    }
}
