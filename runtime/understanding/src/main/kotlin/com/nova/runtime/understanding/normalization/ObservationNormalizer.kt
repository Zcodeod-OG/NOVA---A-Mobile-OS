package com.nova.runtime.understanding.normalization

import com.nova.runtime.models.Observation

interface ObservationNormalizer {
    fun normalize(observation: Observation): NormalizedObservation
}

class DefaultObservationNormalizer : ObservationNormalizer {
    override fun normalize(observation: Observation): NormalizedObservation {
        val normalizedPayload = observation.payload
            .trim()
            .replace(WHITESPACE_REGEX, " ")

        val tokens = if (normalizedPayload.isEmpty()) {
            emptyList()
        } else {
            normalizedPayload.lowercase().split(TOKEN_SPLIT_REGEX).filter { it.isNotBlank() }
        }

        val metadata = buildMap {
            observation.metadata.forEach { (key, value) ->
                put(key.trim().lowercase(), value.trim())
            }
            put("modality", observation.modality.name.lowercase())
        }

        return NormalizedObservation(
            observation = observation,
            normalizedPayload = normalizedPayload,
            tokens = tokens,
            metadata = metadata,
        )
    }

    companion object {
        private val WHITESPACE_REGEX = Regex("\\s+")
        private val TOKEN_SPLIT_REGEX = Regex("\\s+")
    }
}
