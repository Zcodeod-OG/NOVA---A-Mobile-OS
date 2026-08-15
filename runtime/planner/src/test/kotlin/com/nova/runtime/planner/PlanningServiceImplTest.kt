package com.nova.runtime.planner

import com.nova.runtime.events.InMemoryEventBus
import com.nova.runtime.events.planner.PlannerEvents
import com.nova.runtime.models.Nir
import com.nova.runtime.models.Priority
import com.nova.runtime.models.ReasoningContext
import com.nova.runtime.models.ActionNode
import com.nova.runtime.models.Nag
import com.nova.runtime.models.contracts.PlanningRequest
import com.nova.runtime.models.contracts.PlanningResult
import com.nova.runtime.planner.decomposition.DefaultGoalDecomposer
import com.nova.runtime.planner.dependency.DefaultDependencyAnalyzer
import com.nova.runtime.planner.events.PlannerEventPublisher
import com.nova.runtime.planner.generation.DefaultTaskGenerator
import com.nova.runtime.planner.graph.DefaultActionGraphBuilder
import com.nova.runtime.planner.optimization.DefaultGraphOptimizer
import com.nova.runtime.planner.serialization.CanonicalNagSerializer
import com.nova.runtime.planner.util.DeterministicIds
import com.nova.runtime.planner.validation.DefaultCycleBreaker
import com.nova.runtime.planner.validation.DefaultGraphValidator
import com.nova.runtime.utils.logging.StructuredLogger
import java.util.UUID
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PlanningServiceImplTest {

    private val logger = StructuredLogger()
    private val eventBus = InMemoryEventBus(logger)
    private val validator = DefaultGraphValidator()

    private fun createService(): PlanningServiceImpl =
        PlanningServiceImpl(
            goalDecomposer = DefaultGoalDecomposer(),
            taskGenerator = DefaultTaskGenerator(),
            actionGraphBuilder = DefaultActionGraphBuilder(),
            dependencyAnalyzer = DefaultDependencyAnalyzer(),
            graphValidator = validator,
            cycleBreaker = DefaultCycleBreaker(validator),
            graphOptimizer = DefaultGraphOptimizer(validator),
            eventPublisher = PlannerEventPublisher(eventBus),
            logger = logger,
        )

    @Test
    fun buildGraph_sameInputTwice_producesIdenticalOutput() = runTest {
        val service = createService()
        val request = sampleRequest()

        val first = service.buildGraph(request)
        val second = service.buildGraph(request)

        assertIs<PlanningResult.Success>(first)
        assertIs<PlanningResult.Success>(second)
        assertEquals(first.graph, second.graph)
    }

    @Test
    fun buildGraph_outputIsAcyclicDag() = runTest {
        val service = createService()
        val result = service.buildGraph(sampleRequest())
        assertIs<PlanningResult.Success>(result)

        val validation = validator.validate(result.graph)
        assertTrue(validation.isAcyclic, "Graph must be acyclic: ${validation.errors}")
        assertTrue(validation.isValid, "Graph must be valid: ${validation.errors}")
    }

    @Test
    fun buildGraph_publishesPlannerEvents() = runTest {
        val service = createService()
        service.buildGraph(sampleRequest())

        val eventTypes = eventBus.publishedEvents().map { it.eventType }
        assertTrue(PlannerEvents.PLANNING_STARTED in eventTypes)
        assertTrue(PlannerEvents.TASKS_GENERATED in eventTypes)
        assertTrue(PlannerEvents.GRAPH_BUILT in eventTypes)
        assertTrue(PlannerEvents.GRAPH_OPTIMIZED in eventTypes)
        assertTrue(PlannerEvents.PLANNING_COMPLETED in eventTypes)
    }

    @Test
    fun buildGraph_includesPlanningMetricsInMetadata() = runTest {
        val service = createService()
        val result = service.buildGraph(sampleRequest())
        assertIs<PlanningResult.Success>(result)

        val metadata = result.graph.metadata
        assertTrue(metadata.containsKey("nodeCount"))
        assertTrue(metadata.containsKey("depth"))
        assertTrue(metadata.containsKey("generationTimeMs"))
        assertTrue(metadata.containsKey("taskCount"))
    }

    @Test
    fun validateGraph_rejectsInvalidGraph() = runTest {
        val service = createService()
        val invalid = Nag(
            graphId = UUID.randomUUID(),
            metadata = emptyMap(),
            taskHierarchy = emptyList(),
            actionNodes = emptyList(),
            dependencies = emptyMap(),
            executionPolicies = emptyMap(),
        )

        val result = service.validateGraph(invalid)
        assertIs<PlanningResult.Failure>(result)
    }

    @Test
    fun estimateCost_sumsNodeTimeouts() = runTest {
        val service = createService()
        val graph = sampleCyclicGraph()
        val cost = service.estimateCost(graph)
        assertTrue(cost >= 20_000L)
    }

    private fun sampleRequest(): PlanningRequest {
        val traceId = UUID.fromString("00000000-0000-0000-0000-000000000101")
        return PlanningRequest(
            traceId = traceId,
            nir = Nir(
                version = 1,
                goal = "send message to john",
                entities = listOf("john", "message"),
                constraints = mapOf("channel" to "whatsapp"),
                context = emptyMap(),
                requiredCapabilities = listOf("communication"),
                confidence = 0.85,
            ),
            reasoningContext = ReasoningContext(
                resolvedEntities = mapOf("john" to "John Smith"),
                evidence = listOf("contact:John Smith (score=0.9)"),
                assumptions = listOf("User prefers WhatsApp"),
                recommendations = listOf("Required capabilities: communication"),
                confidence = 0.82,
            ),
        )
    }

    private fun sampleCyclicGraph(): Nag {
        val nodeA = actionNode("a", emptyList())
        val nodeB = actionNode("b", listOf(nodeA.id))
        val nodeC = actionNode("c", listOf(nodeB.id))
        val cyclicA = nodeA.copy(dependencies = listOf(nodeC.id))
        return Nag(
            graphId = DeterministicIds.uuid("graph", "test-cycle"),
            metadata = emptyMap(),
            taskHierarchy = listOf("complete_goal"),
            actionNodes = listOf(cyclicA, nodeB, nodeC),
            dependencies = mapOf(
                cyclicA.id to listOf(nodeC.id),
                nodeB.id to listOf(nodeA.id),
                nodeC.id to listOf(nodeB.id),
            ),
            executionPolicies = emptyMap(),
        )
    }

    private fun actionNode(key: String, dependencies: List<UUID>): ActionNode =
        ActionNode(
            id = DeterministicIds.uuid("action", key),
            actionType = "execute_capability",
            inputs = mapOf("taskKey" to key),
            outputs = mapOf("taskKey" to key),
            dependencies = dependencies,
            timeoutMs = 10_000L,
            retryPolicy = "none",
            rollbackPolicy = "none",
            executionPriority = Priority.NORMAL,
        )
}
