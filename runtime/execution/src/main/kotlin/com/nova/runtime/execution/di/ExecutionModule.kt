package com.nova.runtime.execution.di

import com.nova.runtime.capability.CapabilityFramework
import com.nova.runtime.execution.ExecutionRuntime
import com.nova.runtime.execution.ExecutionRuntimeFactory
import com.nova.runtime.execution.ExecutionRuntimeImpl
import com.nova.runtime.execution.events.ExecutionEventPublisher
import com.nova.runtime.execution.history.ExecutionHistoryRecorder
import com.nova.runtime.execution.history.NoOpExecutionHistoryRecorder
import com.nova.runtime.execution.metrics.ExecutionMetrics
import com.nova.runtime.execution.model.ExecutionConfig
import com.nova.runtime.execution.monitor.DefaultExecutionMonitor
import com.nova.runtime.execution.monitor.ExecutionMonitor
import com.nova.runtime.execution.queue.DefaultQueueManager
import com.nova.runtime.execution.queue.QueueManager
import com.nova.runtime.execution.retry.DefaultRetryManager
import com.nova.runtime.execution.retry.RetryManager
import com.nova.runtime.execution.rollback.DefaultRollbackManager
import com.nova.runtime.execution.rollback.RollbackManager
import com.nova.runtime.execution.scheduler.DefaultDependencyResolver
import com.nova.runtime.execution.scheduler.DependencyResolver
import com.nova.runtime.execution.worker.ActionExecutor
import com.nova.runtime.execution.worker.ChainingCapabilityActionExecutor
import com.nova.runtime.models.contracts.ActionPolicyGate
import org.koin.dsl.module

/** Koin DI wiring for Execution Runtime per MSP §9. */
val executionModule = module {
    single<DependencyResolver> { DefaultDependencyResolver() }
    single<QueueManager> { DefaultQueueManager() }
    single<RetryManager> {
        DefaultRetryManager(
            defaultMaxRetries = get<ExecutionConfig>().defaultMaxRetries,
            defaultBackoffMs = get<ExecutionConfig>().defaultBackoffMs,
        )
    }
    single<RollbackManager> { DefaultRollbackManager() }
    single<ExecutionMonitor> { DefaultExecutionMonitor() }
    single { ExecutionMetrics() }
    single { ExecutionConfig() }
    single<ExecutionHistoryRecorder> { NoOpExecutionHistoryRecorder() }
    single { ExecutionEventPublisher(get()) }
    single<ActionExecutor> { ChainingCapabilityActionExecutor(get<CapabilityFramework>()) }
    single {
        ExecutionRuntimeFactory(
            dependencyResolver = get(),
            queueManager = get(),
            retryManager = get(),
            monitor = get(),
            metrics = get(),
            eventPublisher = get(),
            actionExecutor = get<ActionExecutor>(),
            actionPolicyGate = get<ActionPolicyGate>(),
            historyRecorder = get(),
            rollbackManager = get(),
            logger = get(),
            config = get(),
        )
    }
    single<ExecutionRuntime> {
        ExecutionRuntimeImpl(
            schedulerFactory = { get<ExecutionRuntimeFactory>().createScheduler() },
            eventPublisher = get(),
            logger = get(),
        )
    }
}
