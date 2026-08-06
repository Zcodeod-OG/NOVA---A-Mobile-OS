package com.nova.runtime.planner

import com.nova.runtime.models.ErrorCategory
import com.nova.runtime.models.ErrorSeverity
import com.nova.runtime.models.Nag
import com.nova.runtime.models.RuntimeError
import com.nova.runtime.models.RuntimeModule
import com.nova.runtime.models.contracts.PlanningRequest
import com.nova.runtime.models.contracts.PlanningResult
import com.nova.runtime.planner.decomposition.GoalDecomposer
import com.nova.runtime.planner.dependency.DependencyAnalyzer
import com.nova.runtime.planner.events.PlannerEventPublisher
import com.nova.runtime.planner.generation.TaskGenerator
import com.nova.runtime.planner.graph.ActionGraphBuilder
import com.nova.runtime.planner.model.PlanningMetrics
import com.nova.runtime.planner.optimization.GraphOptimizer
import com.nova.runtime.planner.util.DeterministicIds
import com.nova.runtime.planner.validation.CycleBreaker
import com.nova.runtime.planner.validation.GraphValidator
import com.nova.runtime.utils.logging.NovaLogger

/**
 * Planning Service — TDD §11 / MSP §8.
 * Transforms Reasoning Context + NIR into a deterministic, validated NAG.
 */
class PlanningServiceImpl(
    private val goalDecomposer: GoalDecomposer,
    private val taskGenerator: TaskGenerator,
    private val actionGraphBuilder: ActionGraphBuilder,
    private val dependencyAnalyzer: DependencyAnalyzer,
    private val graphValidator: GraphValidator,
    private val cycleBreaker: CycleBreaker,
    private val graphOptimizer: GraphOptimizer,
    private val eventPublisher: PlannerEventPublisher,
    private val logger: NovaLogger,
) : PlanningService {

    override suspend fun buildGraph(request: PlanningRequest): PlanningResult {
        val traceId = request.traceId
        val nir = request.nir
        val reasoningContext = request.reasoningContext
        val startNanos = System.nanoTime()

        logger.info(
            RuntimeModule.PLANNER.name,
            "Planning started for goal '${nir.goal}'",
            traceId,
        )
        eventPublisher.publishStarted(traceId, nir.goal)

        val subGoals = goalDecomposer.decompose(nir, reasoningContext)
        val tasks = taskGenerator.generate(subGoals, nir, reasoningContext)
        eventPublisher.publishTasksGenerated(traceId, tasks.size, subGoals.size)

        val graphId = DeterministicIds.uuid("graph", "$traceId:${nir.goal}")
        var graph = actionGraphBuilder.build(graphId, subGoals, tasks, traceId)
        graph = dependencyAnalyzer.assignDependencies(graph, tasks)
        eventPublisher.publishGraphBuilt(traceId, graphId, graph.actionNodes.size)

        val preValidation = graphValidator.validate(graph)
        val cycleBreak = cycleBreaker.ensureAcyclic(graph)
        graph = cycleBreak.graph

        val optimization = graphOptimizer.optimize(graph)
        graph = optimization.graph

        val finalValidation = graphValidator.validate(graph)
        if (!finalValidation.isAcyclic) {
            return PlanningResult.Failure(
                RuntimeError(
                    code = "PLANNER_CYCLE_UNRESOLVED",
                    category = ErrorCategory.VALIDATION,
                    severity = ErrorSeverity.HIGH,
                    recoverable = false,
                    userVisibleMessage = "Unable to produce a valid action graph.",
                    diagnostics = mapOf("cyclesDetected" to finalValidation.cyclesDetected.toString()),
                ),
            )
        }

        eventPublisher.publishGraphOptimized(
            traceId = traceId,
            nodesMerged = optimization.nodesMerged,
            nodesPruned = optimization.nodesPruned,
        )

        val generationTimeMs = (System.nanoTime() - startNanos) / 1_000_000
        val metrics = PlanningMetrics(
            nodeCount = graph.actionNodes.size,
            depth = finalValidation.depth,
            generationTimeMs = generationTimeMs,
            cyclesDetected = preValidation.cyclesDetected,
            cyclesBroken = cycleBreak.cyclesBroken,
            nodesMerged = optimization.nodesMerged,
            nodesPruned = optimization.nodesPruned,
            taskCount = tasks.size,
            subGoalCount = subGoals.size,
        )

        graph = graph.copy(metadata = graph.metadata + metrics.toMetadata())

        eventPublisher.publishCompleted(traceId, graphId, metrics.toMetadata())
        logger.info(
            RuntimeModule.PLANNER.name,
            "Planning completed with ${graph.actionNodes.size} node(s)",
            traceId,
            durationMs = generationTimeMs,
            metadata = metrics.toMetadata(),
        )

        return PlanningResult.Success(graph)
    }

    override suspend fun validateGraph(graph: Nag): PlanningResult {
        val validation = graphValidator.validate(graph)
        return if (validation.isValid) {
            PlanningResult.Success(graph)
        } else {
            PlanningResult.Failure(
                RuntimeError(
                    code = "PLANNER_INVALID_GRAPH",
                    category = ErrorCategory.VALIDATION,
                    severity = ErrorSeverity.MEDIUM,
                    recoverable = true,
                    userVisibleMessage = "The action graph is invalid.",
                    diagnostics = validation.errors.associateWith { it },
                ),
            )
        }
    }

    override suspend fun estimateCost(graph: Nag): Long {
        val nodeCost = graph.actionNodes.sumOf { it.timeoutMs }
        val depthPenalty = graph.metadata["depth"]?.toLongOrNull()?.times(1_000L) ?: 0L
        return nodeCost + depthPenalty
    }
}
