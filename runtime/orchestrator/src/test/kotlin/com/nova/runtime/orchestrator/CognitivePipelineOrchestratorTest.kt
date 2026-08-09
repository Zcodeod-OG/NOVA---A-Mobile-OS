package com.nova.runtime.orchestrator

import com.nova.runtime.capability.CapabilityFrameworkImpl
import com.nova.runtime.capability.events.CapabilityEventPublisher
import com.nova.runtime.capability.health.DefaultCapabilityHealthMonitor
import com.nova.runtime.capability.lifecycle.DefaultCapabilityLifecycleManager
import com.nova.runtime.capability.provider.defaultStubProviders
import com.nova.runtime.capability.registry.DefaultCapabilityRegistry
import com.nova.runtime.capability.resolver.DefaultCapabilityProviderResolver
import com.nova.runtime.capability.transaction.DefaultCapabilityTransactionManager
import com.nova.runtime.events.InMemoryEventBus
import com.nova.runtime.execution.ExecutionRuntimeImpl
import com.nova.runtime.execution.ExecutionRuntimeFactory
import com.nova.runtime.execution.events.ExecutionEventPublisher
import com.nova.runtime.execution.history.NoOpExecutionHistoryRecorder
import com.nova.runtime.execution.metrics.ExecutionMetrics
import com.nova.runtime.execution.model.ExecutionConfig
import com.nova.runtime.execution.monitor.DefaultExecutionMonitor
import com.nova.runtime.execution.queue.DefaultQueueManager
import com.nova.runtime.execution.retry.DefaultRetryManager
import com.nova.runtime.execution.rollback.DefaultRollbackManager
import com.nova.runtime.execution.scheduler.DefaultDependencyResolver
import com.nova.runtime.execution.worker.StubCapabilityActionExecutor
import com.nova.runtime.inference.AdaptiveInferenceEngineStub
import com.nova.runtime.kernel.trace.DefaultTraceIdGenerator
import com.nova.runtime.kernel.trace.TraceContextHolder
import com.nova.runtime.memory.MemoryPlatform
import com.nova.runtime.models.NovaCapabilityOperations
import com.nova.runtime.models.contracts.MemoryQuery
import com.nova.runtime.models.contracts.MemoryResult
import com.nova.runtime.planner.PlanningServiceImpl
import com.nova.runtime.planner.decomposition.DefaultGoalDecomposer
import com.nova.runtime.planner.dependency.DefaultDependencyAnalyzer
import com.nova.runtime.planner.events.PlannerEventPublisher
import com.nova.runtime.planner.generation.DefaultTaskGenerator
import com.nova.runtime.planner.graph.DefaultActionGraphBuilder
import com.nova.runtime.planner.optimization.DefaultGraphOptimizer
import com.nova.runtime.planner.validation.DefaultCycleBreaker
import com.nova.runtime.planner.validation.DefaultGraphValidator
import com.nova.runtime.policy.PolicyEngineImpl
import com.nova.runtime.policy.context.DefaultPolicyEnvironment
import com.nova.runtime.policy.evaluator.BatteryPolicyEvaluator
import com.nova.runtime.policy.evaluator.ConfirmationPolicyEvaluator
import com.nova.runtime.policy.evaluator.PermissionPolicyEvaluator
import com.nova.runtime.policy.evaluator.PrivacyPolicyEvaluator
import com.nova.runtime.policy.evaluator.SafetyPolicyEvaluator
import com.nova.runtime.policy.events.PolicyEventPublisher
import com.nova.runtime.reasoning.ReasoningEngineImpl
import com.nova.runtime.reasoning.ambiguity.DefaultAmbiguityResolver
import com.nova.runtime.reasoning.confidence.DefaultConfidenceScorer
import com.nova.runtime.reasoning.context.DefaultReasoningContextBuilder
import com.nova.runtime.reasoning.events.ReasoningEventPublisher
import com.nova.runtime.reasoning.evidence.DefaultEvidenceRanker
import com.nova.runtime.reasoning.explanation.DefaultExplanationGenerator
import com.nova.runtime.understanding.SemanticUnderstandingPipelineImpl
import com.nova.runtime.understanding.constraint.PlaceholderConstraintExtractor
import com.nova.runtime.understanding.entity.PlaceholderEntityExtractor
import com.nova.runtime.understanding.events.UnderstandingEventPublisher
import com.nova.runtime.understanding.intent.PlaceholderIntentClassifier
import com.nova.runtime.understanding.nir.DefaultNirGenerator
import com.nova.runtime.understanding.normalization.DefaultObservationNormalizer
import com.nova.runtime.understanding.routing.StubInferenceRoutingHook
import com.nova.runtime.understanding.validation.DefaultNirValidator
import com.nova.runtime.utils.logging.StructuredLogger
import java.util.UUID
import java.util.stream.Stream
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

class CognitivePipelineOrchestratorTest {

    private val logger = StructuredLogger()
    private val eventBus = InMemoryEventBus(logger)
    private val traceContextHolder = TraceContextHolder(DefaultTraceIdGenerator())

    private fun createOrchestrator(): CognitivePipelineOrchestrator {
        val capabilityRegistry = DefaultCapabilityRegistry(defaultStubProviders())
        val capabilityLifecycle = DefaultCapabilityLifecycleManager(capabilityRegistry)
        val capabilityFramework = CapabilityFrameworkImpl(
            registry = capabilityRegistry,
            resolver = DefaultCapabilityProviderResolver(capabilityRegistry, capabilityLifecycle),
            lifecycleManager = capabilityLifecycle,
            healthMonitor = DefaultCapabilityHealthMonitor(capabilityRegistry, capabilityLifecycle),
            transactionManager = DefaultCapabilityTransactionManager(),
            eventPublisher = CapabilityEventPublisher(eventBus),
            logger = logger,
        )
        val graphValidator = DefaultGraphValidator()
        val policyEngine = PolicyEngineImpl(
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
        val executionFactory = ExecutionRuntimeFactory(
            dependencyResolver = DefaultDependencyResolver(),
            queueManager = DefaultQueueManager(),
            retryManager = DefaultRetryManager(defaultMaxRetries = 1, defaultBackoffMs = 0),
            rollbackManager = DefaultRollbackManager(),
            monitor = DefaultExecutionMonitor(),
            metrics = ExecutionMetrics(),
            eventPublisher = ExecutionEventPublisher(eventBus),
            actionExecutor = StubCapabilityActionExecutor(capabilityFramework),
            actionPolicyGate = policyEngine,
            historyRecorder = NoOpExecutionHistoryRecorder(),
            logger = logger,
            config = ExecutionConfig(workerPoolSize = 2),
        )

        return CognitivePipelineOrchestrator(
            understandingPipeline = SemanticUnderstandingPipelineImpl(
                normalizer = DefaultObservationNormalizer(),
                routingHook = StubInferenceRoutingHook(),
                entityExtractor = PlaceholderEntityExtractor(),
                intentClassifier = PlaceholderIntentClassifier(),
                constraintExtractor = PlaceholderConstraintExtractor(),
                nirGenerator = DefaultNirGenerator(),
                nirValidator = DefaultNirValidator(),
                eventPublisher = UnderstandingEventPublisher(eventBus),
                inferenceEngine = AdaptiveInferenceEngineStub(),
                traceContextHolder = traceContextHolder,
                logger = logger,
            ),
            reasoningEngine = ReasoningEngineImpl(
                evidenceRanker = DefaultEvidenceRanker(),
                ambiguityResolver = DefaultAmbiguityResolver(),
                confidenceScorer = DefaultConfidenceScorer(),
                contextBuilder = DefaultReasoningContextBuilder(),
                explanationGenerator = DefaultExplanationGenerator(),
                eventPublisher = ReasoningEventPublisher(eventBus),
                logger = logger,
            ),
            planningService = PlanningServiceImpl(
                goalDecomposer = DefaultGoalDecomposer(),
                taskGenerator = DefaultTaskGenerator(),
                actionGraphBuilder = DefaultActionGraphBuilder(),
                dependencyAnalyzer = DefaultDependencyAnalyzer(),
                graphValidator = graphValidator,
                cycleBreaker = DefaultCycleBreaker(graphValidator),
                graphOptimizer = DefaultGraphOptimizer(graphValidator),
                eventPublisher = PlannerEventPublisher(eventBus),
                logger = logger,
            ),
            policyEngine = policyEngine,
            executionRuntime = ExecutionRuntimeImpl(
                schedulerFactory = { executionFactory.createScheduler() },
                eventPublisher = ExecutionEventPublisher(eventBus),
                logger = logger,
            ),
            memoryPlatform = EmptyMemoryPlatform,
            logger = logger,
        )
    }

    @ParameterizedTest
    @MethodSource("featureCommands")
    fun processUserCommand_featureIntent_reachesExecution(
        command: String,
        expectedOperation: String,
    ) = runTest {
        val orchestrator = createOrchestrator()
        val traceId = UUID.randomUUID()

        val result = orchestrator.processUserCommand(command, traceId.toString())

        assertIs<PipelineResult.Success>(result, "Command '$command' failed: $result")
        assertTrue(
            result.capabilityOperation == expectedOperation,
            "Expected $expectedOperation but got ${result.capabilityOperation}",
        )
        assertTrue(result.completedNodes > 0, "Expected at least one node executed")
        val expectedShortOp = expectedOperation.substringAfter(".")
        assertTrue(
            result.graph.actionNodes.any { node ->
                if (node.actionType != "execute_capability") return@any false
                val op = node.inputs["operation"]
                val capabilityType = node.inputs["capabilityType"]
                val capabilityOperation = node.inputs["capabilityOperation"]
                op == expectedShortOp ||
                    op == "search" && expectedOperation.startsWith("search.") ||
                    capabilityType == expectedOperation ||
                    capabilityOperation == expectedOperation
            },
            "Expected execute_capability node for $expectedOperation, nodes=${result.graph.actionNodes.map { it.inputs }}",
        )
    }

    @ParameterizedTest
    @MethodSource("featureCommands")
    fun processUserCommand_blankMemory_degradesGracefully(command: String, expectedOperation: String) = runTest {
        val result = createOrchestrator().processUserCommand(command, UUID.randomUUID().toString())
        assertIs<PipelineResult.Success>(result)
        assertTrue(result.capabilityOperation == expectedOperation)
    }

    @ParameterizedTest
    @MethodSource("negatedCommands")
    fun processUserCommand_negatedCommand_executesNothing(command: String) = runTest {
        val result = createOrchestrator().processUserCommand(command, UUID.randomUUID().toString())

        assertIs<PipelineResult.Success>(result, "Negated command '$command' failed: $result")
        assertTrue(result.completedNodes == 0, "Expected no nodes executed for '$command'")
        assertTrue(result.graph.actionNodes.isEmpty(), "Expected empty action graph for '$command'")
        assertTrue(result.summary.startsWith("Okay"), "Expected acknowledgement summary, got: ${result.summary}")
    }

    @org.junit.jupiter.api.Test
    fun processUserCommand_openCommandVsNegatedOpen_differ() = runTest {
        val orchestrator = createOrchestrator()

        val open = orchestrator.processUserCommand("open youtube", UUID.randomUUID().toString())
        assertIs<PipelineResult.Success>(open)
        assertTrue(open.capabilityOperation == NovaCapabilityOperations.DEVICE_OPEN_APP)
        assertTrue(open.completedNodes > 0)

        val negated = orchestrator.processUserCommand("do not open youtube", UUID.randomUUID().toString())
        assertIs<PipelineResult.Success>(negated)
        assertTrue(negated.capabilityOperation == "none")
        assertTrue(negated.completedNodes == 0)
    }

    companion object {
        @JvmStatic
        fun featureCommands(): Stream<Arguments> = Stream.of(
            Arguments.of(
                "send whatsapp message to John saying hello",
                NovaCapabilityOperations.WHATSAPP_SEND_MESSAGE,
            ),
            Arguments.of(
                "search photos from last week",
                NovaCapabilityOperations.SEARCH_PHOTOS,
            ),
            Arguments.of(
                "find document about budget",
                NovaCapabilityOperations.SEARCH_DOCUMENTS,
            ),
            Arguments.of(
                "set alarm for 7am",
                NovaCapabilityOperations.ALARM_CREATE,
            ),
            Arguments.of(
                "create calendar event tomorrow 3pm",
                NovaCapabilityOperations.CALENDAR_CREATE,
            ),
            Arguments.of(
                "lookup contact John",
                NovaCapabilityOperations.CONTACTS_SEARCH,
            ),
            Arguments.of(
                "semantic search vacation photos",
                NovaCapabilityOperations.SEARCH_SEMANTIC,
            ),
            Arguments.of(
                "share file report.pdf",
                NovaCapabilityOperations.SHARE_FILE,
            ),
            Arguments.of(
                "open youtube",
                NovaCapabilityOperations.DEVICE_OPEN_APP,
            ),
            Arguments.of(
                "open youtube and search shape of you",
                NovaCapabilityOperations.DEVICE_APP_SEARCH,
            ),
            Arguments.of(
                "remind me to call mom at 5pm",
                NovaCapabilityOperations.ALARM_CREATE,
            ),
            Arguments.of(
                "send todays mess menu to atharv on whatsapp",
                NovaCapabilityOperations.WHATSAPP_SEND_MESSAGE,
            ),
            Arguments.of(
                "play shape of you on spotify",
                NovaCapabilityOperations.DEVICE_APP_SEARCH,
            ),
        )

        @JvmStatic
        fun negatedCommands(): Stream<Arguments> = Stream.of(
            Arguments.of("do not open youtube"),
            Arguments.of("don't open youtube"),
            Arguments.of("never open youtube"),
            Arguments.of("do not set an alarm for 7am"),
        )
    }
}

private object EmptyMemoryPlatform : MemoryPlatform {
    override suspend fun store(entry: Map<String, String>): MemoryResult =
        MemoryResult.Success(emptyList())

    override suspend fun query(query: MemoryQuery): MemoryResult =
        MemoryResult.Failure(
            com.nova.runtime.models.RuntimeError(
                code = "MEMORY_NOT_IMPLEMENTED",
                category = com.nova.runtime.models.ErrorCategory.INFRASTRUCTURE,
                severity = com.nova.runtime.models.ErrorSeverity.LOW,
                recoverable = true,
                userVisibleMessage = "Memory unavailable",
            ),
        )

    override suspend fun update(id: String, entry: Map<String, String>): MemoryResult =
        MemoryResult.Success(emptyList())

    override suspend fun forget(id: String): MemoryResult = MemoryResult.Success(emptyList())

    override suspend fun restore(id: String): MemoryResult = MemoryResult.Success(emptyList())
}
