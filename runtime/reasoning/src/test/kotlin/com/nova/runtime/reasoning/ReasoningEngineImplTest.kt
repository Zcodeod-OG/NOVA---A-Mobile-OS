package com.nova.runtime.reasoning

import com.nova.runtime.events.InMemoryEventBus
import com.nova.runtime.events.reasoning.ReasoningEvents
import com.nova.runtime.models.Nir
import com.nova.runtime.models.contracts.MemoryResult
import com.nova.runtime.models.contracts.ReasoningEngineResult
import com.nova.runtime.models.contracts.ReasoningRequest
import com.nova.runtime.reasoning.ambiguity.DefaultAmbiguityResolver
import com.nova.runtime.reasoning.confidence.DefaultConfidenceScorer
import com.nova.runtime.reasoning.context.DefaultReasoningContextBuilder
import com.nova.runtime.reasoning.events.ReasoningEventPublisher
import com.nova.runtime.reasoning.evidence.DefaultEvidenceRanker
import com.nova.runtime.reasoning.explanation.DefaultExplanationGenerator
import com.nova.runtime.utils.logging.StructuredLogger
import java.util.UUID
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ReasoningEngineImplTest {

    private val logger = StructuredLogger()
    private val eventBus = InMemoryEventBus(logger)

    private fun createEngine(): ReasoningEngineImpl =
        ReasoningEngineImpl(
            evidenceRanker = DefaultEvidenceRanker(),
            ambiguityResolver = DefaultAmbiguityResolver(),
            confidenceScorer = DefaultConfidenceScorer(),
            contextBuilder = DefaultReasoningContextBuilder(),
            explanationGenerator = DefaultExplanationGenerator(),
            eventPublisher = ReasoningEventPublisher(eventBus),
            logger = logger,
        )

    @Test
    fun reason_sameInputTwice_producesIdenticalOutput() = runTest {
        val engine = createEngine()
        val request = sampleRequest()

        val first = engine.reason(request)
        val second = engine.reason(request)

        assertIs<ReasoningEngineResult.Success>(first)
        assertIs<ReasoningEngineResult.Success>(second)
        assertEquals(first.context, second.context)
    }

    @Test
    fun reason_resolvesAmbiguousEntityFromRankedEvidence() = runTest {
        val engine = createEngine()
        val result = engine.reason(
            ReasoningRequest(
                nir = Nir(
                    version = 1,
                    goal = "send message to john",
                    entities = listOf("john"),
                    constraints = emptyMap(),
                    context = emptyMap(),
                    requiredCapabilities = listOf("communication"),
                    confidence = 0.8,
                ),
                memoryResults = MemoryResult.Success(
                    entries = listOf(
                        memoryEntry(
                            id = "mem-1",
                            source = "contact",
                            content = "John Smith is a contact",
                            timestamp = 1000L,
                            confidence = 0.9,
                        ),
                        memoryEntry(
                            id = "mem-2",
                            source = "contact",
                            content = "John Doe is a contact",
                            timestamp = 2000L,
                            confidence = 0.7,
                        ),
                    ),
                ),
                traceId = TRACE_ID,
            ),
        )

        assertIs<ReasoningEngineResult.Success>(result)
        assertTrue(result.context.resolvedEntities.values.any { it.contains("John Doe") })
        assertTrue(result.context.assumptions.any { it.contains("john") })
        assertTrue(result.context.evidence.isNotEmpty())
        assertTrue(result.context.recommendations.any { it.contains("Confidence factor") })
    }

    @Test
    fun reason_publishesReasoningEvents() = runTest {
        val engine = createEngine()
        engine.reason(sampleRequest())

        val eventTypes = eventBus.publishedEvents().map { it.eventType }
        assertTrue(ReasoningEvents.STARTED in eventTypes)
        assertTrue(ReasoningEvents.EVIDENCE_COLLECTED in eventTypes)
        assertTrue(ReasoningEvents.AMBIGUITY_RESOLVED in eventTypes)
        assertTrue(ReasoningEvents.COMPLETED in eventTypes)
    }

    @Test
    fun reason_memoryFailure_returnsFailure() = runTest {
        val engine = createEngine()
        val result = engine.reason(
            ReasoningRequest(
                nir = sampleNir(),
                memoryResults = MemoryResult.Failure(
                    com.nova.runtime.models.RuntimeError(
                        code = "MEMORY_NOT_IMPLEMENTED",
                        category = com.nova.runtime.models.ErrorCategory.INFRASTRUCTURE,
                        severity = com.nova.runtime.models.ErrorSeverity.LOW,
                        recoverable = true,
                        userVisibleMessage = "Memory unavailable",
                    ),
                ),
                traceId = TRACE_ID,
            ),
        )

        assertIs<ReasoningEngineResult.Failure>(result)
    }

    private fun sampleRequest(): ReasoningRequest =
        ReasoningRequest(
            nir = sampleNir(),
            memoryResults = MemoryResult.Success(
                entries = listOf(
                    memoryEntry(
                        id = "mem-a",
                        source = "conversation",
                        content = "Reminder to call john tomorrow",
                        timestamp = 5000L,
                        confidence = 0.85,
                    ),
                    memoryEntry(
                        id = "mem-b",
                        source = "calendar",
                        content = "Meeting with team tomorrow",
                        timestamp = 3000L,
                        confidence = 0.75,
                    ),
                ),
            ),
            traceId = TRACE_ID,
        )

    private fun sampleNir(): Nir =
        Nir(
            version = 1,
            goal = "remind me to call john tomorrow",
            entities = listOf("john", "tomorrow"),
            constraints = mapOf("time" to "tomorrow"),
            context = emptyMap(),
            requiredCapabilities = listOf("time", "notifications"),
            confidence = 0.82,
        )

    private fun memoryEntry(
        id: String,
        source: String,
        content: String,
        timestamp: Long,
        confidence: Double,
    ): Map<String, String> =
        mapOf(
            "id" to id,
            "source" to source,
            "content" to content,
            "timestamp" to timestamp.toString(),
            "confidence" to confidence.toString(),
        )

    companion object {
        private val TRACE_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000099")
    }
}
