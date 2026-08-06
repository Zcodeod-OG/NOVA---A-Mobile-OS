package com.nova.runtime.inference.budget

import com.nova.runtime.inference.tier.InferenceTier

data class ContextBudget(
    val maxTokens: Int,
    val maxChars: Int,
)

data class TrimmedContext(
    val text: String,
    val originalTokenCount: Int,
    val trimmedTokenCount: Int,
    val wasTrimmed: Boolean,
)

interface ContextBudgetManager {
    fun budgetFor(tier: InferenceTier): ContextBudget
    fun trim(text: String, tier: InferenceTier): TrimmedContext
}

class DefaultContextBudgetManager : ContextBudgetManager {

    override fun budgetFor(tier: InferenceTier): ContextBudget = when (tier) {
        InferenceTier.DETERMINISTIC -> ContextBudget(maxTokens = 64, maxChars = 256)
        InferenceTier.LIGHT -> ContextBudget(maxTokens = 256, maxChars = 1024)
        InferenceTier.FULL -> ContextBudget(maxTokens = 1024, maxChars = 4096)
    }

    override fun trim(text: String, tier: InferenceTier): TrimmedContext {
        val budget = budgetFor(tier)
        val tokens = text.trim().split(WHITESPACE_REGEX).filter { it.isNotBlank() }
        val originalTokenCount = tokens.size

        val trimmedByTokens = if (tokens.size > budget.maxTokens) {
            tokens.take(budget.maxTokens).joinToString(" ")
        } else {
            text.trim()
        }

        val trimmedText = if (trimmedByTokens.length > budget.maxChars) {
            trimmedByTokens.take(budget.maxChars)
        } else {
            trimmedByTokens
        }

        val trimmedTokenCount = trimmedText.split(WHITESPACE_REGEX).count { it.isNotBlank() }
        return TrimmedContext(
            text = trimmedText,
            originalTokenCount = originalTokenCount,
            trimmedTokenCount = trimmedTokenCount,
            wasTrimmed = trimmedText != text.trim(),
        )
    }

    companion object {
        private val WHITESPACE_REGEX = Regex("\\s+")
    }
}
