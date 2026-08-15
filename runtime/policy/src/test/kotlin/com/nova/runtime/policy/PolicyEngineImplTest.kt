package com.nova.runtime.policy

import com.nova.runtime.events.InMemoryEventBus
import com.nova.runtime.events.policy.PolicyEvents
import com.nova.runtime.models.ActionNode
import com.nova.runtime.models.Nag
import com.nova.runtime.models.Priority
import com.nova.runtime.models.contracts.ActionPolicyRequest
import com.nova.runtime.models.contracts.PolicyDecisionType
import com.nova.runtime.models.contracts.PolicyRequest
import com.nova.runtime.policy.context.DefaultPolicyEnvironment
import com.nova.runtime.policy.evaluator.BatteryPolicyEvaluator
import com.nova.runtime.policy.evaluator.ConfirmationPolicyEvaluator
import com.nova.runtime.policy.evaluator.PermissionPolicyEvaluator
import com.nova.runtime.policy.evaluator.PrivacyPolicyEvaluator
import com.nova.runtime.policy.evaluator.SafetyPolicyEvaluator
import com.nova.runtime.policy.events.PolicyEventPublisher
import com.nova.runtime.utils.logging.StructuredLogger
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class PolicyEngineImplTest {

    private val logger = StructuredLogger()
    private val eventBus = InMemoryEventBus(logger)

    @Test
    fun evaluateAction_deniesDangerousOperation() = runTest {
        val engine = createEngine()
        val result = engine.evaluateAction(
            ActionPolicyRequest(
                node = node("danger", inputs = mapOf("operation" to "factory_reset")),
                traceId = UUID.randomUUID(),
            ),
        )
        val success = assertIs<com.nova.runtime.models.contracts.PolicyEvaluationResult.Success>(result)
        assertEquals(PolicyDecisionType.REJECTED, success.decision.type)
        assertTrue(PolicyEvents.ACTION_DENIED in eventBus.publishedEvents().map { it.eventType })
    }

    @Test
    fun evaluate_graphAggregatesWorstDecision() = runTest {
        val engine = createEngine()
        val safe = node("safe")
        val sensitive = node(
            "delete",
            inputs = mapOf("requiresConfirmation" to "true"),
        )
        val graph = Nag(
            graphId = UUID.randomUUID(),
            metadata = emptyMap(),
            taskHierarchy = emptyList(),
            actionNodes = listOf(safe, sensitive),
            dependencies = emptyMap(),
            executionPolicies = emptyMap(),
        )

        val result = engine.evaluate(PolicyRequest(graph = graph, traceId = UUID.randomUUID()))
        val success = assertIs<com.nova.runtime.models.contracts.PolicyEvaluationResult.Success>(result)
        assertEquals(PolicyDecisionType.REQUIRES_USER_CONFIRMATION, success.decision.type)
    }

    private fun createEngine(): PolicyEngineImpl =
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

    private fun node(
        key: String,
        inputs: Map<String, String> = emptyMap(),
    ): ActionNode =
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
}
