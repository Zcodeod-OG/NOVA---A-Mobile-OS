package com.nova.runtime.reasoning.di

import com.nova.runtime.reasoning.ReasoningEngine
import com.nova.runtime.reasoning.ReasoningEngineImpl
import com.nova.runtime.reasoning.ambiguity.AmbiguityResolver
import com.nova.runtime.reasoning.ambiguity.DefaultAmbiguityResolver
import com.nova.runtime.reasoning.confidence.ConfidenceScorer
import com.nova.runtime.reasoning.confidence.DefaultConfidenceScorer
import com.nova.runtime.reasoning.context.DefaultReasoningContextBuilder
import com.nova.runtime.reasoning.context.ReasoningContextBuilder
import com.nova.runtime.reasoning.events.ReasoningEventPublisher
import com.nova.runtime.reasoning.evidence.DefaultEvidenceRanker
import com.nova.runtime.reasoning.evidence.EvidenceRanker
import com.nova.runtime.reasoning.explanation.DefaultExplanationGenerator
import com.nova.runtime.reasoning.explanation.ExplanationGenerator
import org.koin.dsl.module

/** Koin DI wiring for Reasoning Engine per MSP §7. */
val reasoningModule = module {
    single<EvidenceRanker> { DefaultEvidenceRanker() }
    single<AmbiguityResolver> { DefaultAmbiguityResolver() }
    single<ConfidenceScorer> { DefaultConfidenceScorer() }
    single<ReasoningContextBuilder> { DefaultReasoningContextBuilder() }
    single<ExplanationGenerator> { DefaultExplanationGenerator() }
    single { ReasoningEventPublisher(get()) }
    single<ReasoningEngine> {
        ReasoningEngineImpl(
            evidenceRanker = get(),
            ambiguityResolver = get(),
            confidenceScorer = get(),
            contextBuilder = get(),
            explanationGenerator = get(),
            eventPublisher = get(),
            logger = get(),
        )
    }
}
