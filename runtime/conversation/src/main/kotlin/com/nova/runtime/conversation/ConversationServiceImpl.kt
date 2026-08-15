package com.nova.runtime.conversation

import com.nova.runtime.conversation.dialogue.DialogueManager
import com.nova.runtime.conversation.events.ConversationEventPublisher
import com.nova.runtime.conversation.input.ConversationInput
import com.nova.runtime.conversation.input.UnifiedInputProcessor
import com.nova.runtime.conversation.observation.ObservationGenerator
import com.nova.runtime.conversation.response.ResponsePipeline
import com.nova.runtime.conversation.session.SessionManager
import com.nova.runtime.conversation.speech.TextToSpeech
import com.nova.runtime.conversation.state.ConversationState
import com.nova.runtime.conversation.state.ConversationStateEvent
import com.nova.runtime.conversation.state.ConversationStateMachine
import com.nova.runtime.error.NovaErrors
import com.nova.runtime.error.NovaException
import com.nova.runtime.kernel.trace.TraceContext
import com.nova.runtime.kernel.trace.TraceContextHolder
import com.nova.runtime.models.Observation
import com.nova.runtime.models.RuntimeModule
import com.nova.runtime.utils.logging.NovaLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class ConversationServiceImpl(
    private val sessionManager: SessionManager,
    private val dialogueManager: DialogueManager,
    private val inputProcessor: UnifiedInputProcessor,
    private val observationGenerator: ObservationGenerator,
    private val responsePipeline: ResponsePipeline,
    private val eventPublisher: ConversationEventPublisher,
    private val textToSpeech: TextToSpeech,
    private val traceContextHolder: TraceContextHolder,
    private val logger: NovaLogger,
    private val coroutineScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : ConversationService {

    private val stateMachines = ConcurrentHashMap<UUID, ConversationStateMachine>()
    private val responseStreams = ConcurrentHashMap<UUID, MutableSharedFlow<String>>()
    private val sessionMutexes = ConcurrentHashMap<UUID, Mutex>()
    private val responseJobs = ConcurrentHashMap<UUID, Job>()

    override suspend fun startSession(): UUID {
        val session = sessionManager.createSession()
        stateMachines[session.sessionId] = ConversationStateMachine(ConversationState.IDLE)
        responseStreams.getOrPut(session.sessionId) { MutableSharedFlow(extraBufferCapacity = 64) }

        logger.info(RuntimeModule.CONVERSATION.name, "Session started", session.traceId)
        eventPublisher.publishStarted(session.sessionId, session.traceId)

        return session.sessionId
    }

    override suspend fun stopSession(sessionId: UUID) {
        withSessionLock(sessionId) {
            responseJobs.remove(sessionId)?.cancel()
            val session = sessionManager.endSession(sessionId)
            val stateMachine = stateMachineFor(sessionId)
            runCatching { stateMachine.transition(ConversationStateEvent.END_SESSION) }
            textToSpeech.stop(sessionId.toString())
            dialogueManager.clearSession(sessionId)
            eventPublisher.publishCompleted(sessionId, session.traceId, session.turnCount)
            stateMachines.remove(sessionId)
            responseStreams.remove(sessionId)
            sessionMutexes.remove(sessionId)
            logger.info(RuntimeModule.CONVERSATION.name, "Session stopped", session.traceId)
        }
    }

    override suspend fun receiveVoice(sessionId: UUID, audioPayload: ByteArray): Observation? =
        receiveInput(ConversationInput.Voice(sessionId, audioPayload))

    override suspend fun receiveText(sessionId: UUID, text: String): Observation =
        receiveInput(ConversationInput.Text(sessionId, text))
            ?: throw NovaException(NovaErrors.internal("Empty text input rejected"))

    override fun streamResponse(sessionId: UUID): Flow<String> =
        responseStreams.getOrPut(sessionId) { MutableSharedFlow(extraBufferCapacity = 64) }

    suspend fun resumeSession(sessionId: UUID? = null): UUID {
        val session = sessionManager.resumeSession(sessionId)
        stateMachines.getOrPut(session.sessionId) {
            ConversationStateMachine(session.state)
        }
        responseStreams.getOrPut(session.sessionId) { MutableSharedFlow(extraBufferCapacity = 64) }
        return session.sessionId
    }

    private suspend fun receiveInput(input: ConversationInput): Observation? =
        withSessionLock(input.sessionId) {
            val session = sessionManager.requireSession(input.sessionId)
            if (!session.isActive) {
                throw NovaException(NovaErrors.internal("Session ${input.sessionId} is not active"))
            }

            val previousTrace = traceContextHolder.current()
            traceContextHolder.set(TraceContext(traceId = session.traceId))
            try {
                receiveInputLocked(input, session)
            } finally {
                if (previousTrace == null) {
                    traceContextHolder.clear()
                } else {
                    traceContextHolder.set(previousTrace)
                }
            }
        }

    private suspend fun receiveInputLocked(
        input: ConversationInput,
        session: com.nova.runtime.conversation.session.ConversationSessionRecord,
    ): Observation? {
        val stateMachine = stateMachineFor(input.sessionId)
        handleInterruptionIfNeeded(input.sessionId, session.traceId, stateMachine)

        if (input is ConversationInput.Voice) {
            stateMachine.transition(ConversationStateEvent.START_LISTENING)
            sessionManager.updateState(input.sessionId, stateMachine.current())
        }

        val normalized = inputProcessor.normalize(input) ?: return null

        stateMachine.transition(ConversationStateEvent.RECEIVE_INPUT)
        sessionManager.updateState(input.sessionId, stateMachine.current())

        val updatedSession = sessionManager.incrementTurn(input.sessionId)
        val observation = observationGenerator.fromInput(
            input = normalized,
            sessionTraceId = session.traceId,
            turnNumber = updatedSession.turnCount,
        )

        val turn = dialogueManager.beginTurn(input.sessionId, observation)
        eventPublisher.publishObservationReceived(observation)

        startResponseDelivery(
            sessionId = input.sessionId,
            traceId = session.traceId,
            observation = observation,
            turnNumber = turn.turnNumber,
            stateMachine = stateMachine,
        )

        return observation
    }

    private fun startResponseDelivery(
        sessionId: UUID,
        traceId: UUID,
        observation: Observation,
        turnNumber: Int,
        stateMachine: ConversationStateMachine,
    ) {
        responseJobs.remove(sessionId)?.cancel()
        responseJobs[sessionId] = coroutineScope.launch {
            try {
                stateMachine.transition(ConversationStateEvent.RESPONSE_READY)
                sessionManager.updateState(sessionId, stateMachine.current())

                val response = responsePipeline.process(observation)
                val stream = responseStreams.getOrPut(sessionId) {
                    MutableSharedFlow(extraBufferCapacity = 64)
                }

                for (token in responsePipeline.streamTokens(response)) {
                    stream.emit(token)
                }

                dialogueManager.completeTurn(sessionId, turnNumber, response)
                textToSpeech.speak(sessionId.toString(), response)

                stateMachine.transition(ConversationStateEvent.RESPONSE_COMPLETE)
                sessionManager.updateState(sessionId, stateMachine.current())

                logger.info(
                    RuntimeModule.CONVERSATION.name,
                    "Turn $turnNumber response delivered",
                    traceId,
                )
            } catch (_: CancellationException) {
                logger.info(RuntimeModule.CONVERSATION.name, "Response delivery cancelled", traceId)
            }
        }
    }

    private suspend fun handleInterruptionIfNeeded(
        sessionId: UUID,
        traceId: UUID,
        stateMachine: ConversationStateMachine,
    ) {
        val current = stateMachine.current()
        val hasActiveResponse = responseJobs[sessionId]?.isActive == true
        if (
            current == ConversationState.SPEAKING ||
            current == ConversationState.PROCESSING ||
            hasActiveResponse
        ) {
            responseJobs.remove(sessionId)?.cancel()
            textToSpeech.stop(sessionId.toString())
            dialogueManager.cancelInProgressTurn(sessionId)
            if (current != ConversationState.INTERRUPTED) {
                runCatching { stateMachine.transition(ConversationStateEvent.INTERRUPT) }
            }
            sessionManager.updateState(sessionId, stateMachine.current())
            eventPublisher.publishInterrupted(sessionId, traceId)
            stateMachine.transition(ConversationStateEvent.ACKNOWLEDGE_INTERRUPT)
            sessionManager.updateState(sessionId, stateMachine.current())
            logger.info(RuntimeModule.CONVERSATION.name, "Conversation interrupted", traceId)
        }
    }

    private fun stateMachineFor(sessionId: UUID): ConversationStateMachine =
        stateMachines.getOrPut(sessionId) { ConversationStateMachine() }

    private suspend fun <T> withSessionLock(sessionId: UUID, block: suspend () -> T): T {
        val mutex = sessionMutexes.getOrPut(sessionId) { Mutex() }
        return mutex.withLock { block() }
    }
}
