package com.nova.runtime.execution

import com.nova.runtime.execution.events.ExecutionEventPublisher
import com.nova.runtime.execution.history.ExecutionHistoryRecorder
import com.nova.runtime.execution.metrics.ExecutionMetrics
import com.nova.runtime.execution.model.ExecutionConfig
import com.nova.runtime.execution.monitor.ExecutionMonitor
import com.nova.runtime.execution.queue.QueueManager
import com.nova.runtime.execution.retry.RetryManager
import com.nova.runtime.execution.rollback.RollbackManager
import com.nova.runtime.execution.scheduler.ActiveGraphExecution
import com.nova.runtime.execution.scheduler.DependencyResolver
import com.nova.runtime.execution.scheduler.ExecutionScheduler
import com.nova.runtime.execution.worker.ActionExecutor
import com.nova.runtime.models.ErrorCategory
import com.nova.runtime.models.ErrorSeverity
import com.nova.runtime.models.RuntimeError
import com.nova.runtime.models.RuntimeModule
import com.nova.runtime.models.contracts.ActionPolicyGate
import com.nova.runtime.models.contracts.ExecutionRequest
import com.nova.runtime.models.contracts.ExecutionResult
import com.nova.runtime.utils.logging.NovaLogger
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Execution Runtime — TDD §13 / MSP §9.
 * Orchestrates DAG execution via scheduler, worker pool, retry, and rollback.
 */
class ExecutionRuntimeImpl(
    private val schedulerFactory: () -> ExecutionScheduler,
    private val eventPublisher: ExecutionEventPublisher,
    private val logger: NovaLogger,
) : ExecutionRuntime {

    private val activeExecutions = ConcurrentHashMap<String, ActiveGraphExecution>()
    private val controlMutex = Mutex()

    override suspend fun execute(request: ExecutionRequest): ExecutionResult {
        val graphId = request.graph.graphId.toString()
        val executionId = UUID.randomUUID()

        val active = ActiveGraphExecution(
            graphId = request.graph.graphId,
            traceId = request.traceId,
            executionId = executionId,
        )

        controlMutex.withLock {
            if (activeExecutions.containsKey(graphId)) {
                return ExecutionResult.Failure(
                    RuntimeError(
                        code = "EXECUTION_ALREADY_RUNNING",
                        category = ErrorCategory.EXECUTION,
                        severity = ErrorSeverity.MEDIUM,
                        recoverable = true,
                        userVisibleMessage = "Graph execution is already in progress.",
                    ),
                )
            }
            activeExecutions[graphId] = active
        }

        return try {
            schedulerFactory().execute(
                request = request,
                executionId = executionId,
                pauseFlag = active.pauseFlag,
                cancelFlag = active.cancelFlag,
            )
        } finally {
            activeExecutions.remove(graphId)
        }
    }

    override suspend fun pause(graphId: String) {
        controlMutex.withLock {
            val active = activeExecutions[graphId] ?: return
            active.pauseFlag.set(true)
            eventPublisher.publishGraphPaused(active.traceId, active.graphId)
            logger.info(RuntimeModule.EXECUTION.name, "Execution paused for graph $graphId", active.traceId)
        }
    }

    override suspend fun resume(graphId: String) {
        controlMutex.withLock {
            val active = activeExecutions[graphId] ?: return
            active.pauseFlag.set(false)
            eventPublisher.publishGraphResumed(active.traceId, active.graphId)
            logger.info(RuntimeModule.EXECUTION.name, "Execution resumed for graph $graphId", active.traceId)
        }
    }

    override suspend fun cancel(graphId: String) {
        controlMutex.withLock {
            val active = activeExecutions[graphId] ?: return
            active.cancelFlag.set(true)
            logger.info(RuntimeModule.EXECUTION.name, "Execution cancel requested for graph $graphId", active.traceId)
        }
    }
}

class ExecutionRuntimeFactory(
    private val dependencyResolver: DependencyResolver,
    private val queueManager: QueueManager,
    private val retryManager: RetryManager,
    private val rollbackManager: RollbackManager,
    private val monitor: ExecutionMonitor,
    private val metrics: ExecutionMetrics,
    private val eventPublisher: ExecutionEventPublisher,
    private val actionExecutor: ActionExecutor,
    private val actionPolicyGate: ActionPolicyGate,
    private val historyRecorder: ExecutionHistoryRecorder,
    private val logger: NovaLogger,
    private val config: ExecutionConfig,
) {
    fun createScheduler(): ExecutionScheduler =
        ExecutionScheduler(
            dependencyResolver = dependencyResolver,
            queueManager = queueManager,
            retryManager = retryManager,
            rollbackManager = rollbackManager,
            monitor = monitor,
            metrics = metrics,
            eventPublisher = eventPublisher,
            actionExecutor = actionExecutor,
            actionPolicyGate = actionPolicyGate,
            historyRecorder = historyRecorder,
            logger = logger,
            config = config,
        )
}
