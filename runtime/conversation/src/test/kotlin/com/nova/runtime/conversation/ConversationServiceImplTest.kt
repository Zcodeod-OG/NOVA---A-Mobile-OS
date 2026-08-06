package com.nova.runtime.conversation

import com.nova.runtime.conversation.dialogue.DialogueManager
import com.nova.runtime.conversation.events.ConversationEventPublisher
import com.nova.runtime.conversation.input.UnifiedInputProcessor
import com.nova.runtime.conversation.observation.ObservationGenerator
import com.nova.runtime.conversation.response.ResponsePipeline
import com.nova.runtime.conversation.session.InMemorySessionPersistence
import com.nova.runtime.conversation.session.SessionManager
import com.nova.runtime.conversation.speech.StubSpeechRecognizer
import com.nova.runtime.conversation.speech.StubTextToSpeech
import com.nova.runtime.events.InMemoryEventBus
import com.nova.runtime.events.conversation.ConversationEvents
import com.nova.runtime.kernel.trace.DefaultTraceIdGenerator
import com.nova.runtime.kernel.trace.TraceContextHolder
import com.nova.runtime.utils.logging.StructuredLogger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ConversationServiceImplTest {

    private val logger = StructuredLogger()
    private val eventBus = InMemoryEventBus(logger)
    private val persistence = InMemorySessionPersistence()
    private val traceContextHolder = TraceContextHolder(DefaultTraceIdGenerator())

    private fun createService(scope: CoroutineScope): ConversationServiceImpl {
        val sessionManager = SessionManager(
            persistence,
            traceContextHolder,
            DefaultTraceIdGenerator(),
        )
        return ConversationServiceImpl(
            sessionManager = sessionManager,
            dialogueManager = DialogueManager(),
            inputProcessor = UnifiedInputProcessor(StubSpeechRecognizer()),
            observationGenerator = ObservationGenerator(DefaultTraceIdGenerator()),
            responsePipeline = ResponsePipeline(),
            eventPublisher = ConversationEventPublisher(eventBus),
            textToSpeech = StubTextToSpeech(),
            traceContextHolder = traceContextHolder,
            logger = logger,
            coroutineScope = scope,
        )
    }

    @Test
    fun startSession_publishesConversationStarted() = runTest {
        val service = createService(this)
        val sessionId = service.startSession()

        assertNotNull(sessionId)
        assertTrue(
            eventBus.publishedEvents().any { it.eventType == ConversationEvents.STARTED },
        )
    }

    @Test
    fun receiveText_generatesObservationAndStreamsResponse() = runTest {
        val service = createService(this)
        val sessionId = service.startSession()

        val tokens = mutableListOf<String>()
        val collectJob = launch {
            service.streamResponse(sessionId).collect { tokens.add(it) }
        }

        val observation = service.receiveText(sessionId, "hello")
        advanceUntilIdle()
        collectJob.cancel()

        assertEquals("hello", observation.payload)
        assertTrue(tokens.joinToString("").contains("Acknowledged: hello"))

        assertTrue(
            eventBus.publishedEvents().any { it.eventType == ConversationEvents.OBSERVATION_RECEIVED },
        )
    }

    @Test
    fun stopSession_publishesConversationCompleted() = runTest {
        val service = createService(this)
        val sessionId = service.startSession()
        service.stopSession(sessionId)

        assertTrue(
            eventBus.publishedEvents().any { it.eventType == ConversationEvents.COMPLETED },
        )
    }

    @Test
    fun interruption_publishesConversationInterrupted() = runTest {
        val gate = CompletableDeferred<Unit>()
        val slowPipeline = ResponsePipeline(
            responseGenerator = {
                gate.await()
                "slow response"
            },
        )
        val sessionManager = SessionManager(
            persistence,
            traceContextHolder,
            DefaultTraceIdGenerator(),
        )
        val service = ConversationServiceImpl(
            sessionManager = sessionManager,
            dialogueManager = DialogueManager(),
            inputProcessor = UnifiedInputProcessor(StubSpeechRecognizer()),
            observationGenerator = ObservationGenerator(DefaultTraceIdGenerator()),
            responsePipeline = slowPipeline,
            eventPublisher = ConversationEventPublisher(eventBus),
            textToSpeech = StubTextToSpeech(),
            traceContextHolder = traceContextHolder,
            logger = logger,
            coroutineScope = this,
        )

        val sessionId = service.startSession()
        launch { service.receiveText(sessionId, "first") }
        delay(50)
        service.receiveText(sessionId, "second")
        gate.complete(Unit)
        advanceUntilIdle()

        assertTrue(
            eventBus.publishedEvents().any { it.eventType == ConversationEvents.INTERRUPTED },
        )
    }
}

private fun ResponsePipeline(
    responseGenerator: suspend (com.nova.runtime.models.Observation) -> String,
): ResponsePipeline = ResponsePipeline(
    responseGenerator = object : com.nova.runtime.conversation.response.ConversationResponseGenerator {
        override suspend fun generate(observation: com.nova.runtime.models.Observation): String =
            responseGenerator(observation)
    },
)
