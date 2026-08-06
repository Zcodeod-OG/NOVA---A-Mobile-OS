package com.nova.runtime.app.di

import com.nova.runtime.android.AndroidAdapterLayer
import com.nova.runtime.android.AndroidAdapterLayerStub
import com.nova.runtime.capability.CapabilityFramework
import com.nova.runtime.capability.CapabilityFrameworkStub
import com.nova.runtime.app.conversation.SessionRepositoryPersistence
import com.nova.runtime.conversation.di.conversationModule
import com.nova.runtime.conversation.session.SessionPersistence
import com.nova.runtime.execution.ExecutionRuntime
import com.nova.runtime.execution.ExecutionRuntimeStub
import com.nova.runtime.inference.AdaptiveInferenceEngine
import com.nova.runtime.inference.AdaptiveInferenceEngineStub
import com.nova.runtime.events.InMemoryEventBus
import com.nova.runtime.kernel.RuntimeKernel
import com.nova.runtime.kernel.di.kernelModule
import com.nova.runtime.memory.MemoryPlatform
import com.nova.runtime.memory.MemoryPlatformStub
import com.nova.runtime.planner.PlanningService
import com.nova.runtime.planner.PlanningServiceStub
import com.nova.runtime.policy.PolicyEngine
import com.nova.runtime.policy.PolicyEngineStub
import com.nova.runtime.reasoning.ReasoningEngine
import com.nova.runtime.reasoning.ReasoningEngineStub
import com.nova.runtime.storage.di.storageModule
import com.nova.runtime.understanding.di.understandingModule
import org.koin.dsl.module

/** Sprint 0 service stubs wired alongside Sprint 1 kernel infrastructure. */
val sprint0StubsModule = module {
    single<AdaptiveInferenceEngine> { AdaptiveInferenceEngineStub() }
    single<MemoryPlatform> { MemoryPlatformStub(get()) }
    single<ReasoningEngine> { ReasoningEngineStub() }
    single<PlanningService> { PlanningServiceStub() }
    single<PolicyEngine> { PolicyEngineStub() }
    single<CapabilityFramework> { CapabilityFrameworkStub() }
    single<ExecutionRuntime> { ExecutionRuntimeStub() }
    single<AndroidAdapterLayer> { AndroidAdapterLayerStub() }
}

val conversationPersistenceModule = module {
    single<SessionPersistence> { SessionRepositoryPersistence(get()) }
}

val runtimeModule = module {
    includes(kernelModule, sprint0StubsModule, understandingModule, conversationModule, conversationPersistenceModule)
    single {
        RuntimeKernel(
            serviceRegistry = get(),
            eventBus = get<InMemoryEventBus>(),
            lifecycleManager = get(),
            configurationManager = get(),
            moduleRegistry = get(),
            traceContextHolder = get(),
            logger = get(),
        )
    }
}
