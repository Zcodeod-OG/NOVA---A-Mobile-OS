package com.nova.runtime.inference.complexity

import com.nova.runtime.inference.tier.InferenceTier
import com.nova.runtime.models.contracts.InferenceRequest
import kotlin.test.Test
import kotlin.test.assertEquals

class DefaultComplexityAnalyzerTest {

    private val analyzer = DefaultComplexityAnalyzer()

    @Test
    fun analyze_blankPrompt_recommendsDeterministicTier() {
        val analysis = analyzer.analyze(request("   "))

        assertEquals(InferenceTier.DETERMINISTIC, analysis.recommendedTier)
        assertEquals("empty_prompt", analysis.reason)
    }

    @Test
    fun analyze_shortUtterance_recommendsDeterministicTier() {
        val analysis = analyzer.analyze(request("hi there"))

        assertEquals(InferenceTier.DETERMINISTIC, analysis.recommendedTier)
        assertEquals("short_utterance", analysis.reason)
    }

    @Test
    fun analyze_mediumUtterance_recommendsLightTier() {
        val analysis = analyzer.analyze(request("remind me tomorrow at three pm please"))

        assertEquals(InferenceTier.LIGHT, analysis.recommendedTier)
        assertEquals("medium_complexity", analysis.reason)
    }

    @Test
    fun analyze_longQuestion_recommendsFullTier() {
        val analysis = analyzer.analyze(
            request("what should I do if I need to schedule a meeting and send a message to john tomorrow afternoon"),
        )

        assertEquals(InferenceTier.FULL, analysis.recommendedTier)
    }

    @Test
    fun analyze_tierHintOverridesHeuristics() {
        val analysis = analyzer.analyze(
            request(
                prompt = "hi",
                tierHint = InferenceTier.FULL.level,
            ),
        )

        assertEquals(InferenceTier.FULL, analysis.recommendedTier)
        assertEquals("tier_hint", analysis.reason)
    }

    private fun request(prompt: String, tierHint: Int? = null): InferenceRequest =
        InferenceRequest(
            prompt = prompt,
            tierHint = tierHint,
            traceId = "00000000-0000-0000-0000-000000000001",
        )
}
