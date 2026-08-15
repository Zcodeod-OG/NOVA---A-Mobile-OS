package com.nova.runtime.policy

import com.nova.runtime.models.ErrorCategory
import com.nova.runtime.models.ErrorSeverity
import com.nova.runtime.models.RuntimeError
import com.nova.runtime.models.RuntimeModule
import com.nova.runtime.models.contracts.ActionPolicyRequest
import com.nova.runtime.models.contracts.PolicyDecision
import com.nova.runtime.models.contracts.PolicyDecisionType
import com.nova.runtime.models.contracts.PolicyEvaluationResult
import com.nova.runtime.models.contracts.PolicyRequest
import com.nova.runtime.policy.evaluator.PolicyDecisionAggregator
import com.nova.runtime.policy.evaluator.PolicyEvaluator
import com.nova.runtime.policy.events.PolicyEventPublisher
import com.nova.runtime.utils.logging.NovaLogger

/**
 * Policy Engine — TDD §14 / MSP §10.
 * Composes evaluators for permissions, safety, confirmation, privacy, and battery.
 */
class PolicyEngineImpl(
    private val evaluators: List<PolicyEvaluator>,
    private val eventPublisher: PolicyEventPublisher,
    private val logger: NovaLogger,
) : PolicyEngine {

    override suspend fun evaluate(request: PolicyRequest): PolicyEvaluationResult {
        eventPublisher.publishEvaluationStarted(
            traceId = request.traceId,
            graphId = request.graph.graphId,
            nodeId = null,
        )

        var worstDecision: PolicyDecision? = null
        for (node in request.graph.actionNodes) {
            when (val result = evaluateAction(actionRequest(node, request))) {
                is PolicyEvaluationResult.Success -> {
                    worstDecision = mergeDecisions(worstDecision, result.decision)
                    if (worstDecision?.type == PolicyDecisionType.REJECTED) break
                }
                is PolicyEvaluationResult.Failure -> return result
            }
        }

        val decision = worstDecision ?: PolicyDecision(
            type = PolicyDecisionType.APPROVED,
            rationale = "Empty graph approved",
        )

        publishCompleted(request.traceId, decision, null)
        logger.info(
            RuntimeModule.POLICY.name,
            "Graph policy evaluation: ${decision.type}",
            request.traceId,
            metadata = mapOf("rationale" to decision.rationale),
        )
        return PolicyEvaluationResult.Success(decision)
    }

    override suspend fun evaluateAction(request: ActionPolicyRequest): PolicyEvaluationResult {
        eventPublisher.publishEvaluationStarted(
            traceId = request.traceId,
            graphId = request.graphId,
            nodeId = request.node.id,
        )

        val results = evaluators.map { evaluator -> evaluator.evaluate(request) }
        val decision = PolicyDecisionAggregator.aggregate(results)

        publishCompleted(request.traceId, decision, request.node.id)
        logActionDecision(request, decision)

        when (decision.type) {
            PolicyDecisionType.REJECTED ->
                eventPublisher.publishActionDenied(request.traceId, request.node.id, decision.rationale)
            PolicyDecisionType.REQUIRES_USER_CONFIRMATION ->
                eventPublisher.publishConfirmationRequired(request.traceId, request.node.id, decision.rationale)
            PolicyDecisionType.APPROVED -> Unit
        }

        return PolicyEvaluationResult.Success(decision)
    }

    private suspend fun publishCompleted(traceId: java.util.UUID, decision: PolicyDecision, nodeId: java.util.UUID?) {
        eventPublisher.publishEvaluationCompleted(
            traceId = traceId,
            decisionType = decision.type.name,
            rationale = decision.rationale,
            nodeId = nodeId,
        )
    }

    private fun logActionDecision(request: ActionPolicyRequest, decision: PolicyDecision) {
        logger.debug(
            RuntimeModule.POLICY.name,
            "Action ${request.node.id} policy: ${decision.type}",
            request.traceId,
            metadata = mapOf(
                "actionType" to request.node.actionType,
                "rationale" to decision.rationale,
            ),
        )
    }

    private fun actionRequest(node: com.nova.runtime.models.ActionNode, request: PolicyRequest): ActionPolicyRequest =
        ActionPolicyRequest(
            node = node,
            traceId = request.traceId,
            graphId = request.graph.graphId,
            executionPolicies = request.graph.executionPolicies,
        )

    private fun mergeDecisions(current: PolicyDecision?, next: PolicyDecision): PolicyDecision {
        if (current == null) return next
        val priority = mapOf(
            PolicyDecisionType.REJECTED to 3,
            PolicyDecisionType.REQUIRES_USER_CONFIRMATION to 2,
            PolicyDecisionType.APPROVED to 1,
        )
        return if ((priority[next.type] ?: 0) > (priority[current.type] ?: 0)) next else current
    }
}

/** Maps policy decisions to execution-layer runtime errors. */
object PolicyExecutionErrors {
    fun denied(rationale: String): RuntimeError = RuntimeError(
        code = "POLICY_DENIED",
        category = ErrorCategory.PERMISSION,
        severity = ErrorSeverity.HIGH,
        recoverable = false,
        userVisibleMessage = rationale,
    )

    fun confirmationRequired(rationale: String): RuntimeError = RuntimeError(
        code = "POLICY_CONFIRMATION_REQUIRED",
        category = ErrorCategory.PERMISSION,
        severity = ErrorSeverity.MEDIUM,
        recoverable = true,
        userVisibleMessage = rationale,
    )
}
