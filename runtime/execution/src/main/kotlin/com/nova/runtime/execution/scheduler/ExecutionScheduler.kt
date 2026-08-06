package com.nova.runtime.execution.scheduler

import com.nova.runtime.execution.events.ExecutionEventPublisher
import com.nova.runtime.execution.history.ExecutionHistoryRecorder
import com.nova.runtime.execution.lifecycle.ExecutionNodeState
import com.nova.runtime.execution.lifecycle.GraphExecutionStatus
import com.nova.runtime.execution.metrics.ExecutionMetrics
import com.nova.runtime.execution.model.ExecutionConfig
import com.nova.runtime.execution.monitor.ExecutionMonitor
import com.nova.runtime.execution.queue.QueueManager
import com.nova.runtime.execution.retry.RetryManager
import com.nova.runtime.execution.rollback.RollbackManager
import com.nova.runtime.execution.worker.ActionExecutor
import com.nova.runtime.execution.worker.NodeExecutionOutcome
import com.nova.runtime.execution.worker.WorkerPool
import com.nova.runtime.models.ActionNode
import com.nova.runtime.models.ErrorCategory
import com.nova.runtime.models.ErrorSeverity
import com.nova.runtime.models.Nag
import com.nova.runtime.models.RuntimeError
import com.nova.runtime.models.RuntimeModule
import com.nova.runtime.models.contracts.ActionPolicyGate
import com.nova.runtime.models.contracts.ActionPolicyRequest
import com.nova.runtime.models.contracts.PolicyDecisionType
import com.nova.runtime.models.contracts.PolicyEvaluationResult
import com.nova.runtime.models.contracts.ExecutionRequest
import com.nova.runtime.models.contracts.ExecutionResult
import com.nova.runtime.policy.PolicyExecutionErrors
import com.nova.runtime.utils.logging.NovaLogger
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** Schedules and executes action nodes from a NAG respecting DAG dependencies. */
class ExecutionScheduler(
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
    private val config: ExecutionConfig = ExecutionConfig(),
) {
    private val retryAttempts = ConcurrentHashMap<UUID, AtomicInteger>()
    private val scheduledNodes = ConcurrentHashMap.newKeySet<UUID>()

    suspend fun execute(
        request: ExecutionRequest,
        executionId: UUID,
        pauseFlag: AtomicBoolean,
        cancelFlag: AtomicBoolean,
    ): ExecutionResult {
        val graph = request.graph
        val traceId = request.traceId
        val graphId = graph.graphId
        val startNanos = System.nanoTime()

        resetState(graph)
        metrics.beginGraph(graph.actionNodes.size)
        monitor.initializeGraph(graphId, graph.actionNodes.map { it.id })

        logger.info(
            RuntimeModule.EXECUTION.name,
            "Execution started for graph $graphId with ${graph.actionNodes.size} node(s)",
            traceId,
        )
        eventPublisher.publishStarted(traceId, graphId, graph.actionNodes.size)
        historyRecorder.recordStart(executionId, graphId, traceId)

        if (graph.actionNodes.isEmpty()) {
            return completeGraph(traceId, graphId, executionId, startNanos, 0)
        }

        try {
            while (true) {
                if (cancelFlag.get()) {
                    return cancelGraph(traceId, graphId, executionId, startNanos)
                }
                awaitWhilePaused(pauseFlag)

                val ready = dependencyResolver.readyNodes(graph, monitor.nodeStates())
                    .filter { node -> node.id !in scheduledNodes }
                if (ready.isNotEmpty()) {
                    queueManager.enqueue(ready)
                    ready.forEach { node ->
                        scheduledNodes.add(node.id)
                        eventPublisher.publishNodeScheduled(traceId, node.id)
                    }
                }

                val batch = drainQueue()
                if (batch.isEmpty()) {
                    if (isGraphSettled(graph)) break
                    delay(POLL_INTERVAL_MS)
                    continue
                }

                val failed = executeBatch(
                    batch = batch,
                    graph = graph,
                    traceId = traceId,
                    pauseFlag = pauseFlag,
                    cancelFlag = cancelFlag,
                )

                if (failed != null) {
                    val rollbackResult = rollbackManager.rollback(traceId, actionExecutor)
                    rollbackResult.rolledBackNodeIds.forEach { nodeId ->
                        monitor.transition(nodeId, ExecutionNodeState.ROLLED_BACK)
                        eventPublisher.publishNodeRolledBack(traceId, nodeId)
                    }
                    monitor.setGraphStatus(graphId, GraphExecutionStatus.ROLLED_BACK)
                    eventPublisher.publishGraphFailed(traceId, graphId, failed.code)
                    persistHistory(executionId, graphId, traceId, "rolled_back", startNanos)
                    return ExecutionResult.Failure(failed)
                }
            }

            val completed = monitor.completedCount()
            return completeGraph(traceId, graphId, executionId, startNanos, completed)
        } finally {
            rollbackManager.clear()
        retryAttempts.clear()
        scheduledNodes.clear()
        queueManager.clear()
    }
    }

    private suspend fun executeBatch(
        batch: List<ActionNode>,
        graph: Nag,
        traceId: UUID,
        pauseFlag: AtomicBoolean,
        cancelFlag: AtomicBoolean,
    ): RuntimeError? = coroutineScope {
        val workerPool = WorkerPool(config.workerPoolSize, actionExecutor)
        val semaphore = Semaphore(workerPool.withPermits { it })

        val outcomes = batch.map { node ->
            async {
                semaphore.withPermit {
                    executeNode(node, graph, traceId, pauseFlag, cancelFlag)
                }
            }
        }.awaitAll()

        outcomes.filterIsInstance<NodeBatchOutcome.Failed>().firstOrNull()?.error
    }

    private suspend fun executeNode(
        node: ActionNode,
        graph: Nag,
        traceId: UUID,
        pauseFlag: AtomicBoolean,
        cancelFlag: AtomicBoolean,
    ): NodeBatchOutcome {
        if (cancelFlag.get()) {
            monitor.transition(node.id, ExecutionNodeState.CANCELLED)
            return NodeBatchOutcome.Cancelled
        }
        awaitWhilePaused(pauseFlag)

        scheduledNodes.remove(node.id)
        monitor.transition(node.id, ExecutionNodeState.RUNNING)
        eventPublisher.publishNodeRunning(traceId, node.id)

        evaluatePolicy(node, graph, traceId)?.let { return it }

        val attempt = retryAttempts.getOrPut(node.id) { AtomicInteger(0) }.get()
        val startNanos = System.nanoTime()

        when (val outcome = actionExecutor.execute(node, traceId)) {
            is NodeExecutionOutcome.Success -> {
                val latencyMs = (System.nanoTime() - startNanos) / 1_000_000
                monitor.transition(node.id, ExecutionNodeState.COMPLETED)
                metrics.recordNodeSuccess(latencyMs)
                rollbackManager.recordCompletion(node)
                eventPublisher.publishNodeCompleted(traceId, node.id, latencyMs)
                return NodeBatchOutcome.Success
            }
            is NodeExecutionOutcome.Failure -> {
                val latencyMs = (System.nanoTime() - startNanos) / 1_000_000
                metrics.recordNodeFailure(latencyMs)

                if (outcome.retryable && retryManager.shouldRetry(node.retryPolicy, attempt)) {
                    retryAttempts.getValue(node.id).incrementAndGet()
                    metrics.recordRetry()
                    monitor.transition(node.id, ExecutionNodeState.PENDING)
                    val backoff = retryManager.backoffDelayMs(node.retryPolicy, attempt)
                    if (backoff > 0) delay(backoff)
                    scheduledNodes.add(node.id)
                    queueManager.enqueue(listOf(node))
                    eventPublisher.publishNodeScheduled(traceId, node.id)
                    return NodeBatchOutcome.Retrying
                }

                monitor.transition(node.id, ExecutionNodeState.FAILED)
                eventPublisher.publishNodeFailed(traceId, node.id, outcome.error.code)
                return NodeBatchOutcome.Failed(outcome.error)
            }
        }
    }

    private suspend fun evaluatePolicy(
        node: ActionNode,
        graph: Nag,
        traceId: UUID,
    ): NodeBatchOutcome.Failed? {
        when (
            val result = actionPolicyGate.evaluateAction(
                ActionPolicyRequest(
                    node = node,
                    traceId = traceId,
                    graphId = graph.graphId,
                    executionPolicies = graph.executionPolicies,
                ),
            )
        ) {
            is PolicyEvaluationResult.Failure -> {
                monitor.transition(node.id, ExecutionNodeState.FAILED)
                eventPublisher.publishNodeFailed(traceId, node.id, result.error.code)
                return NodeBatchOutcome.Failed(result.error)
            }
            is PolicyEvaluationResult.Success -> {
                val error = when (result.decision.type) {
                    PolicyDecisionType.APPROVED -> return null
                    PolicyDecisionType.REJECTED ->
                        PolicyExecutionErrors.denied(result.decision.rationale)
                    PolicyDecisionType.REQUIRES_USER_CONFIRMATION ->
                        PolicyExecutionErrors.confirmationRequired(result.decision.rationale)
                }
                monitor.transition(node.id, ExecutionNodeState.FAILED)
                eventPublisher.publishNodeFailed(traceId, node.id, error.code)
                return NodeBatchOutcome.Failed(error)
            }
        }
    }

    private suspend fun completeGraph(
        traceId: UUID,
        graphId: UUID,
        executionId: UUID,
        startNanos: Long,
        completedNodes: Int,
    ): ExecutionResult {
        val durationMs = (System.nanoTime() - startNanos) / 1_000_000
        monitor.setGraphStatus(graphId, GraphExecutionStatus.COMPLETED)
        eventPublisher.publishGraphCompleted(traceId, graphId, completedNodes, durationMs)
        persistHistory(executionId, graphId, traceId, "completed", startNanos)
        logger.info(
            RuntimeModule.EXECUTION.name,
            "Execution completed with $completedNodes node(s)",
            traceId,
            durationMs = durationMs,
            metadata = metrics.snapshot().let {
                mapOf(
                    "completedNodes" to it.completedNodes.toString(),
                    "retryCount" to it.retryCount.toString(),
                    "throughput" to "%.2f".format(it.throughputNodesPerSecond),
                )
            },
        )
        return ExecutionResult.Success(completedNodes = completedNodes)
    }

    private suspend fun cancelGraph(
        traceId: UUID,
        graphId: UUID,
        executionId: UUID,
        startNanos: Long,
    ): ExecutionResult {
        monitor.setGraphStatus(graphId, GraphExecutionStatus.CANCELLED)
        eventPublisher.publishGraphCancelled(traceId, graphId)
        persistHistory(executionId, graphId, traceId, "cancelled", startNanos)
        return ExecutionResult.Failure(
            RuntimeError(
                code = "EXECUTION_CANCELLED",
                category = ErrorCategory.EXECUTION,
                severity = ErrorSeverity.LOW,
                recoverable = true,
                userVisibleMessage = "Execution was cancelled.",
            ),
        )
    }

    private fun drainQueue(): List<ActionNode> {
        val batch = mutableListOf<ActionNode>()
        while (true) {
            val node = queueManager.dequeue() ?: break
            batch.add(node)
        }
        return batch
    }

    private fun isGraphSettled(graph: Nag): Boolean {
        val states = monitor.nodeStates()
        return graph.actionNodes.all { node ->
            val state = states[node.id] ?: ExecutionNodeState.PENDING
            state == ExecutionNodeState.COMPLETED ||
                state == ExecutionNodeState.FAILED ||
                state == ExecutionNodeState.ROLLED_BACK ||
                state == ExecutionNodeState.CANCELLED
        }
    }

    private suspend fun awaitWhilePaused(pauseFlag: AtomicBoolean) {
        while (pauseFlag.get() && !Thread.currentThread().isInterrupted) {
            delay(POLL_INTERVAL_MS)
        }
    }

    private suspend fun persistHistory(
        executionId: UUID,
        graphId: UUID,
        traceId: UUID,
        status: String,
        startNanos: Long,
    ) {
        val snapshot = metrics.snapshot()
        historyRecorder.recordUpdate(
            executionId = executionId,
            graphId = graphId,
            traceId = traceId,
            status = status,
            durationMs = (System.nanoTime() - startNanos) / 1_000_000,
            retryCount = snapshot.retryCount,
            completedNodes = snapshot.completedNodes,
            failedNodes = snapshot.failedNodes,
        )
    }

    private fun resetState(graph: Nag) {
        monitor.reset()
        queueManager.clear()
        rollbackManager.clear()
        retryAttempts.clear()
        scheduledNodes.clear()
        monitor.initializeGraph(graph.graphId, graph.actionNodes.map { it.id })
    }

    private sealed interface NodeBatchOutcome {
        data object Success : NodeBatchOutcome
        data object Retrying : NodeBatchOutcome
        data object Cancelled : NodeBatchOutcome
        data class Failed(val error: RuntimeError) : NodeBatchOutcome
    }

    companion object {
        private const val POLL_INTERVAL_MS = 10L
    }
}

data class ActiveGraphExecution(
    val graphId: UUID,
    val traceId: UUID,
    val executionId: UUID,
    val pauseFlag: AtomicBoolean = AtomicBoolean(false),
    val cancelFlag: AtomicBoolean = AtomicBoolean(false),
    val job: Job? = null,
)
