package com.nova.runtime.orchestrator

import com.nova.runtime.execution.ExecutionRuntime
import com.nova.runtime.memory.MemoryPlatform
import com.nova.runtime.models.Modality
import com.nova.runtime.models.NovaCapabilityOperations
import com.nova.runtime.models.Observation
import com.nova.runtime.models.RuntimeModule
import com.nova.runtime.models.contracts.ExecutionRequest
import com.nova.runtime.models.contracts.ExecutionResult
import com.nova.runtime.models.contracts.MemoryQuery
import com.nova.runtime.models.contracts.MemoryResult
import com.nova.runtime.models.contracts.PlanningRequest
import com.nova.runtime.models.contracts.PlanningResult
import com.nova.runtime.models.contracts.PolicyDecisionType
import com.nova.runtime.models.contracts.PolicyEvaluationResult
import com.nova.runtime.models.contracts.PolicyRequest
import com.nova.runtime.models.contracts.ReasoningEngineResult
import com.nova.runtime.models.contracts.ReasoningRequest
import com.nova.runtime.planner.PlanningService
import com.nova.runtime.policy.PolicyEngine
import com.nova.runtime.reasoning.ReasoningEngine
import com.nova.runtime.understanding.SemanticUnderstandingPipeline
import com.nova.runtime.utils.logging.NovaLogger
import java.time.Instant
import java.util.UUID

/**
 * End-to-end cognitive pipeline coordinator — TDD §2 flow.
 * SUP → Reasoning → Planning → Policy → Execution.
 */
class CognitivePipelineOrchestrator(
    private val understandingPipeline: SemanticUnderstandingPipeline,
    private val reasoningEngine: ReasoningEngine,
    private val planningService: PlanningService,
    private val policyEngine: PolicyEngine,
    private val executionRuntime: ExecutionRuntime,
    private val memoryPlatform: MemoryPlatform,
    private val logger: NovaLogger,
) {
    suspend fun processUserCommand(text: String, traceId: String): PipelineResult {
        val traceUuid = parseTraceId(traceId)
        val command = text.trim()
        if (command.isBlank()) {
            return PipelineResult.Failure(
                traceId = traceUuid,
                stage = PipelineStage.UNDERSTANDING,
                error = blankInputError(),
                summary = "Command cannot be empty.",
            )
        }

        logger.info(RuntimeModule.KERNEL.name, "Pipeline started for command", traceUuid)

        val observation = Observation(
            id = UUID.randomUUID(),
            timestamp = Instant.now(),
            sessionId = UI_SESSION_ID,
            traceId = traceUuid,
            modality = Modality.TEXT,
            payload = command,
            metadata = mapOf("source" to "command_bar"),
        )

        val nir = understandingPipeline.process(observation)
            ?: return PipelineResult.Failure(
                traceId = traceUuid,
                stage = PipelineStage.UNDERSTANDING,
                error = understandingFailedError(),
                summary = "Could not understand the command.",
            )

        val capabilityOperation = nir.constraints["capabilityOperation"]
            ?: NovaCapabilityOperations.forIntent(nir.goal)
            ?: "unknown"

        val memoryResults = resolveMemory(nir, traceUuid)
        val reasoningResult = reasoningEngine.reason(
            ReasoningRequest(
                nir = nir,
                memoryResults = memoryResults,
                traceId = traceUuid,
            ),
        )

        val reasoningContext = when (reasoningResult) {
            is ReasoningEngineResult.Success -> reasoningResult.context
            is ReasoningEngineResult.Failure -> {
                return PipelineResult.Failure(
                    traceId = traceUuid,
                    stage = PipelineStage.REASONING,
                    error = reasoningResult.error,
                    summary = reasoningResult.error.userVisibleMessage,
                )
            }
        }

        val planningResult = planningService.buildGraph(
            PlanningRequest(
                nir = nir,
                reasoningContext = reasoningContext,
                traceId = traceUuid,
            ),
        )

        val graph = when (planningResult) {
            is PlanningResult.Success -> planningResult.graph
            is PlanningResult.Failure -> {
                return PipelineResult.Failure(
                    traceId = traceUuid,
                    stage = PipelineStage.PLANNING,
                    error = planningResult.error,
                    summary = planningResult.error.userVisibleMessage,
                )
            }
        }

        when (val policyResult = policyEngine.evaluate(PolicyRequest(graph = graph, traceId = traceUuid))) {
            is PolicyEvaluationResult.Success -> {
                if (policyResult.decision.type == PolicyDecisionType.REJECTED) {
                    return PipelineResult.Failure(
                        traceId = traceUuid,
                        stage = PipelineStage.POLICY,
                        error = policyDeniedError(policyResult.decision.rationale),
                        summary = policyResult.decision.rationale,
                    )
                }
            }
            is PolicyEvaluationResult.Failure -> {
                return PipelineResult.Failure(
                    traceId = traceUuid,
                    stage = PipelineStage.POLICY,
                    error = policyResult.error,
                    summary = policyResult.error.userVisibleMessage,
                )
            }
        }

        when (val executionResult = executionRuntime.execute(ExecutionRequest(graph = graph, traceId = traceUuid))) {
            is ExecutionResult.Success -> {
                val summary = buildSuccessSummary(nir.goal, capabilityOperation, executionResult.completedNodes)
                logger.info(
                    RuntimeModule.EXECUTION.name,
                    summary,
                    traceUuid,
                    metadata = mapOf("capabilityOperation" to capabilityOperation),
                )
                return PipelineResult.Success(
                    traceId = traceUuid,
                    nir = nir,
                    graph = graph,
                    capabilityOperation = capabilityOperation,
                    completedNodes = executionResult.completedNodes,
                    summary = summary,
                )
            }
            is ExecutionResult.Failure -> {
                return PipelineResult.Failure(
                    traceId = traceUuid,
                    stage = PipelineStage.EXECUTION,
                    error = executionResult.error,
                    summary = executionResult.error.userVisibleMessage,
                )
            }
        }
    }

    private suspend fun resolveMemory(nir: com.nova.runtime.models.Nir, traceId: UUID): MemoryResult =
        when (
            val result = memoryPlatform.query(
                MemoryQuery(
                    queryType = "context_retrieval",
                    parameters = buildMap {
                        put("goal", nir.goal)
                        if (nir.entities.isNotEmpty()) {
                            put("entities", nir.entities.joinToString(","))
                        }
                    },
                    retrievalStrategy = "default",
                    maxResults = 10,
                    timeoutMs = 5_000,
                    traceId = traceId,
                ),
            )
        ) {
            is MemoryResult.Success -> result
            is MemoryResult.Failure -> {
                logger.debug(
                    RuntimeModule.MEMORY.name,
                    "Memory unavailable, proceeding with empty context: ${result.error.code}",
                    traceId,
                )
                MemoryResult.Success(entries = emptyList())
            }
        }

    private fun buildSuccessSummary(goal: String, capabilityOperation: String, completedNodes: Int): String =
        "Completed '$goal' via $capabilityOperation ($completedNodes node(s) executed)."

    private fun parseTraceId(traceId: String): UUID =
        runCatching { UUID.fromString(traceId) }.getOrElse { UUID.randomUUID() }

    private fun blankInputError() = com.nova.runtime.models.RuntimeError(
        code = "PIPELINE_BLANK_INPUT",
        category = com.nova.runtime.models.ErrorCategory.VALIDATION,
        severity = com.nova.runtime.models.ErrorSeverity.LOW,
        recoverable = true,
        userVisibleMessage = "Please enter a command.",
    )

    private fun understandingFailedError() = com.nova.runtime.models.RuntimeError(
        code = "PIPELINE_UNDERSTANDING_FAILED",
        category = com.nova.runtime.models.ErrorCategory.VALIDATION,
        severity = com.nova.runtime.models.ErrorSeverity.MEDIUM,
        recoverable = true,
        userVisibleMessage = "Could not understand the command.",
    )

    private fun policyDeniedError(rationale: String) = com.nova.runtime.models.RuntimeError(
        code = "PIPELINE_POLICY_DENIED",
        category = com.nova.runtime.models.ErrorCategory.PERMISSION,
        severity = com.nova.runtime.models.ErrorSeverity.HIGH,
        recoverable = false,
        userVisibleMessage = rationale,
    )

    companion object {
        private val UI_SESSION_ID = UUID.fromString("00000000-0000-0000-0000-000000000001")
    }
}
