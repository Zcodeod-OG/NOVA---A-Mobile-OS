package com.nova.runtime.understanding

import com.nova.runtime.inference.AdaptiveInferenceEngine
import com.nova.runtime.kernel.trace.TraceContext
import com.nova.runtime.kernel.trace.TraceContextHolder
import com.nova.runtime.models.Nir
import com.nova.runtime.models.Observation
import com.nova.runtime.models.RuntimeModule
import com.nova.runtime.models.contracts.InferenceRequest
import com.nova.runtime.models.contracts.InferenceResult
import com.nova.runtime.understanding.constraint.ConstraintExtractor
import com.nova.runtime.understanding.entity.EntityExtractor
import com.nova.runtime.understanding.events.UnderstandingEventPublisher
import com.nova.runtime.understanding.intent.IntentClassifier
import com.nova.runtime.understanding.nir.NirGenerator
import com.nova.runtime.understanding.normalization.NormalizedObservation
import com.nova.runtime.understanding.normalization.ObservationNormalizer
import com.nova.runtime.understanding.routing.InferenceRoutingHook
import com.nova.runtime.understanding.validation.NirValidator
import com.nova.runtime.utils.logging.NovaLogger

/**
 * SUP implementation — TDD §6 stages from normalization through validated NIR generation.
 */
class SemanticUnderstandingPipelineImpl(
    private val normalizer: ObservationNormalizer,
    private val routingHook: InferenceRoutingHook,
    private val entityExtractor: EntityExtractor,
    private val intentClassifier: IntentClassifier,
    private val constraintExtractor: ConstraintExtractor,
    private val nirGenerator: NirGenerator,
    private val nirValidator: NirValidator,
    private val eventPublisher: UnderstandingEventPublisher,
    private val inferenceEngine: AdaptiveInferenceEngine,
    private val traceContextHolder: TraceContextHolder,
    private val logger: NovaLogger,
) : SemanticUnderstandingPipeline {

    override suspend fun process(observation: Observation): Nir? {
        val previousTrace = traceContextHolder.current()
        traceContextHolder.set(TraceContext(traceId = observation.traceId, correlationId = observation.id))

        return try {
            processInternal(observation)
        } finally {
            if (previousTrace == null) {
                traceContextHolder.clear()
            } else {
                traceContextHolder.set(previousTrace)
            }
        }
    }

    private suspend fun processInternal(observation: Observation): Nir? {
        logger.info(
            RuntimeModule.UNDERSTANDING.name,
            "Processing observation ${observation.id}",
            observation.traceId,
        )

        val normalized = normalizer.normalize(observation)
        eventPublisher.publishObservationNormalized(normalized)

        if (normalized.normalizedPayload.isBlank()) {
            logger.info(
                RuntimeModule.UNDERSTANDING.name,
                "Skipping NIR generation for blank observation",
                observation.traceId,
            )
            return null
        }

        val routeDecision = routingHook.route(normalized)
        logger.debug(
            RuntimeModule.UNDERSTANDING.name,
            "Routing to tier ${routeDecision.tier}: ${routeDecision.reason}",
            observation.traceId,
        )

        if (!routeDecision.useDeterministicPath) {
            invokeInferencePlaceholder(normalized, routeDecision.tier, observation.traceId)
        }

        val entities = entityExtractor.extract(normalized)
        eventPublisher.publishEntityResolved(entities, observation)

        val intent = intentClassifier.classify(normalized)
        eventPublisher.publishIntentDetected(intent, observation)

        val constraints = constraintExtractor.extract(normalized, entities)
        eventPublisher.publishConstraintExtracted(constraints, observation)

        val nir = nirGenerator.generate(
            normalized = normalized,
            intent = intent,
            entities = entities,
            constraints = constraints,
            routeDecision = routeDecision,
        )

        val validation = nirValidator.validate(nir)
        if (!validation.isValid) {
            logger.error(
                RuntimeModule.UNDERSTANDING.name,
                "NIR validation failed: ${validation.errors.joinToString()}",
                observation.traceId,
            )
            return null
        }

        eventPublisher.publishNirGenerated(nir, observation)
        logger.info(
            RuntimeModule.UNDERSTANDING.name,
            "NIR generated for goal '${nir.goal}'",
            observation.traceId,
        )
        return nir
    }

    private suspend fun invokeInferencePlaceholder(
        normalized: NormalizedObservation,
        tier: Int,
        traceId: java.util.UUID,
    ) {
        when (
            val result = inferenceEngine.infer(
                InferenceRequest(
                    prompt = normalized.normalizedPayload,
                    tierHint = tier,
                    traceId = traceId.toString(),
                ),
            )
        ) {
            is InferenceResult.Success -> logger.debug(
                RuntimeModule.UNDERSTANDING.name,
                "AIE placeholder returned tier ${result.tierUsed}",
                traceId,
            )
            is InferenceResult.Failure -> logger.debug(
                RuntimeModule.UNDERSTANDING.name,
                "AIE placeholder unavailable: ${result.error.code}",
                traceId,
            )
        }
    }
}
