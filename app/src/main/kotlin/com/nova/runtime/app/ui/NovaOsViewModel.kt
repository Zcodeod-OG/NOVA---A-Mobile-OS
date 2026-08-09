package com.nova.runtime.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nova.runtime.ai.model.ModelAssetPaths
import com.nova.runtime.ai.model.ModelDownloadManager
import com.nova.runtime.ai.model.ModelDownloadPhase
import com.nova.runtime.ai.model.ModelDownloadSessionState
import com.nova.runtime.ai.model.ModelFilePhase
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

class NovaOsViewModel(
    private val orchestrator: CognitivePipelineOrchestrator,
    private val eventBus: EventBus,
    private val speechRecognizer: SpeechRecognizer,
    private val modelLoader: ModelLoader,
    private val modelDownloadManager: ModelDownloadManager,
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

    val modelDownloadState: StateFlow<ModelDownloadSessionState> = modelDownloadManager.state

    private val _modelsReadyForHeavyInference = MutableStateFlow(false)
    val modelsReadyForHeavyInference: StateFlow<Boolean> = _modelsReadyForHeavyInference.asStateFlow()

    private val _indexingStatus = MutableStateFlow<String?>(null)
    val indexingStatus: StateFlow<String?> = _indexingStatus.asStateFlow()

    private var lastLoggedDownloadPhase: ModelDownloadPhase? = null
    private val loggedFilePhases = mutableSetOf<String>()
    private var lastCommandCapabilityMessage: String? = null
    private var clearIndexingStatusJob: Job? = null
    private var lastIndexingProgressSignature: String? = null

    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
        .withZone(ZoneId.systemDefault())

    private val commandMutex = Mutex()

    init {
        _lifecycleState.value = lifecycleManager.state.value
        subscribeToRuntimeEvents()
        seedBootEvents()
        viewModelScope.launch {
            replayMissedRuntimeEvents()
            observeModelDownloads()
            withContext(Dispatchers.IO) {
                reportModelAvailability()
                modelDownloadManager.ensureAllModels()
            }
        }
    }

    fun retryModelDownloads() {
        viewModelScope.launch(Dispatchers.IO) {
            loggedFilePhases.clear()
            lastLoggedDownloadPhase = null
            prependActivity(
                ActivityItem(
                    timestamp = now(),
                    source = "MODELS",
                    message = "Retrying missing ONNX model downloads…",
                ),
            )
            modelDownloadManager.ensureAllModels()
        }
    }

    private fun observeModelDownloads() {
        viewModelScope.launch {
            modelDownloadManager.state.collectLatest { session ->
                syncReadinessFromSession(session)
                logDownloadSession(session)
            }
        }
    }

    private fun syncReadinessFromSession(session: ModelDownloadSessionState) {
        _whisperAvailable.value = session.readiness.prefersLocalWhisper
        _modelsReadyForHeavyInference.value = session.readiness.canRunHeavyInference

        _voiceStatusMessage.value = when {
            session.readiness.prefersLocalWhisper -> null
            session.phase == ModelDownloadPhase.DOWNLOADING &&
                session.files.any { it.fileName == ModelAssetPaths.WHISPER_MODEL && it.phase == ModelFilePhase.DOWNLOADING } ->
                "Downloading whisper-tiny.onnx for offline voice…"
            else ->
                "Whisper ONNX not installed — MIC uses device speech recognition (explicit fallback)."
        }
    }

    private fun logDownloadSession(session: ModelDownloadSessionState) {
        if (session.phase != lastLoggedDownloadPhase &&
            session.phase != ModelDownloadPhase.IDLE
        ) {
            session.message?.let { message ->
                prependActivity(
                    ActivityItem(
                        timestamp = now(),
                        source = "MODELS",
                        message = message,
                        isAlert = session.phase == ModelDownloadPhase.COMPLETE,
                    ),
                )
            }
            lastLoggedDownloadPhase = session.phase
        }

        session.files.forEach { file ->
            val key = "${file.fileName}:${file.phase}"
            if (key in loggedFilePhases) return@forEach

            when (file.phase) {
                ModelFilePhase.COMPLETE -> {
                    val sizeMb = file.bytesDownloaded / (1024 * 1024)
                    prependActivity(
                        ActivityItem(
                            timestamp = now(),
                            source = "MODELS",
                            message = "${file.fileName} ready (${sizeMb} MB)",
                            isAlert = true,
                        ),
                    )
                    loggedFilePhases.add(key)
                }
                ModelFilePhase.FAILED -> {
                    prependActivity(
                        ActivityItem(
                            timestamp = now(),
                            source = "MODELS",
                            message = "${file.fileName} failed: ${file.errorMessage ?: "unknown error"}",
                            isAlert = true,
                        ),
                    )
                    loggedFilePhases.add(key)
                }
                else -> Unit
            }
        }
    }

    private suspend fun reportModelAvailability() {
        val report = modelLoader.availabilityReport()
        report.forEach { model ->
            val sizeMb = model.sizeBytes / (1024 * 1024)
            val corrupt = model.sizeBytes > 0L && !model.available
            if (model.fileName == ModelAssetPaths.WHISPER_MODEL) {
                _whisperAvailable.value = model.available
            }
            if (model.fileName == ModelAssetPaths.LLM_LIGHT_MODEL || model.fileName == ModelAssetPaths.LLM_FULL_MODEL) {
                if (model.available) {
                    _modelsReadyForHeavyInference.value = true
                }
            }
            prependActivity(
                ActivityItem(
                    timestamp = now(),
                    source = "MODELS",
                    message = when {
                        model.available ->
                            "${model.fileName} available (${sizeMb} MB)"
                        corrupt ->
                            "${model.fileName} corrupt (${model.sizeBytes} bytes) — re-downloading"
                        model.fileName in ModelAssetPaths.REMOTE_DOWNLOAD ->
                            "${model.fileName} pending download"
                        else ->
                            "${model.fileName} missing — add to assets/models/"
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
                    "Could not transcribe audio. Download whisper-tiny.onnx or type your command."
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
            val downloadState = modelDownloadState.value
            if (downloadState.isActive && !downloadState.readiness.canRunHeavyInference) {
                prependActivity(
                    ActivityItem(
                        timestamp = now(),
                        source = "MODELS",
                        message = "On-device LLM still downloading — using lightweight fallbacks for this command.",
                    ),
                )
            }

            _isProcessing.value = true
            lastCommandCapabilityMessage = null
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
                        if (result.summary != lastCommandCapabilityMessage) {
                            prependActivity(
                                ActivityItem(
                                    timestamp = now(),
                                    source = "EXECUTION",
                                    message = result.summary,
                                    isAlert = true,
                                ),
                            )
                        }
                    }
                    is PipelineResult.Failure -> {
                        val detail = buildString {
                            append("Failed: ${result.summary}")
                            result.error.code.takeIf { it.isNotBlank() }?.let {
                                append(" [$it]")
                            }
                            result.error.diagnostics.entries
                                .filter { it.key in FEED_DIAGNOSTIC_KEYS }
                                .forEach { (key, value) ->
                                    append(" · $key=$value")
                                }
                        }
                        prependActivity(
                            ActivityItem(
                                timestamp = now(),
                                source = stageLabel(result.stage),
                                message = detail,
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
            StorageEvents.INDEXING_PROGRESS,
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
                    if (event.eventType == CapabilityEvents.EXECUTED) {
                        val matchDebug = (event.payload as? Map<*, *>)
                            ?.get("matchDebug")
                            ?.toString()
                            ?.takeIf { it.isNotBlank() }
                        if (matchDebug != null) {
                            prependActivity(
                                ActivityItem(
                                    timestamp = formatTimestamp(event.timestamp),
                                    source = moduleLabel(event.sourceModule),
                                    message = matchDebug,
                                ),
                            )
                        }
                    }
                    when (event.eventType) {
                        StorageEvents.INDEXING_STARTED -> {
                            lastIndexingProgressSignature = null
                            formatEvent(event)?.let { upsertIndexingFeedLine(it, live = true) }
                            updateIndexingStatus(event)
                        }
                        StorageEvents.INDEXING_PROGRESS -> {
                            val payload = event.payload as? Map<*, *>
                            if (shouldPublishIndexingProgress(payload)) {
                                formatEvent(event)?.let { upsertIndexingFeedLine(it, live = true) }
                                updateIndexingStatus(event)
                            }
                        }
                        StorageEvents.INDEXING_COMPLETED -> {
                            formatEvent(event)?.let { upsertIndexingFeedLine(it, live = false) }
                            lastIndexingProgressSignature = null
                            updateIndexingStatus(event)
                        }
                        else -> {
                            formatEvent(event)?.let { prependActivity(it) }
                            updateIndexingStatus(event)
                        }
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
                    ?: "Indexing gallery, downloads, and documents…"
            StorageEvents.INDEXING_PROGRESS ->
                formatIndexingProgressFeed(event.payload as? Map<*, *>)
                    ?: return null
            StorageEvents.INDEXING_COMPLETED ->
                (event.payload as? Map<*, *>)?.get("message")?.toString()
                    ?: "Indexing complete"
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
            ExecutionEvents.GRAPH_FAILED -> {
                val payload = event.payload as? Map<*, *>
                val code = payload?.get("errorCode")?.toString()
                val detail = payload?.get("userVisibleMessage")?.toString()
                    ?: payload?.get("message")?.toString()
                when {
                    !detail.isNullOrBlank() -> "FAILED: $detail"
                    !code.isNullOrBlank() -> "FAILED: Graph execution failed [$code]"
                    else -> "FAILED: Graph execution failed"
                }
            }
            CapabilityEvents.EXECUTED -> {
                val payload = event.payload as? Map<*, *>
                val answer = payload?.get("userMessage")?.toString()
                    ?: payload?.get("answer")?.toString()
                if (!answer.isNullOrBlank()) {
                    lastCommandCapabilityMessage = answer
                }
                if (!answer.isNullOrBlank()) answer else "Capability executed successfully"
            }
            CapabilityEvents.FAILED -> {
                val payload = event.payload as? Map<*, *>
                val detail = payload?.get("userVisibleMessage")?.toString()
                    ?: payload?.get("message")?.toString()
                val code = payload?.get("errorCode")?.toString()
                when {
                    !detail.isNullOrBlank() -> "FAILED: $detail"
                    !code.isNullOrBlank() -> "FAILED: Capability execution failed [$code]"
                    else -> "FAILED: Capability execution failed"
                }
            }
            else -> return null
        }

        if (event.eventType in SILENT_PIPELINE_EVENTS) return null

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

    /**
     * Keep at most one STORAGE indexing line in the feed.
     * While [live], the row stays replaceable; on completion it becomes a normal final line.
     */
    private fun upsertIndexingFeedLine(item: ActivityItem, live: Boolean) {
        val tagged =
            item.copy(
                source = "STORAGE",
                replaceKey = if (live) INDEXING_FEED_KEY else null,
            )
        _activityFeed.update { current ->
            val withoutLive = current.filterNot { it.replaceKey == INDEXING_FEED_KEY }
            listOf(tagged) + withoutLive.take(MAX_FEED_ITEMS - 1)
        }
    }

    /** Skip micro-batch chatter unless category / totals / summary counts change. */
    private fun shouldPublishIndexingProgress(payload: Map<*, *>?): Boolean {
        val signature = indexingProgressSignature(payload) ?: return true
        if (signature == lastIndexingProgressSignature) return false
        lastIndexingProgressSignature = signature
        return true
    }

    private fun indexingProgressSignature(payload: Map<*, *>?): String? {
        if (payload == null) return null
        val category = payload["category"]?.toString().orEmpty()
        val totalIndexed = payload["totalIndexed"]?.toString().orEmpty()
        val ready = payload["summariesReady"]?.toString().orEmpty()
        val pending = payload["summariesPending"]?.toString().orEmpty()
        val docsTotal = payload["documentsTotal"]?.toString().orEmpty()
        return "$category|$totalIndexed|$ready|$pending|$docsTotal"
    }

    private fun updateIndexingStatus(event: RuntimeEvent) {
        when (event.eventType) {
            StorageEvents.INDEXING_STARTED -> {
                clearIndexingStatusJob?.cancel()
                _indexingStatus.value = "Indexing…"
            }
            StorageEvents.INDEXING_PROGRESS -> {
                clearIndexingStatusJob?.cancel()
                _indexingStatus.value = compactIndexingStatus(event.payload as? Map<*, *>)
                    ?: "Indexing…"
            }
            StorageEvents.INDEXING_COMPLETED -> {
                val payload = event.payload as? Map<*, *>
                val pending = payload?.get("summariesPending")?.toString()?.toIntOrNull() ?: 0
                val docsTotal = payload?.get("documentsTotal")?.toString()?.toIntOrNull() ?: 0
                val failed =
                    payload?.get("message")?.toString()?.contains("failed", ignoreCase = true) == true
                when {
                    failed -> {
                        clearIndexingStatusJob?.cancel()
                        _indexingStatus.value = null
                    }
                    docsTotal > 0 && pending > 0 -> {
                        clearIndexingStatusJob?.cancel()
                        val ready = payload?.get("summariesReady")?.toString()?.toIntOrNull() ?: 0
                        _indexingStatus.value = buildCoverageStatus(docsTotal, ready, pending)
                        scheduleClearIndexingStatus(INDEXING_BACKFILL_DISPLAY_MS)
                    }
                    else -> {
                        _indexingStatus.value = buildCoverageStatus(docsTotal, pending = 0, ready = docsTotal)
                        maybePromptSparseIndexing(docsTotal)
                        scheduleClearIndexingStatus(
                            if (docsTotal > 0) INDEXING_COVERAGE_DISPLAY_MS else INDEXING_READY_DISPLAY_MS,
                        )
                    }
                }
            }
        }
    }

    private fun compactIndexingStatus(payload: Map<*, *>?): String? {
        if (payload == null) return null
        val category = readableCategory(payload["category"]?.toString()) ?: return null
        val totalIndexed = payload["totalIndexed"]?.toString() ?: "0"
        val ready = payload["summariesReady"]?.toString()?.toIntOrNull() ?: 0
        val pending = payload["summariesPending"]?.toString()?.toIntOrNull() ?: 0
        val docsTotal = payload["documentsTotal"]?.toString()?.toIntOrNull() ?: 0
        if (docsTotal > 0) {
            return buildCoverageStatus(docsTotal, ready, pending)
        }
        return "Indexing · $category · $totalIndexed"
    }

    private fun buildCoverageStatus(docsTotal: Int, ready: Int, pending: Int): String {
        if (docsTotal <= 0) return "Indexing…"
        if (pending > 0) {
            return "Documents+Downloads: $docsTotal indexed · summaries $ready/$docsTotal"
        }
        if (docsTotal < SPARSE_DOC_THRESHOLD) {
            return "Documents+Downloads: $docsTotal indexed · grant All files access for more"
        }
        return "Documents+Downloads indexed: $docsTotal"
    }

    private fun maybePromptSparseIndexing(docsTotal: Int) {
        if (docsTotal >= SPARSE_DOC_THRESHOLD) return
        prependActivity(
            ActivityItem(
                timestamp = now(),
                source = "STORAGE",
                message = "Sparse document index ($docsTotal files) — put PDFs in Documents or Downloads and grant All files access",
                isAlert = true,
            ),
        )
    }

    private fun formatIndexingProgressFeed(payload: Map<*, *>?): String? {
        if (payload == null) return null
        val fromPayload = payload["message"]?.toString()?.takeIf { it.isNotBlank() }
        val category = readableCategory(payload["category"]?.toString())
        val totalIndexed = payload["totalIndexed"]?.toString()
        if (category != null && totalIndexed != null) {
            val ready = payload["summariesReady"]?.toString()?.toIntOrNull() ?: 0
            val pending = payload["summariesPending"]?.toString()?.toIntOrNull() ?: 0
            val docsTotal = payload["documentsTotal"]?.toString()?.toIntOrNull() ?: 0
            if (docsTotal > 0) {
                val coverage = buildCoverageStatus(docsTotal, ready, pending)
                return "Indexing $category · $totalIndexed · $coverage"
            }
            return "Indexing $category · $totalIndexed"
        }
        return fromPayload
    }

    private fun readableCategory(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        return when (raw.uppercase()) {
            "PHOTOS" -> "Photos"
            "VIDEOS" -> "Videos"
            "AUDIO" -> "Audio"
            "DOWNLOADS" -> "Downloads"
            "DOCUMENTS" -> "Documents"
            else -> raw.lowercase().replace('_', ' ').replaceFirstChar { it.titlecase() }
        }
    }

    private fun scheduleClearIndexingStatus(delayMs: Long = INDEXING_READY_DISPLAY_MS) {
        clearIndexingStatusJob?.cancel()
        clearIndexingStatusJob = viewModelScope.launch {
            delay(delayMs)
            _indexingStatus.value = null
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
        private const val INDEXING_READY_DISPLAY_MS = 2_500L
        private const val INDEXING_BACKFILL_DISPLAY_MS = 8_000L
        private const val INDEXING_COVERAGE_DISPLAY_MS = 30_000L
        private const val SPARSE_DOC_THRESHOLD = 5
        private const val INDEXING_FEED_KEY = "indexing"
        private val FEED_DIAGNOSTIC_KEYS = setOf(
            "providerId",
            "operation",
            "capabilityType",
            "permission",
            "supportedOperations",
            "alarmMode",
            "triggerAtMillis",
        )
        private val BOOT_REPLAY_EVENTS = setOf(
            SystemEvents.RUNTIME_STARTED,
            SystemEvents.RUNTIME_READY,
        )
        private val SILENT_PIPELINE_EVENTS = setOf(
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
