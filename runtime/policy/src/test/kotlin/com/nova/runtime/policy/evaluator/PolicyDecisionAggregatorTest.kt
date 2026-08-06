package com.nova.runtime.policy.evaluator

import com.nova.runtime.models.contracts.PolicyDecisionType
import com.nova.runtime.models.contracts.PolicyVerdict
import kotlin.test.Test
import kotlin.test.assertEquals

class PolicyDecisionAggregatorTest {

    @Test
    fun deny_takesPrecedenceOverConfirmation() {
        val decision = PolicyDecisionAggregator.aggregate(
            listOf(
                PolicyEvaluatorResult(PolicyVerdict.ALLOW, "ok"),
                PolicyEvaluatorResult(PolicyVerdict.REQUIRE_CONFIRMATION, "confirm"),
                PolicyEvaluatorResult(PolicyVerdict.DENY, "blocked"),
            ),
        )
        assertEquals(PolicyDecisionType.REJECTED, decision.type)
        assertEquals("blocked", decision.rationale)
    }

    @Test
    fun confirmation_whenNoDeny() {
        val decision = PolicyDecisionAggregator.aggregate(
            listOf(
                PolicyEvaluatorResult(PolicyVerdict.ALLOW, "ok"),
                PolicyEvaluatorResult(PolicyVerdict.REQUIRE_CONFIRMATION, "confirm"),
            ),
        )
        assertEquals(PolicyDecisionType.REQUIRES_USER_CONFIRMATION, decision.type)
    }

    @Test
    fun approved_whenAllAllow() {
        val decision = PolicyDecisionAggregator.aggregate(
            listOf(PolicyEvaluatorResult(PolicyVerdict.ALLOW, "ok")),
        )
        assertEquals(PolicyDecisionType.APPROVED, decision.type)
    }
}
