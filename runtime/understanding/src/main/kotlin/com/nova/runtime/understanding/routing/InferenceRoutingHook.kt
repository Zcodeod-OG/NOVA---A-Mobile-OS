package com.nova.runtime.understanding.routing

import com.nova.runtime.understanding.normalization.NormalizedObservation

data class InferenceRouteDecision(
    val tier: Int,
    val reason: String,
    val useDeterministicPath: Boolean,
    val confidence: Double,
)

/** Adaptive routing hook — stub tier selection without real AIE integration. */
interface InferenceRoutingHook {
    suspend fun route(normalized: NormalizedObservation): InferenceRouteDecision
}

class StubInferenceRoutingHook : InferenceRoutingHook {
    override suspend fun route(normalized: NormalizedObservation): InferenceRouteDecision {
        val tokenCount = normalized.tokens.size
        val payloadLength = normalized.normalizedPayload.length

        return when {
            normalized.normalizedPayload.isBlank() -> InferenceRouteDecision(
                tier = TIER_DETERMINISTIC,
                reason = "empty_payload",
                useDeterministicPath = true,
                confidence = 1.0,
            )
            tokenCount <= SHORT_UTTERANCE_TOKENS && payloadLength <= SHORT_UTTERANCE_CHARS -> {
                InferenceRouteDecision(
                    tier = TIER_DETERMINISTIC,
                    reason = "short_utterance",
                    useDeterministicPath = true,
                    confidence = 0.9,
                )
            }
            tokenCount <= MEDIUM_UTTERANCE_TOKENS -> InferenceRouteDecision(
                tier = TIER_RULE_ENGINE,
                reason = "medium_complexity",
                useDeterministicPath = true,
                confidence = 0.75,
            )
            else -> InferenceRouteDecision(
                tier = TIER_LIGHTWEIGHT_MODEL,
                reason = "complex_utterance",
                useDeterministicPath = false,
                confidence = 0.6,
            )
        }
    }

    companion object {
        const val TIER_DETERMINISTIC = 0
        const val TIER_RULE_ENGINE = 1
        const val TIER_LIGHTWEIGHT_MODEL = 2

        private const val SHORT_UTTERANCE_TOKENS = 4
        private const val SHORT_UTTERANCE_CHARS = 32
        private const val MEDIUM_UTTERANCE_TOKENS = 12
    }
}
