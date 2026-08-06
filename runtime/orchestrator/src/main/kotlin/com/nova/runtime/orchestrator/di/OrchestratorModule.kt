package com.nova.runtime.orchestrator.di

import com.nova.runtime.orchestrator.CognitivePipelineOrchestrator
import org.koin.dsl.module

val orchestratorModule = module {
    single {
        CognitivePipelineOrchestrator(
            understandingPipeline = get(),
            reasoningEngine = get(),
            planningService = get(),
            policyEngine = get(),
            executionRuntime = get(),
            memoryPlatform = get(),
            logger = get(),
        )
    }
}
