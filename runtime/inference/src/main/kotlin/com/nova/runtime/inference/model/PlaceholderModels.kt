package com.nova.runtime.inference.model

import com.nova.runtime.inference.tier.InferenceTier
import com.nova.runtime.models.ErrorCategory
import com.nova.runtime.models.ErrorSeverity
import com.nova.runtime.models.RuntimeError

/** Tier 0 — deterministic pattern matching, no model invocation. */
class DeterministicInferenceModel : InferenceModel {
    override val tier: InferenceTier = InferenceTier.DETERMINISTIC
    override val modelId: String = "deterministic-v1"
    override val capabilities: Set<String> = setOf("pattern_match", "greeting", "ack")

    override suspend fun infer(input: ModelInput): ModelOutput {
        val sourceText = input.metadata["userContent"] ?: input.prompt
        val normalized = sourceText.trim().lowercase()
        if (normalized.isBlank()) {
            return ModelOutput.Failure(
                RuntimeError(
                    code = "AIE_EMPTY_PROMPT",
                    category = ErrorCategory.VALIDATION,
                    severity = ErrorSeverity.LOW,
                    recoverable = true,
                    userVisibleMessage = "Cannot infer on an empty prompt.",
                ),
            )
        }

        val output = when {
            GREETING_REGEX.matches(normalized) -> "ack:greeting"
            ACK_REGEX.matches(normalized) -> "ack:ok"
            else -> "deterministic:${normalized.take(DETERMINISTIC_OUTPUT_LIMIT)}"
        }
        return ModelOutput.Success(output)
    }

    companion object {
        private val GREETING_REGEX = Regex("^(hi|hello|hey|good morning|good evening)\\b.*")
        private val ACK_REGEX = Regex("^(ok|okay|thanks|thank you|yes|no)\\b.*")
        private const val DETERMINISTIC_OUTPUT_LIMIT = 64
    }
}

/** Tier 1 — lightweight rule engine fallback when ONNX model is unavailable. */
class RuleEngineInferenceModel : InferenceModel {
    override val tier: InferenceTier = InferenceTier.LIGHT
    override val modelId: String = "rule-engine-v1"
    override val capabilities: Set<String> = setOf("intent_hint", "entity_hint", "constraint_hint")

    override suspend fun infer(input: ModelInput): ModelOutput {
        val tokens = input.prompt.trim().split(WHITESPACE_REGEX).filter { it.isNotBlank() }
        if (tokens.isEmpty()) {
            return ModelOutput.Failure(
                RuntimeError(
                    code = "AIE_EMPTY_PROMPT",
                    category = ErrorCategory.VALIDATION,
                    severity = ErrorSeverity.LOW,
                    recoverable = true,
                    userVisibleMessage = "Cannot infer on an empty prompt.",
                ),
            )
        }

        val intentHint = when {
            tokens.any { it.equals("remind", ignoreCase = true) || it.equals("alarm", ignoreCase = true) } ->
                "set_reminder"
            tokens.any { it.equals("call", ignoreCase = true) || it.equals("message", ignoreCase = true) } ->
                "send_message"
            tokens.any { it.equals("search", ignoreCase = true) || it.equals("find", ignoreCase = true) } ->
                "search"
            else -> "general"
        }

        return ModelOutput.Success("rule:intent=$intentHint;tokens=${tokens.size}")
    }

    companion object {
        private val WHITESPACE_REGEX = Regex("\\s+")
    }
}

/** Tier 2 — full inference fallback when ONNX model is unavailable. */
class LightweightInferenceModel : InferenceModel {
    override val tier: InferenceTier = InferenceTier.FULL
    override val modelId: String = "lightweight-v1"
    override val capabilities: Set<String> = setOf("general_inference", "summarization", "reasoning_hint")

    override suspend fun infer(input: ModelInput): ModelOutput {
        val trimmed = input.prompt.trim()
        if (trimmed.isBlank()) {
            return ModelOutput.Failure(
                RuntimeError(
                    code = "AIE_EMPTY_PROMPT",
                    category = ErrorCategory.VALIDATION,
                    severity = ErrorSeverity.LOW,
                    recoverable = true,
                    userVisibleMessage = "Cannot infer on an empty prompt.",
                ),
            )
        }

        val summary = trimmed
            .split(WHITESPACE_REGEX)
            .take(SUMMARY_TOKEN_LIMIT)
            .joinToString(" ")

        return ModelOutput.Success("full:processed=${summary.length};preview=${summary.take(PREVIEW_LIMIT)}")
    }

    companion object {
        private val WHITESPACE_REGEX = Regex("\\s+")
        private const val SUMMARY_TOKEN_LIMIT = 24
        private const val PREVIEW_LIMIT = 48
    }
}
