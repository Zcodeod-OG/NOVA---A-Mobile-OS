package com.nova.runtime.reasoning

import com.nova.runtime.models.RuntimeModule
import com.nova.runtime.models.contracts.MemoryResult
import com.nova.runtime.models.contracts.ReasoningEngineResult
import com.nova.runtime.models.contracts.ReasoningRequest
import com.nova.runtime.reasoning.ambiguity.AmbiguityResolver
import com.nova.runtime.reasoning.confidence.ConfidenceScorer
import com.nova.runtime.reasoning.context.ReasoningContextBuilder
import com.nova.runtime.reasoning.events.ReasoningEventPublisher
import com.nova.runtime.reasoning.evidence.EvidenceRanker
import com.nova.runtime.reasoning.explanation.ExplanationGenerator
import com.nova.runtime.utils.logging.NovaLogger

/**
 * Reasoning Engine — TDD §10 / MSP §7.
 * Resolves ambiguity, ranks evidence, and produces an explainable ReasoningContext.
 */
class ReasoningEngineImpl(
    private val evidenceRanker: EvidenceRanker,
    private val ambiguityResolver: AmbiguityResolver,
    private val confidenceScorer: ConfidenceScorer,
    private val contextBuilder: ReasoningContextBuilder,
    private val explanationGenerator: ExplanationGenerator,
    private val eventPublisher: ReasoningEventPublisher,
    private val logger: NovaLogger,
) : ReasoningEngine {

    override suspend fun reason(request: ReasoningRequest): ReasoningEngineResult {
        val traceId = request.traceId
        val nir = request.nir

        logger.info(
            RuntimeModule.REASONING.name,
            "Reasoning started for goal '${nir.goal}'",
            traceId,
        )
        eventPublisher.publishStarted(traceId, nir.goal)

        return when (val memoryResults = request.memoryResults) {
            is MemoryResult.Failure -> {
                logger.warn(
                    RuntimeModule.REASONING.name,
                    "Memory unavailable: ${memoryResults.error.code}",
                    traceId,
                )
                ReasoningEngineResult.Failure(memoryResults.error)
            }
            is MemoryResult.Success -> executeReasoning(request, memoryResults.entries)
        }
    }

    private suspend fun executeReasoning(
        request: ReasoningRequest,
        memoryEntries: List<Map<String, String>>,
    ): ReasoningEngineResult {
        val traceId = request.traceId
        val nir = request.nir

        val rankedEvidence = evidenceRanker.rank(nir, memoryEntries)
        eventPublisher.publishEvidenceCollected(
            traceId = traceId,
            evidenceCount = rankedEvidence.size,
            topSource = rankedEvidence.firstOrNull()?.source,
        )
        logger.debug(
            RuntimeModule.REASONING.name,
            "Ranked ${rankedEvidence.size} evidence item(s)",
            traceId,
        )

        val resolution = ambiguityResolver.resolve(nir, rankedEvidence)
        eventPublisher.publishAmbiguityResolved(
            traceId = traceId,
            resolvedCount = resolution.resolvedCount,
            assumptionCount = resolution.assumptionCount,
        )

        val confidence = confidenceScorer.score(nir, rankedEvidence, resolution)
        val baseContext = contextBuilder.build(nir, rankedEvidence, resolution, confidence)
        val explanations = explanationGenerator.generate(nir, rankedEvidence, resolution, confidence)

        val context = baseContext.copy(
            recommendations = baseContext.recommendations + explanations,
        )

        eventPublisher.publishCompleted(traceId, context.confidence, rankedEvidence.size)
        logger.info(
            RuntimeModule.REASONING.name,
            "Reasoning completed with confidence ${context.confidence}",
            traceId,
            metadata = mapOf("evidenceCount" to rankedEvidence.size.toString()),
        )

        return ReasoningEngineResult.Success(context)
    }
}
