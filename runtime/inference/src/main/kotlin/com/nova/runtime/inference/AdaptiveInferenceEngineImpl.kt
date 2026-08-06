package com.nova.runtime.inference

import com.nova.runtime.inference.budget.ContextBudgetManager
import com.nova.runtime.inference.complexity.ComplexityAnalyzer
import com.nova.runtime.inference.events.InferenceEventPublisher
import com.nova.runtime.inference.metrics.InferenceMetrics
import com.nova.runtime.inference.model.ModelInput
import com.nova.runtime.inference.model.ModelOutput
import com.nova.runtime.inference.prompt.DefaultPromptManager
import com.nova.runtime.inference.prompt.PromptManager
import com.nova.runtime.inference.registry.ModelRegistry
import com.nova.runtime.inference.resource.DeviceResourceSnapshot
import com.nova.runtime.inference.resource.ResourceAdvisor
import com.nova.runtime.inference.scheduler.InferencePriority
import com.nova.runtime.inference.scheduler.InferenceScheduler
import com.nova.runtime.inference.tier.InferenceTier
import com.nova.runtime.models.ErrorCategory
import com.nova.runtime.models.ErrorSeverity
import com.nova.runtime.models.RuntimeError
import com.nova.runtime.models.RuntimeModule
import com.nova.runtime.models.contracts.InferenceRequest
import com.nova.runtime.models.contracts.InferenceResult
import com.nova.runtime.utils.logging.NovaLogger
import java.util.UUID

/**
 * Adaptive Inference Engine — MSP §5 orchestration across analyzer, registry,
 * prompt assembly, context budgeting, scheduling, and tiered placeholder models.
 */
class AdaptiveInferenceEngineImpl(
    private val complexityAnalyzer: ComplexityAnalyzer,
    private val modelRegistry: ModelRegistry,
    private val promptManager: PromptManager,
    private val contextBudgetManager: ContextBudgetManager,
    private val scheduler: InferenceScheduler,
    private val metrics: InferenceMetrics,
    private val eventPublisher: InferenceEventPublisher,
    private val resourceAdvisor: ResourceAdvisor,
    private val deviceResources: () -> DeviceResourceSnapshot = { DeviceResourceSnapshot() },
    private val logger: NovaLogger,
) : AdaptiveInferenceEngine {

    override suspend fun infer(request: InferenceRequest): InferenceResult {
        val traceId = parseTraceId(request.traceId)
        val startedAtMs = metrics.recordStart(request.traceId)

        logger.debug(
            RuntimeModule.INFERENCE.name,
            "Inference requested (${request.prompt.length} chars)",
            traceId,
        )
        eventPublisher.publishStarted(traceId, request.prompt.length)

        return scheduler.schedule(InferencePriority.NORMAL) {
            executeInference(request, traceId, startedAtMs)
        }
    }

    private suspend fun executeInference(
        request: InferenceRequest,
        traceId: UUID,
        startedAtMs: Long,
    ): InferenceResult {
        val analysis = complexityAnalyzer.analyze(request)
        val selectedTier = resourceAdvisor.adviseTier(
            requested = analysis.recommendedTier,
            resources = deviceResources(),
        )

        eventPublisher.publishTierSelected(traceId, selectedTier, analysis.reason)
        logger.debug(
            RuntimeModule.INFERENCE.name,
            "Selected tier ${selectedTier.level} (${analysis.reason})",
            traceId,
        )

        val model = modelRegistry.lookup(selectedTier)
        if (model == null) {
            val error = RuntimeError(
                code = "AIE_MODEL_NOT_FOUND",
                category = ErrorCategory.INFRASTRUCTURE,
                severity = ErrorSeverity.MEDIUM,
                recoverable = true,
                userVisibleMessage = "No inference model registered for tier ${selectedTier.level}.",
                diagnostics = mapOf("tier" to selectedTier.level.toString()),
            )
            recordFailure(request.traceId, traceId, selectedTier, startedAtMs, error)
            return InferenceResult.Failure(error)
        }

        val assembledPrompt = promptManager.assemble(
            templateId = DefaultPromptManager.DEFAULT_TEMPLATE_ID,
            userContent = request.prompt,
            tier = selectedTier,
            variables = mapOf("tier" to selectedTier.label),
        )
        val trimmedContext = contextBudgetManager.trim(assembledPrompt.text, selectedTier)

        return when (
            val output = model.infer(
                ModelInput(
                    prompt = trimmedContext.text,
                    traceId = request.traceId,
                    metadata = mapOf(
                        "userContent" to request.prompt.trim(),
                        "modelId" to model.modelId,
                        "templateId" to assembledPrompt.templateId,
                        "wasTrimmed" to trimmedContext.wasTrimmed.toString(),
                    ),
                ),
            )
        ) {
            is ModelOutput.Success -> {
                val latencyMs = (System.currentTimeMillis() - startedAtMs).coerceAtLeast(0)
                metrics.recordSuccess(request.traceId, selectedTier, startedAtMs)
                eventPublisher.publishCompleted(traceId, selectedTier, latencyMs)
                logger.info(
                    RuntimeModule.INFERENCE.name,
                    "Inference completed on tier ${selectedTier.level} via ${model.modelId}",
                    traceId,
                    durationMs = latencyMs,
                    metadata = mapOf("tier" to selectedTier.level.toString()),
                )
                InferenceResult.Success(output = output.text, tierUsed = selectedTier.level)
            }
            is ModelOutput.Failure -> {
                recordFailure(request.traceId, traceId, selectedTier, startedAtMs, output.error)
                InferenceResult.Failure(output.error)
            }
        }
    }

    private suspend fun recordFailure(
        traceIdString: String,
        traceId: UUID,
        tier: InferenceTier,
        startedAtMs: Long,
        error: RuntimeError,
    ) {
        metrics.recordFailure(traceIdString, tier, startedAtMs)
        eventPublisher.publishFailed(traceId, error.code, tier)
        logger.warn(
            RuntimeModule.INFERENCE.name,
            "Inference failed: ${error.code}",
            traceId,
            metadata = mapOf("tier" to tier.level.toString()),
        )
    }

    private fun parseTraceId(traceId: String): UUID =
        runCatching { UUID.fromString(traceId) }.getOrElse { UUID.randomUUID() }
}
