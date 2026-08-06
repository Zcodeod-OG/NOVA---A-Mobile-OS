package com.nova.runtime.inference.budget

import com.nova.runtime.inference.tier.InferenceTier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DefaultContextBudgetManagerTest {

    private val budgetManager = DefaultContextBudgetManager()

    @Test
    fun budgetFor_tiersIncreaseWithComplexity() {
        val deterministic = budgetManager.budgetFor(InferenceTier.DETERMINISTIC)
        val light = budgetManager.budgetFor(InferenceTier.LIGHT)
        val full = budgetManager.budgetFor(InferenceTier.FULL)

        assertTrue(deterministic.maxTokens < light.maxTokens)
        assertTrue(light.maxTokens < full.maxTokens)
    }

    @Test
    fun trim_longPromptIsTrimmedForTier() {
        val longPrompt = List(200) { "word$it" }.joinToString(" ")
        val trimmed = budgetManager.trim(longPrompt, InferenceTier.DETERMINISTIC)

        assertTrue(trimmed.wasTrimmed)
        assertTrue(trimmed.trimmedTokenCount <= budgetManager.budgetFor(InferenceTier.DETERMINISTIC).maxTokens)
        assertEquals(200, trimmed.originalTokenCount)
    }
}
