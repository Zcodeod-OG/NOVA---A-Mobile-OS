package com.nova.runtime.orchestrator.di

import com.nova.runtime.orchestrator.CognitivePipelineOrchestrator
import com.nova.runtime.orchestrator.calendar.NoOpCalendarIntentSupport
import org.koin.dsl.module

val orchestratorModule = module {
    single<com.nova.runtime.orchestrator.calendar.CalendarIntentSupport> { NoOpCalendarIntentSupport }
    single {
        CognitivePipelineOrchestrator(
            understandingPipeline = get(),
            reasoningEngine = get(),
            planningService = get(),
            policyEngine = get(),
            executionRuntime = get(),
            memoryPlatform = get(),
            calendarIntentSupport = get(),
            logger = get(),
        )
    }
}
