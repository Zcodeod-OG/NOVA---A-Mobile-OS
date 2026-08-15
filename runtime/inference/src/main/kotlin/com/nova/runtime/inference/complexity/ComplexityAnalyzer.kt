package com.nova.runtime.inference.complexity

import com.nova.runtime.inference.tier.InferenceTier
import com.nova.runtime.models.contracts.InferenceRequest

data class ComplexityAnalysis(
    val tokenCount: Int,
    val charCount: Int,
    val recommendedTier: InferenceTier,
    val score: Double,
    val reason: String,
)

interface ComplexityAnalyzer {
    fun analyze(request: InferenceRequest): ComplexityAnalysis
}

class DefaultComplexityAnalyzer : ComplexityAnalyzer {
    override fun analyze(request: InferenceRequest): ComplexityAnalysis {
        val prompt = request.prompt.trim()
        val tokens = tokenize(prompt)
        val tokenCount = tokens.size
        val charCount = prompt.length

        if (prompt.isBlank()) {
            return ComplexityAnalysis(
                tokenCount = 0,
                charCount = 0,
                recommendedTier = InferenceTier.DETERMINISTIC,
                score = 0.0,
                reason = "empty_prompt",
            )
        }

        request.tierHint?.let { hint ->
            InferenceTier.fromLevel(hint)?.let { tier ->
                return ComplexityAnalysis(
                    tokenCount = tokenCount,
                    charCount = charCount,
                    recommendedTier = tier,
                    score = tierScore(tier),
                    reason = "tier_hint",
                )
            }
        }

        val (tier, reason) = when {
            tokenCount <= SHORT_UTTERANCE_TOKENS && charCount <= SHORT_UTTERANCE_CHARS ->
                InferenceTier.DETERMINISTIC to "short_utterance"
            tokenCount <= MEDIUM_UTTERANCE_TOKENS ->
                InferenceTier.LIGHT to "medium_complexity"
            containsMultiClause(prompt) || containsQuestion(prompt) ->
                InferenceTier.FULL to "complex_structure"
            else ->
                InferenceTier.FULL to "long_utterance"
        }

        return ComplexityAnalysis(
            tokenCount = tokenCount,
            charCount = charCount,
            recommendedTier = tier,
            score = tierScore(tier),
            reason = reason,
        )
    }

    private fun tokenize(prompt: String): List<String> =
        prompt.split(WHITESPACE_REGEX).filter { it.isNotBlank() }

    private fun tierScore(tier: InferenceTier): Double = when (tier) {
        InferenceTier.DETERMINISTIC -> 0.1
        InferenceTier.LIGHT -> 0.45
        InferenceTier.FULL -> 0.85
    }

    private fun containsMultiClause(prompt: String): Boolean =
        MULTI_CLAUSE_REGEX.containsMatchIn(prompt)

    private fun containsQuestion(prompt: String): Boolean =
        prompt.contains('?') || prompt.startsWith("what", ignoreCase = true) ||
            prompt.startsWith("how", ignoreCase = true) ||
            prompt.startsWith("why", ignoreCase = true)

    companion object {
        private val WHITESPACE_REGEX = Regex("\\s+")
        private val MULTI_CLAUSE_REGEX = Regex("\\b(and|or|but|then|because|although)\\b", RegexOption.IGNORE_CASE)

        const val SHORT_UTTERANCE_TOKENS = 4
        const val SHORT_UTTERANCE_CHARS = 32
        const val MEDIUM_UTTERANCE_TOKENS = 12
    }
}
