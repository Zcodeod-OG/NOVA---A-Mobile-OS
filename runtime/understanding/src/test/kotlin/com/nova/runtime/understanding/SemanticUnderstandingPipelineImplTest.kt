package com.nova.runtime.understanding

import com.nova.runtime.events.InMemoryEventBus
import com.nova.runtime.events.understanding.UnderstandingEvents
import com.nova.runtime.inference.AdaptiveInferenceEngineStub
import com.nova.runtime.kernel.trace.DefaultTraceIdGenerator
import com.nova.runtime.kernel.trace.TraceContextHolder
import com.nova.runtime.models.Modality
import com.nova.runtime.models.Observation
import com.nova.runtime.understanding.constraint.PlaceholderConstraintExtractor
import com.nova.runtime.understanding.entity.PlaceholderEntityExtractor
import com.nova.runtime.understanding.events.UnderstandingEventPublisher
import com.nova.runtime.understanding.intent.PlaceholderIntentClassifier
import com.nova.runtime.understanding.nir.DefaultNirGenerator
import com.nova.runtime.understanding.normalization.DefaultObservationNormalizer
import com.nova.runtime.understanding.routing.StubInferenceRoutingHook
import com.nova.runtime.understanding.validation.DefaultNirValidator
import com.nova.runtime.utils.logging.StructuredLogger
import kotlinx.coroutines.test.runTest
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SemanticUnderstandingPipelineImplTest {

    private val logger = StructuredLogger()
    private val eventBus = InMemoryEventBus(logger)
    private val traceContextHolder = TraceContextHolder(DefaultTraceIdGenerator())

    private fun createPipeline(): SemanticUnderstandingPipelineImpl =
        SemanticUnderstandingPipelineImpl(
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
        )

    @Test
    fun process_generatesValidatedNirAndPublishesEvents() = runTest {
        val pipeline = createPipeline()
        val observation = sampleObservation("Remind me tomorrow at 3pm")

        val nir = pipeline.process(observation)

        assertNotNull(nir)
        assertEquals("set_reminder", nir.goal)
        assertTrue(nir.entities.isNotEmpty())
        assertTrue(nir.requiredCapabilities.contains("alarm"))

        val eventTypes = eventBus.publishedEvents().map { it.eventType }
        assertTrue(UnderstandingEvents.OBSERVATION_NORMALIZED in eventTypes)
        assertTrue(UnderstandingEvents.ENTITY_RESOLVED in eventTypes)
        assertTrue(UnderstandingEvents.INTENT_DETECTED in eventTypes)
        assertTrue(UnderstandingEvents.CONSTRAINT_EXTRACTED in eventTypes)
        assertTrue(UnderstandingEvents.NIR_GENERATED in eventTypes)
    }

    @Test
    fun process_blankPayloadReturnsNullWithoutNirGenerated() = runTest {
        val pipeline = createPipeline()
        val observation = sampleObservation("   ")

        val nir = pipeline.process(observation)

        assertNull(nir)
        val eventTypes = eventBus.publishedEvents().map { it.eventType }
        assertTrue(UnderstandingEvents.OBSERVATION_NORMALIZED in eventTypes)
        assertTrue(UnderstandingEvents.NIR_GENERATED !in eventTypes)
    }

    @Test
    fun process_voiceModalityIsSupported() = runTest {
        val pipeline = createPipeline()
        val observation = sampleObservation(
            payload = "call john",
            modality = Modality.VOICE,
        )

        val nir = pipeline.process(observation)

        assertNotNull(nir)
        assertEquals("make_phone_call", nir.goal)
        assertEquals("voice", nir.constraints["modality"])
    }

    private fun sampleObservation(
        payload: String,
        modality: Modality = Modality.TEXT,
    ): Observation = Observation(
        id = UUID.randomUUID(),
        timestamp = Instant.now(),
        sessionId = UUID.randomUUID(),
        traceId = UUID.randomUUID(),
        modality = modality,
        payload = payload,
        metadata = mapOf("source" to "test"),
    )
}
