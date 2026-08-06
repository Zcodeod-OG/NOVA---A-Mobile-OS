package com.nova.runtime.understanding.di

import com.nova.runtime.understanding.SemanticUnderstandingPipeline
import com.nova.runtime.understanding.SemanticUnderstandingPipelineImpl
import com.nova.runtime.understanding.constraint.ConstraintExtractor
import com.nova.runtime.understanding.constraint.PlaceholderConstraintExtractor
import com.nova.runtime.understanding.entity.EntityExtractor
import com.nova.runtime.understanding.entity.PlaceholderEntityExtractor
import com.nova.runtime.understanding.events.UnderstandingEventPublisher
import com.nova.runtime.understanding.intent.IntentClassifier
import com.nova.runtime.understanding.intent.PlaceholderIntentClassifier
import com.nova.runtime.understanding.nir.DefaultNirGenerator
import com.nova.runtime.understanding.nir.NirGenerator
import com.nova.runtime.understanding.normalization.DefaultObservationNormalizer
import com.nova.runtime.understanding.normalization.ObservationNormalizer
import com.nova.runtime.understanding.routing.InferenceRoutingHook
import com.nova.runtime.understanding.routing.StubInferenceRoutingHook
import com.nova.runtime.understanding.validation.DefaultNirValidator
import com.nova.runtime.understanding.validation.NirValidator
import org.koin.dsl.module

/** Koin DI wiring for Semantic Understanding Pipeline per MSP §4. */
val understandingModule = module {
    single<ObservationNormalizer> { DefaultObservationNormalizer() }
    single<InferenceRoutingHook> { StubInferenceRoutingHook() }
    single<EntityExtractor> { PlaceholderEntityExtractor() }
    single<IntentClassifier> { PlaceholderIntentClassifier() }
    single<ConstraintExtractor> { PlaceholderConstraintExtractor() }
    single<NirGenerator> { DefaultNirGenerator() }
    single<NirValidator> { DefaultNirValidator() }
    single { UnderstandingEventPublisher(get()) }
    single<SemanticUnderstandingPipeline> {
        SemanticUnderstandingPipelineImpl(
            normalizer = get(),
            routingHook = get(),
            entityExtractor = get(),
            intentClassifier = get(),
            constraintExtractor = get(),
            nirGenerator = get(),
            nirValidator = get(),
            eventPublisher = get(),
            inferenceEngine = get(),
            traceContextHolder = get(),
            logger = get(),
        )
    }
}
