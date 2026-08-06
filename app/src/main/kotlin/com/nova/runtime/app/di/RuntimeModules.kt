package com.nova.runtime.app.di

import com.nova.runtime.android.di.androidAdapterModule
import com.nova.runtime.capability.di.capabilityModule
import com.nova.runtime.app.conversation.SessionRepositoryPersistence
import com.nova.runtime.conversation.di.conversationModule
import com.nova.runtime.conversation.session.SessionPersistence
import com.nova.runtime.app.execution.StorageExecutionHistoryRecorder
import com.nova.runtime.execution.ExecutionRuntime
import com.nova.runtime.execution.di.executionModule
import com.nova.runtime.execution.history.ExecutionHistoryRecorder
import com.nova.runtime.inference.di.inferenceModule
import com.nova.runtime.events.InMemoryEventBus
import com.nova.runtime.kernel.RuntimeKernel
import com.nova.runtime.kernel.di.kernelModule
import com.nova.runtime.memory.MemoryPlatform
import com.nova.runtime.memory.MemoryPlatformStub
import com.nova.runtime.planner.di.plannerModule
import com.nova.runtime.policy.di.policyModule
import com.nova.runtime.reasoning.di.reasoningModule
import com.nova.runtime.storage.di.storageModule
import com.nova.runtime.understanding.di.understandingModule
import org.koin.dsl.module

/** Sprint 0 service stubs wired alongside Sprint 1 kernel infrastructure. */
val sprint0StubsModule = module {
    single<MemoryPlatform> { MemoryPlatformStub(get()) }
}

val conversationPersistenceModule = module {
    single<SessionPersistence> { SessionRepositoryPersistence(get()) }
}

/** Overrides execution history persistence when storage module is available. */
val executionPersistenceModule = module {
    single<ExecutionHistoryRecorder> { StorageExecutionHistoryRecorder(get()) }
}

val runtimeModule = module {
    includes(
        kernelModule,
        inferenceModule,
        reasoningModule,
        plannerModule,
        policyModule,
        capabilityModule,
        androidAdapterModule,
        executionModule,
        sprint0StubsModule,
        understandingModule,
        conversationModule,
        conversationPersistenceModule,
    )
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
