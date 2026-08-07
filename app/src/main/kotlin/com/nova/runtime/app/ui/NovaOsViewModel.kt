package com.nova.runtime.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nova.runtime.ai.model.ModelAssetPaths
import com.nova.runtime.ai.model.ModelLoader
import com.nova.runtime.app.ui.components.ActivityItem
import com.nova.runtime.conversation.speech.SpeechRecognizer
import com.nova.runtime.events.EventBus
import com.nova.runtime.events.RuntimeEvent
import com.nova.runtime.events.capability.CapabilityEvents
import com.nova.runtime.events.execution.ExecutionEvents
import com.nova.runtime.events.planner.PlannerEvents
import com.nova.runtime.events.reasoning.ReasoningEvents
import com.nova.runtime.events.storage.StorageEvents
import com.nova.runtime.events.system.SystemEvents
import com.nova.runtime.events.understanding.UnderstandingEvents
import com.nova.runtime.kernel.lifecycle.LifecycleManager
import com.nova.runtime.models.RuntimeLifecycleState
import com.nova.runtime.models.RuntimeModule
import com.nova.runtime.orchestrator.CognitivePipelineOrchestrator
import com.nova.runtime.orchestrator.PipelineResult
import com.nova.runtime.orchestrator.PipelineStage
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class NovaOsViewModel(
    private val orchestrator: CognitivePipelineOrchestrator,
    private val eventBus: EventBus,
    private val speechRecognizer: SpeechRecognizer,
    private val modelLoader: ModelLoader,
    private val lifecycleManager: LifecycleManager,
) : ViewModel() {

    private val _lifecycleState = MutableStateFlow(RuntimeLifecycleState.CREATED)
    val lifecycleState: StateFlow<RuntimeLifecycleState> = _lifecycleState.asStateFlow()

    private val _activityFeed = MutableStateFlow<List<ActivityItem>>(emptyList())
    val activityFeed: StateFlow<List<ActivityItem>> = _activityFeed.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _isRecordingVoice = MutableStateFlow(false)
    val isRecordingVoice: StateFlow<Boolean> = _isRecordingVoice.asStateFlow()

    private val _voiceStatusMessage = MutableStateFlow<String?>(null)
    val voiceStatusMessage: StateFlow<String?> = _voiceStatusMessage.asStateFlow()

    private val _whisperAvailable = MutableStateFlow(false)
    val whisperAvailable: StateFlow<Boolean> = _whisperAvailable.asStateFlow()

    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
        .withZone(ZoneId.systemDefault())

    private val commandMutex = Mutex()

    init {
        _lifecycleState.value = lifecycleManager.state.value
        subscribeToRuntimeEvents()
        seedBootEvents()
        viewModelScope.launch {
            replayMissedRuntimeEvents()
            withContext(Dispatchers.IO) {
                reportModelAvailability()
            }
        }
    }

    private suspend fun reportModelAvailability() {
        val report = modelLoader.availabilityReport()
        report.forEach { model ->
            val sizeMb = model.sizeBytes / (1024 * 1024)
            if (model.fileName == ModelAssetPaths.WHISPER_MODEL) {
                _whisperAvailable.value = model.available
                if (!model.available) {
                    _voiceStatusMessage.value =
                        "Whisper ONNX not installed — MIC uses device speech recognition."
                }
            }
            prependActivity(
                ActivityItem(
                    timestamp = now(),
                    source = "MODELS",
                    message = if (model.available) {
                        "${model.fileName} available (${sizeMb} MB)"
                    } else {
                        "${model.fileName} missing (optional fallback active)"
                    },
                    isAlert = model.available,
                ),
            )
        }
    }

    fun updateLifecycleState(state: RuntimeLifecycleState) {
        _lifecycleState.value = state
    }

    fun setVoiceRecording(active: Boolean, listeningHint: String = "Listening… tap mic to stop.") {
        _isRecordingVoice.value = active
        if (active) {
            _voiceStatusMessage.value = listeningHint
        }
    }

    fun clearVoiceStatus() {
        _voiceStatusMessage.value = null
    }

    fun reportVoiceError(message: String) {
        _voiceStatusMessage.value = message
        prependActivity(
            ActivityItem(
                timestamp = now(),
                source = "VOICE",
                message = message,
                isAlert = true,
            ),
        )
    }

    fun submitPlatformSpeechTranscript(transcript: String) {
        if (transcript.isBlank()) return
        _voiceStatusMessage.value = null
        prependActivity(
            ActivityItem(
                timestamp = now(),
                source = "VOICE",
                message = "Heard: $transcript",
                isAlert = true,
            ),
        )
        submitCommandInternal(transcript)
    }

    fun submitVoiceCommand(audioPayload: ByteArray) {
        if (audioPayload.isEmpty()) return

        viewModelScope.launch {
            _isRecordingVoice.value = false
            val sessionId = UUID.randomUUID().toString()
            val transcript = speechRecognizer.transcribe(sessionId, audioPayload)

            if (transcript.isNullOrBlank() || isStubTranscript(transcript)) {
                _voiceStatusMessage.value =
                    "Could not transcribe audio. Add whisper-tiny.onnx to assets/models/ or type your command."
                prependActivity(
                    ActivityItem(
                        timestamp = now(),
                        source = "VOICE",
                        message = "Voice transcription failed — no usable speech detected.",
                        isAlert = true,
                    ),
                )
                return@launch
            }

            _voiceStatusMessage.value = null
            prependActivity(
                ActivityItem(
                    timestamp = now(),
                    source = "VOICE",
                    message = "Transcribed: $transcript",
                    isAlert = true,
                ),
            )

            processCommand(transcript)
        }
    }

    fun submitCommand(command: String) {
        submitCommandInternal(command)
    }

    private fun submitCommandInternal(command: String) {
        if (command.isBlank()) return
        viewModelScope.launch {
            processCommand(command)
        }
    }

    private suspend fun processCommand(command: String) {
        commandMutex.withLock {
            _isProcessing.value = true
            val traceId = UUID.randomUUID()
            prependActivity(
                ActivityItem(
                    timestamp = now(),
                    source = "USER",
                    message = command,
                    isAlert = true,
                ),
            )
            prependActivity(
                ActivityItem(
                    timestamp = now(),
                    source = "PIPELINE",
                    message = "Processing command (trace=${traceId.toString().take(8)})…",
                ),
            )

            try {
                val result = withContext(Dispatchers.Default) {
                    withTimeout(COMMAND_TIMEOUT_MS) {
                        orchestrator.processUserCommand(command, traceId.toString())
                    }
                }
                when (result) {
                    is PipelineResult.Success -> {
                        prependActivity(
                            ActivityItem(
                                timestamp = now(),
                                source = "EXECUTION",
                                message = result.summary,
                                isAlert = true,
                            ),
                        )
                    }
                    is PipelineResult.Failure -> {
                        prependActivity(
                            ActivityItem(
                                timestamp = now(),
                                source = stageLabel(result.stage),
                                message = "Failed: ${result.summary}",
                                isAlert = true,
                            ),
                        )
                    }
                }
            } catch (exception: Exception) {
                prependActivity(
                    ActivityItem(
                        timestamp = now(),
                        source = "PIPELINE",
                        message = "Unexpected error: ${exception.message ?: exception::class.simpleName}",
                        isAlert = true,
                    ),
                )
            } finally {
                _isProcessing.value = false
            }
        }
    }

    private fun subscribeToRuntimeEvents() {
        val trackedEvents = setOf(
            SystemEvents.RUNTIME_STARTED,
            SystemEvents.RUNTIME_READY,
            SystemEvents.RUNTIME_ERROR,
            StorageEvents.INDEXING_STARTED,
            StorageEvents.INDEXING_COMPLETED,
            UnderstandingEvents.INTENT_DETECTED,
            UnderstandingEvents.NIR_GENERATED,
            ReasoningEvents.STARTED,
            ReasoningEvents.COMPLETED,
            PlannerEvents.PLANNING_STARTED,
            PlannerEvents.GRAPH_BUILT,
            PlannerEvents.PLANNING_COMPLETED,
            ExecutionEvents.STARTED,
            ExecutionEvents.NODE_COMPLETED,
            ExecutionEvents.GRAPH_COMPLETED,
            ExecutionEvents.GRAPH_FAILED,
            CapabilityEvents.EXECUTED,
            CapabilityEvents.FAILED,
        )

        eventBus.subscribe(
            object : com.nova.runtime.events.EventSubscriber {
                override val subscriberId = "nova-os-ui"
                override val eventTypes = trackedEvents

                override suspend fun onEvent(event: RuntimeEvent) {
                    formatEvent(event)?.let { item ->
                        prependActivity(item)
                    }
                    if (event.eventType == SystemEvents.RUNTIME_READY) {
                        _lifecycleState.value = RuntimeLifecycleState.READY
                        reportModelAvailability()
                    }
                }
            },
        )
    }

    private fun seedBootEvents() {
        _activityFeed.value = listOf(
            ActivityItem(now(), "KERNEL", "System boot initiated. Loading models and runtime modules…"),
        )
    }

    private suspend fun replayMissedRuntimeEvents() {
        eventBus.publishedEvents()
            .filter { it.eventType in BOOT_REPLAY_EVENTS }
            .forEach { event ->
                formatEvent(event)?.let { prependActivity(it) }
            }
        _lifecycleState.value = lifecycleManager.state.value
    }

    private fun formatEvent(event: RuntimeEvent): ActivityItem? {
        val message = when (event.eventType) {
            SystemEvents.RUNTIME_STARTED ->
                "Runtime modules initializing…"
            SystemEvents.RUNTIME_READY ->
                "Runtime lifecycle transitioned to READY"
            SystemEvents.RUNTIME_ERROR -> {
                val payload = event.payload
                if (payload is com.nova.runtime.events.system.RuntimeErrorPayload) {
                    "Runtime error: ${payload.code} — ${payload.message}"
                } else {
                    "Runtime error reported"
                }
            }
            StorageEvents.INDEXING_STARTED ->
                (event.payload as? Map<*, *>)?.get("message")?.toString()
                    ?: "Indexing gallery photos and downloads…"
            StorageEvents.INDEXING_COMPLETED ->
                (event.payload as? Map<*, *>)?.get("message")?.toString()
                    ?: "Gallery indexing completed"
            UnderstandingEvents.INTENT_DETECTED -> "Intent detected"
            UnderstandingEvents.NIR_GENERATED -> "NIR generated and validated"
            ReasoningEvents.STARTED -> "Reasoning engine started"
            ReasoningEvents.COMPLETED -> "Reasoning completed"
            PlannerEvents.PLANNING_STARTED -> "Planning started"
            PlannerEvents.GRAPH_BUILT -> "Action graph built"
            PlannerEvents.PLANNING_COMPLETED -> "Planning completed"
            ExecutionEvents.STARTED -> "Execution runtime started"
            ExecutionEvents.NODE_COMPLETED -> "Action node completed"
            ExecutionEvents.GRAPH_COMPLETED -> "Graph execution completed"
            ExecutionEvents.GRAPH_FAILED -> "Graph execution failed"
            CapabilityEvents.EXECUTED -> "Capability executed successfully"
            CapabilityEvents.FAILED -> "Capability execution failed"
            else -> return null
        }

        return ActivityItem(
            timestamp = formatTimestamp(event.timestamp),
            source = moduleLabel(event.sourceModule),
            message = message,
            isAlert = event.eventType in ALERT_EVENTS,
        )
    }

    private fun prependActivity(item: ActivityItem) {
        _activityFeed.update { current ->
            listOf(item) + current.take(MAX_FEED_ITEMS - 1)
        }
    }

    private fun now(): String = timeFormatter.format(Instant.now())

    private fun formatTimestamp(instant: Instant): String = timeFormatter.format(instant)

    private fun moduleLabel(module: RuntimeModule): String = when (module) {
        RuntimeModule.KERNEL -> "KERNEL"
        RuntimeModule.UNDERSTANDING -> "SUP"
        RuntimeModule.REASONING -> "REASONING"
        RuntimeModule.PLANNER -> "PLANNER"
        RuntimeModule.EXECUTION -> "EXECUTION"
        RuntimeModule.POLICY -> "POLICY"
        RuntimeModule.CAPABILITY -> "CAPABILITY"
        RuntimeModule.CONVERSATION -> "CONVERSATION"
        RuntimeModule.INFERENCE -> "INFERENCE"
        RuntimeModule.MEMORY -> "MEMORY"
        else -> module.name
    }

    private fun stageLabel(stage: PipelineStage): String = when (stage) {
        PipelineStage.UNDERSTANDING -> "SUP"
        PipelineStage.REASONING -> "REASONING"
        PipelineStage.PLANNING -> "PLANNER"
        PipelineStage.POLICY -> "POLICY"
        PipelineStage.EXECUTION -> "EXECUTION"
    }

    companion object {
        private const val MAX_FEED_ITEMS = 50
        private const val COMMAND_TIMEOUT_MS = 60_000L
        private val BOOT_REPLAY_EVENTS = setOf(
            SystemEvents.RUNTIME_STARTED,
            SystemEvents.RUNTIME_READY,
        )
        private val ALERT_EVENTS = setOf(
            SystemEvents.RUNTIME_READY,
            ExecutionEvents.GRAPH_COMPLETED,
            ExecutionEvents.GRAPH_FAILED,
            CapabilityEvents.EXECUTED,
            CapabilityEvents.FAILED,
        )

        private fun isStubTranscript(text: String): Boolean {
            val trimmed = text.trim()
            return trimmed.startsWith("voice input (") && trimmed.endsWith(" bytes)")
        }
    }
}
