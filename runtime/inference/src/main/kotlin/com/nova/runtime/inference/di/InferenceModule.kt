package com.nova.runtime.inference.di

import com.nova.runtime.inference.AdaptiveInferenceEngine
import com.nova.runtime.inference.AdaptiveInferenceEngineImpl
import com.nova.runtime.inference.budget.ContextBudgetManager
import com.nova.runtime.inference.budget.DefaultContextBudgetManager
import com.nova.runtime.inference.complexity.ComplexityAnalyzer
import com.nova.runtime.inference.complexity.DefaultComplexityAnalyzer
import com.nova.runtime.inference.events.InferenceEventPublisher
import com.nova.runtime.inference.metrics.DefaultInferenceMetrics
import com.nova.runtime.inference.metrics.InferenceMetrics
import com.nova.runtime.inference.model.DeterministicInferenceModel
import com.nova.runtime.inference.model.LightweightInferenceModel
import com.nova.runtime.inference.model.RuleEngineInferenceModel
import com.nova.runtime.inference.prompt.DefaultPromptManager
import com.nova.runtime.inference.prompt.PromptManager
import com.nova.runtime.inference.registry.DefaultModelRegistry
import com.nova.runtime.inference.registry.ModelRegistry
import com.nova.runtime.inference.resource.DefaultResourceAdvisor
import com.nova.runtime.inference.resource.ResourceAdvisor
import com.nova.runtime.inference.scheduler.DefaultInferenceScheduler
import com.nova.runtime.inference.scheduler.InferenceScheduler
import org.koin.dsl.module

/** Koin DI wiring for Adaptive Inference Engine per MSP §5. */
val inferenceModule = module {
    single<ComplexityAnalyzer> { DefaultComplexityAnalyzer() }
    single<PromptManager> { DefaultPromptManager() }
    single<ContextBudgetManager> { DefaultContextBudgetManager() }
    single<InferenceScheduler> { DefaultInferenceScheduler() }
    single<InferenceMetrics> { DefaultInferenceMetrics() }
    single<ResourceAdvisor> { DefaultResourceAdvisor() }
    single { InferenceEventPublisher(get()) }
    single<ModelRegistry> {
        DefaultModelRegistry(
            initialModels = listOf(
                DeterministicInferenceModel(),
                RuleEngineInferenceModel(),
                LightweightInferenceModel(),
            ),
        )
    }
    single<AdaptiveInferenceEngine> {
        AdaptiveInferenceEngineImpl(
            complexityAnalyzer = get(),
            modelRegistry = get(),
            promptManager = get(),
            contextBudgetManager = get(),
            scheduler = get(),
            metrics = get(),
            eventPublisher = get(),
            resourceAdvisor = get(),
            logger = get(),
        )
    }
}
