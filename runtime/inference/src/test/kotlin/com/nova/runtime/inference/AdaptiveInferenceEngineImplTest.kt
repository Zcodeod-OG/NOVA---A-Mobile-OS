package com.nova.runtime.inference

import com.nova.runtime.events.InMemoryEventBus
import com.nova.runtime.events.inference.InferenceEvents
import com.nova.runtime.inference.budget.DefaultContextBudgetManager
import com.nova.runtime.inference.complexity.DefaultComplexityAnalyzer
import com.nova.runtime.inference.events.InferenceEventPublisher
import com.nova.runtime.inference.metrics.DefaultInferenceMetrics
import com.nova.runtime.inference.model.DeterministicInferenceModel
import com.nova.runtime.inference.model.LightweightInferenceModel
import com.nova.runtime.inference.model.RuleEngineInferenceModel
import com.nova.runtime.inference.prompt.DefaultPromptManager
import com.nova.runtime.inference.registry.DefaultModelRegistry
import com.nova.runtime.inference.resource.DefaultResourceAdvisor
import com.nova.runtime.inference.resource.DeviceResourceSnapshot
import com.nova.runtime.inference.scheduler.DefaultInferenceScheduler
import com.nova.runtime.inference.tier.InferenceTier
import com.nova.runtime.models.contracts.InferenceRequest
import com.nova.runtime.models.contracts.InferenceResult
import com.nova.runtime.utils.logging.StructuredLogger
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AdaptiveInferenceEngineImplTest {

    private val logger = StructuredLogger()
    private val eventBus = InMemoryEventBus(logger)
    private val metrics = DefaultInferenceMetrics()

    private fun createEngine(
        resources: () -> DeviceResourceSnapshot = { DeviceResourceSnapshot() },
    ): AdaptiveInferenceEngineImpl =
        AdaptiveInferenceEngineImpl(
            complexityAnalyzer = DefaultComplexityAnalyzer(),
            modelRegistry = DefaultModelRegistry(
                initialModels = listOf(
                    DeterministicInferenceModel(),
                    RuleEngineInferenceModel(),
                    LightweightInferenceModel(),
                ),
            ),
            promptManager = DefaultPromptManager(),
            contextBudgetManager = DefaultContextBudgetManager(),
            scheduler = DefaultInferenceScheduler(),
            metrics = metrics,
            eventPublisher = InferenceEventPublisher(eventBus),
            resourceAdvisor = DefaultResourceAdvisor(),
            deviceResources = resources,
            logger = logger,
        )

    @Test
    fun infer_shortPrompt_usesDeterministicTier() = runTest {
        val engine = createEngine()
        val result = engine.infer(request("hello"))

        assertIs<InferenceResult.Success>(result)
        assertEquals(InferenceTier.DETERMINISTIC.level, result.tierUsed)
        assertTrue(result.output.startsWith("ack:"))

        val eventTypes = eventBus.publishedEvents().map { it.eventType }
        assertTrue(InferenceEvents.INFERENCE_STARTED in eventTypes)
        assertTrue(InferenceEvents.TIER_SELECTED in eventTypes)
        assertTrue(InferenceEvents.INFERENCE_COMPLETED in eventTypes)
    }

    @Test
    fun infer_mediumPrompt_usesLightTier() = runTest {
        val engine = createEngine()
        val result = engine.infer(request("remind me tomorrow at three pm please"))

        assertIs<InferenceResult.Success>(result)
        assertEquals(InferenceTier.LIGHT.level, result.tierUsed)
        assertTrue(result.output.startsWith("rule:"))
    }

    @Test
    fun infer_complexPrompt_usesFullTier() = runTest {
        val engine = createEngine()
        val result = engine.infer(
            request("what should I do if I need to schedule a meeting and send a message to john tomorrow afternoon"),
        )

        assertIs<InferenceResult.Success>(result)
        assertEquals(InferenceTier.FULL.level, result.tierUsed)
        assertTrue(result.output.startsWith("full:"))
    }

    @Test
    fun infer_resourcePressure_downgradesTier() = runTest {
        val engine = createEngine {
            DeviceResourceSnapshot(batteryLow = true)
        }
        val result = engine.infer(
            request("what should I do if I need to schedule a meeting and send a message to john tomorrow afternoon"),
        )

        assertIs<InferenceResult.Success>(result)
        assertEquals(InferenceTier.LIGHT.level, result.tierUsed)
    }

    @Test
    fun infer_tierHintOverridesComplexity() = runTest {
        val engine = createEngine()
        val result = engine.infer(
            InferenceRequest(
                prompt = "hello",
                tierHint = InferenceTier.FULL.level,
                traceId = TRACE_ID,
            ),
        )

        assertIs<InferenceResult.Success>(result)
        assertEquals(InferenceTier.FULL.level, result.tierUsed)
    }

    private fun request(prompt: String): InferenceRequest =
        InferenceRequest(
            prompt = prompt,
            traceId = TRACE_ID,
        )

    companion object {
        private const val TRACE_ID = "00000000-0000-0000-0000-000000000099"
    }
}
