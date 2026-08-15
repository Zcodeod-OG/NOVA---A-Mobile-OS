package com.nova.runtime.execution.scheduler

import com.nova.runtime.events.InMemoryEventBus
import com.nova.runtime.events.policy.PolicyEvents
import com.nova.runtime.execution.events.ExecutionEventPublisher
import com.nova.runtime.execution.history.NoOpExecutionHistoryRecorder
import com.nova.runtime.execution.metrics.ExecutionMetrics
import com.nova.runtime.execution.model.ExecutionConfig
import com.nova.runtime.execution.monitor.DefaultExecutionMonitor
import com.nova.runtime.execution.queue.DefaultQueueManager
import com.nova.runtime.execution.retry.DefaultRetryManager
import com.nova.runtime.execution.rollback.DefaultRollbackManager
import com.nova.runtime.execution.worker.ActionExecutor
import com.nova.runtime.execution.worker.NodeExecutionOutcome
import com.nova.runtime.models.ActionNode
import com.nova.runtime.models.Nag
import com.nova.runtime.models.Priority
import com.nova.runtime.models.contracts.ActionPolicyGate
import com.nova.runtime.models.contracts.ActionPolicyRequest
import com.nova.runtime.models.contracts.ExecutionRequest
import com.nova.runtime.models.contracts.ExecutionResult
import com.nova.runtime.models.contracts.PolicyDecision
import com.nova.runtime.models.contracts.PolicyDecisionType
import com.nova.runtime.models.contracts.PolicyEvaluationResult
import com.nova.runtime.policy.PolicyEngineImpl
import com.nova.runtime.policy.context.DefaultPolicyEnvironment
import com.nova.runtime.policy.evaluator.BatteryPolicyEvaluator
import com.nova.runtime.policy.evaluator.ConfirmationPolicyEvaluator
import com.nova.runtime.policy.evaluator.PermissionPolicyEvaluator
import com.nova.runtime.policy.evaluator.PrivacyPolicyEvaluator
import com.nova.runtime.policy.evaluator.SafetyPolicyEvaluator
import com.nova.runtime.policy.events.PolicyEventPublisher
import com.nova.runtime.utils.logging.StructuredLogger
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class ExecutionPolicyGateIntegrationTest {

    private val logger = StructuredLogger()
    private val eventBus = InMemoryEventBus(logger)

    @Test
    fun execute_blocksNodeWhenPolicyDenies() = runTest {
        val executed = mutableListOf<String>()
        val scheduler = createScheduler(
            actionExecutor = RecordingActionExecutor(executed),
            actionPolicyGate = DenyPolicyGate,
        )

        val node = node("blocked", inputs = mapOf("safetyLevel" to "blocked"))
        val result = scheduler.execute(
            request = ExecutionRequest(graph = graphOf(listOf(node)), traceId = UUID.randomUUID()),
            executionId = UUID.randomUUID(),
            pauseFlag = AtomicBoolean(false),
            cancelFlag = AtomicBoolean(false),
        )

        assertIs<ExecutionResult.Failure>(result)
        assertEquals("POLICY_DENIED", result.error.code)
        assertTrue(executed.isEmpty())
    }

    @Test
    fun execute_runsNodeWhenPolicyApproves() = runTest {
        val executed = mutableListOf<String>()
        val scheduler = createScheduler(
            actionExecutor = RecordingActionExecutor(executed),
            actionPolicyGate = createPolicyEngine(),
        )

        val node = node("ok")
        val result = scheduler.execute(
            request = ExecutionRequest(graph = graphOf(listOf(node)), traceId = UUID.randomUUID()),
            executionId = UUID.randomUUID(),
            pauseFlag = AtomicBoolean(false),
            cancelFlag = AtomicBoolean(false),
        )

        assertIs<ExecutionResult.Success>(result)
        assertEquals(listOf("ok"), executed)
    }

    @Test
    fun execute_requiresConfirmationBlocksUntilConfirmed() = runTest {
        val scheduler = createScheduler(
            actionExecutor = RecordingActionExecutor(mutableListOf()),
            actionPolicyGate = createPolicyEngine(),
        )

        val node = node(
            "purchase",
            inputs = mapOf("requiresConfirmation" to "true"),
        )
        val result = scheduler.execute(
            request = ExecutionRequest(graph = graphOf(listOf(node)), traceId = UUID.randomUUID()),
            executionId = UUID.randomUUID(),
            pauseFlag = AtomicBoolean(false),
            cancelFlag = AtomicBoolean(false),
        )

        assertIs<ExecutionResult.Failure>(result)
        assertEquals("POLICY_CONFIRMATION_REQUIRED", result.error.code)
        assertTrue(PolicyEvents.CONFIRMATION_REQUIRED in eventBus.publishedEvents().map { it.eventType })
    }

    private fun createPolicyEngine(): PolicyEngineImpl =
        PolicyEngineImpl(
            evaluators = listOf(
                PermissionPolicyEvaluator(DefaultPolicyEnvironment()),
                SafetyPolicyEvaluator(),
                ConfirmationPolicyEvaluator(),
                PrivacyPolicyEvaluator(),
                BatteryPolicyEvaluator(DefaultPolicyEnvironment()),
            ),
            eventPublisher = PolicyEventPublisher(eventBus),
            logger = logger,
        )

    private fun createScheduler(
        actionExecutor: ActionExecutor,
        actionPolicyGate: ActionPolicyGate,
    ): ExecutionScheduler =
        ExecutionScheduler(
            dependencyResolver = DefaultDependencyResolver(),
            queueManager = DefaultQueueManager(),
            retryManager = DefaultRetryManager(),
            rollbackManager = DefaultRollbackManager(),
            monitor = DefaultExecutionMonitor(),
            metrics = ExecutionMetrics(),
            eventPublisher = ExecutionEventPublisher(eventBus),
            actionExecutor = actionExecutor,
            actionPolicyGate = actionPolicyGate,
            historyRecorder = NoOpExecutionHistoryRecorder(),
            logger = logger,
            config = ExecutionConfig(workerPoolSize = 1),
        )

    private object DenyPolicyGate : ActionPolicyGate {
        override suspend fun evaluateAction(request: ActionPolicyRequest): PolicyEvaluationResult =
            PolicyEvaluationResult.Success(
                PolicyDecision(
                    type = PolicyDecisionType.REJECTED,
                    rationale = "test deny",
                ),
            )
    }

    private class RecordingActionExecutor(
        private val executed: MutableList<String>,
    ) : ActionExecutor {
        override suspend fun execute(node: ActionNode, traceId: UUID): NodeExecutionOutcome {
            executed.add(node.inputs.getValue("taskKey"))
            return NodeExecutionOutcome.Success(node.outputs)
        }

        override suspend fun rollback(node: ActionNode, traceId: UUID): NodeExecutionOutcome =
            NodeExecutionOutcome.Success(emptyMap())
    }

    private fun node(key: String, inputs: Map<String, String> = emptyMap()): ActionNode =
        ActionNode(
            id = UUID.nameUUIDFromBytes(key.toByteArray()),
            actionType = "execute_capability",
            inputs = inputs + mapOf("taskKey" to key),
            outputs = emptyMap(),
            dependencies = emptyList(),
            timeoutMs = 1_000L,
            retryPolicy = "none",
            rollbackPolicy = "none",
            executionPriority = Priority.NORMAL,
        )

    private fun graphOf(nodes: List<ActionNode>): Nag =
        Nag(
            graphId = UUID.randomUUID(),
            metadata = emptyMap(),
            taskHierarchy = emptyList(),
            actionNodes = nodes,
            dependencies = nodes.associate { it.id to it.dependencies },
            executionPolicies = emptyMap(),
        )
}
