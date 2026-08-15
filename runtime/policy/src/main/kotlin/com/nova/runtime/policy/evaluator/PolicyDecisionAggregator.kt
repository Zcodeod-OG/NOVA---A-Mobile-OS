package com.nova.runtime.policy.evaluator

import com.nova.runtime.models.contracts.PolicyDecision
import com.nova.runtime.models.contracts.PolicyDecisionType
import com.nova.runtime.models.contracts.PolicyVerdict

/** Aggregates evaluator verdicts into a single policy decision. */
object PolicyDecisionAggregator {
    fun aggregate(results: List<PolicyEvaluatorResult>): PolicyDecision {
        val deny = results.firstOrNull { it.verdict == PolicyVerdict.DENY }
        if (deny != null) {
            return PolicyDecision(
                type = PolicyDecisionType.REJECTED,
                rationale = deny.rationale,
                evaluatorId = results.indexOf(deny).let { idx ->
                    results.getOrNull(idx)?.let { "evaluator[$idx]" }
                },
            )
        }

        val confirmation = results.firstOrNull { it.verdict == PolicyVerdict.REQUIRE_CONFIRMATION }
        if (confirmation != null) {
            return PolicyDecision(
                type = PolicyDecisionType.REQUIRES_USER_CONFIRMATION,
                rationale = confirmation.rationale,
            )
        }

        return PolicyDecision(
            type = PolicyDecisionType.APPROVED,
            rationale = "All policy checks passed",
        )
    }
}
