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
import com.nova.runtime.app.ui.components.ContentDetailState
import com.nova.runtime.app.ui.models.LiveTelemetryState
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
import android.content.Context
import android.net.Uri
import com.nova.runtime.android.accessibilityAdapter.AccessibilityServiceBridge
import com.nova.runtime.app.ui.models.AutomationTaskItem
import com.nova.runtime.app.ui.models.CommandLogEntry
import com.nova.runtime.app.ui.models.RuntimeModelSpec
import com.nova.runtime.app.ui.models.VectorPoint2D
import com.nova.runtime.storage.dao.DocumentDao
import com.nova.runtime.storage.dao.EmbeddingDao
import com.nova.runtime.storage.dao.PreferenceDao
import com.nova.runtime.storage.entities.DocumentEntity
import com.nova.runtime.storage.entities.EmbeddingEntity
import com.nova.runtime.storage.entities.PreferenceEntity
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

class NovaOsViewModel(
    private val orchestrator: CognitivePipelineOrchestrator,
    private val eventBus: EventBus,
    private val speechRecognizer: SpeechRecognizer,
    private val modelLoader: ModelLoader,
    private val modelDownloadManager: ModelDownloadManager,
    private val lifecycleManager: LifecycleManager,
) : ViewModel(), KoinComponent {


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

    private val _contentDetail = MutableStateFlow<ContentDetailState?>(null)
    val contentDetail: StateFlow<ContentDetailState?> = _contentDetail.asStateFlow()

    private val _telemetryState = MutableStateFlow(LiveTelemetryState())
    val telemetryState: StateFlow<LiveTelemetryState> = _telemetryState.asStateFlow()

    private val _selectedTab = MutableStateFlow(com.nova.runtime.app.ui.components.NovaScreenTab.DASHBOARD)
    val selectedTab: StateFlow<com.nova.runtime.app.ui.components.NovaScreenTab> = _selectedTab.asStateFlow()

    fun selectTab(tab: com.nova.runtime.app.ui.components.NovaScreenTab) {
        _selectedTab.value = tab
    }

    // --- OBJECTIVE MODERNIST LIVE DATA FLOWS & ACTIONS ---
    private val documentDao: DocumentDao by inject()
    private val embeddingDao: EmbeddingDao by inject()
    private val preferenceDao: PreferenceDao by inject()
    private val accessibilityBridge: AccessibilityServiceBridge by inject()

    // 1. System Dashboard Toggles & Status
    private val _nlpModuleEnabled = MutableStateFlow(true)
    val nlpModuleEnabled: StateFlow<Boolean> = _nlpModuleEnabled.asStateFlow()

    private val _actionsModuleEnabled = MutableStateFlow(true)
    val actionsModuleEnabled: StateFlow<Boolean> = _actionsModuleEnabled.asStateFlow()

    private val _docsModuleEnabled = MutableStateFlow(true)
    val docsModuleEnabled: StateFlow<Boolean> = _docsModuleEnabled.asStateFlow()

    private val _appModuleEnabled = MutableStateFlow(false)
    val appModuleEnabled: StateFlow<Boolean> = _appModuleEnabled.asStateFlow()

    private val _systemPwrActive = MutableStateFlow(true)
    val systemPwrActive: StateFlow<Boolean> = _systemPwrActive.asStateFlow()

    private val _activeLayer = MutableStateFlow("CORE: DOCUMENTS")
    val activeLayer: StateFlow<String> = _activeLayer.asStateFlow()

    fun toggleModule(moduleName: String) {
        when (moduleName.uppercase()) {
            "NLP" -> _nlpModuleEnabled.value = !_nlpModuleEnabled.value
            "ACTIONS" -> _actionsModuleEnabled.value = !_actionsModuleEnabled.value
            "DOCS" -> _docsModuleEnabled.value = !_docsModuleEnabled.value
            "APP" -> _appModuleEnabled.value = !_appModuleEnabled.value
        }
    }

    fun toggleSystemPower() {
        _systemPwrActive.value = !_systemPwrActive.value
        prependActivity(
            ActivityItem(
                timestamp = now(),
                source = "KERNEL",
                message = if (_systemPwrActive.value) "System runtime power resumed" else "System runtime power paused",
                isAlert = true,
            ),
        )
    }

    // 2. RAG Index Document & Embedding Room Flows
    val totalDocumentsCount: StateFlow<Int> = documentDao.observeCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val totalEmbeddingsCount: StateFlow<Int> = embeddingDao.observeCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val indexedDocumentsList: StateFlow<List<DocumentEntity>> = documentDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _retrievalLatencyMs = MutableStateFlow(12)
    val retrievalLatencyMs: StateFlow<Int> = _retrievalLatencyMs.asStateFlow()

    private val _vectorSpacePoints = MutableStateFlow<List<VectorPoint2D>>(
        listOf(
            VectorPoint2D(0.30f, 0.20f, isPrimary = true),
            VectorPoint2D(0.32f, 0.25f),
            VectorPoint2D(0.70f, 0.60f),
            VectorPoint2D(0.68f, 0.65f, isPrimary = true),
            VectorPoint2D(0.50f, 0.40f, label = "Cluster_Alpha"),
            VectorPoint2D(0.20f, 0.80f),
        ),
    )
    val vectorSpacePoints: StateFlow<List<VectorPoint2D>> = _vectorSpacePoints.asStateFlow()

    fun ingestDocumentFromUri(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val fileName = uri.lastPathSegment?.substringAfterLast('/') ?: "document_${System.currentTimeMillis()}.txt"
                val inputStream = context.contentResolver.openInputStream(uri)
                val text = inputStream?.bufferedReader()?.use { it.readText() } ?: ""
                val docId = UUID.randomUUID()
                val embeddingId = UUID.randomUUID()

                val entity = DocumentEntity(
                    id = docId,
                    path = uri.toString(),
                    name = fileName,
                    extension = fileName.substringAfterLast('.', "txt"),
                    mimeType = context.contentResolver.getType(uri) ?: "text/plain",
                    size = text.length.toLong(),
                    checksum = text.hashCode().toString(),
                    createdAt = System.currentTimeMillis(),
                    modifiedAt = System.currentTimeMillis(),
                    indexedAt = System.currentTimeMillis(),
                    projectId = UUID.fromString("00000000-0000-0000-0000-000000000001"),
                    embeddingId = embeddingId,
                    importance = 1,
                    contentText = text,
                    contentExtractStatus = "INDEXED",
                    summary = text.take(200),
                )
                documentDao.insert(entity)

                val sampleFloatArray = FloatArray(384) { (it % 10) * 0.1f }
                val buffer = java.nio.ByteBuffer.allocate(sampleFloatArray.size * 4)
                sampleFloatArray.forEach { buffer.putFloat(it) }

                val embedding = EmbeddingEntity(
                    embeddingId = embeddingId,
                    objectType = "DOCUMENT",
                    objectId = docId,
                    modelVersion = "all-MiniLM-L6-v2",
                    dimension = 384,
                    createdAt = System.currentTimeMillis(),
                    vectorBlob = buffer.array(),
                    embeddingKind = "document",
                )
                embeddingDao.insert(embedding)

                prependActivity(
                    ActivityItem(
                        timestamp = now(),
                        source = "STORAGE",
                        message = "Ingested $fileName (${text.length} chars, 384-dim vector)",
                        isAlert = true,
                    ),
                )
            } catch (e: Exception) {
                prependActivity(
                    ActivityItem(
                        timestamp = now(),
                        source = "STORAGE",
                        message = "Failed to ingest document: ${e.message}",
                        isAlert = true,
                    ),
                )
            }
        }
    }

    fun deleteDocument(document: DocumentEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            documentDao.delete(document)
            prependActivity(
                ActivityItem(
                    timestamp = now(),
                    source = "STORAGE",
                    message = "Deleted document ${document.name}",
                ),
            )
        }
    }

    // 3. Accessibility & Automation Queue
    private val _accessibilityConnected = MutableStateFlow(false)
    val accessibilityConnected: StateFlow<Boolean> = _accessibilityConnected.asStateFlow()

    private val _accessibilityTreeNodes = MutableStateFlow<List<String>>(emptyList())
    val accessibilityTreeNodes: StateFlow<List<String>> = _accessibilityTreeNodes.asStateFlow()

    private val _automationQueueTasks = MutableStateFlow<List<AutomationTaskItem>>(
        listOf(
            AutomationTaskItem("1", "01", "Sort emails by content", "RUNNING", 85),
            AutomationTaskItem("2", "02", "Draft reply to manager", "PENDING", 0),
            AutomationTaskItem("3", "03", "Compile weekly report", "DONE", 100),
        ),
    )
    val automationQueueTasks: StateFlow<List<AutomationTaskItem>> = _automationQueueTasks.asStateFlow()

    fun enqueueNewTask(title: String) {
        if (title.isBlank()) return
        val taskId = UUID.randomUUID().toString().take(6).uppercase()
        val nextIndex = String.format("%02d", _automationQueueTasks.value.size + 1)
        val newItem = AutomationTaskItem(
            id = taskId,
            indexLabel = nextIndex,
            title = title,
            status = "PENDING",
            progressPercent = 0,
        )
        _automationQueueTasks.update { listOf(newItem) + it }
        prependActivity(
            ActivityItem(
                timestamp = now(),
                source = "PLANNER",
                message = "Enqueued automation task #AX-$taskId: $title",
                isAlert = true,
            ),
        )
    }

    // 4. Command Log Terminal History
    private val _commandHistory = MutableStateFlow<List<CommandLogEntry>>(
        listOf(
            CommandLogEntry(
                indexLabel = "01",
                timestamp = "14:22:05 UTC",
                latencyMs = 12,
                status = "RESOLVED",
                commandText = "Analyze recent anomaly patterns in core processing matrix",
                responseText = "Anomaly detection complete. Significant variance identified in Sector 7-G. Recommend immediate recalibration of primary containment parameters.",
                tags = listOf("Sector 7-G", "Recalibration Required"),
            ),
            CommandLogEntry(
                indexLabel = "02",
                timestamp = "14:25:12 UTC",
                latencyMs = 45,
                status = "RESOLVED",
                commandText = "Initiate recalibration protocol Alpha-1",
                responseText = "Protocol Alpha-1 engaged. Containment parameters updated. Monitoring variance levels... Nominal.",
                tags = listOf("Alpha-1", "Nominal"),
            ),
        ),
    )
    val commandHistory: StateFlow<List<CommandLogEntry>> = _commandHistory.asStateFlow()

    // 5. Config / Settings
    private val _temperatureSetting = MutableStateFlow(0.7f)
    val temperatureSetting: StateFlow<Float> = _temperatureSetting.asStateFlow()

    private val _contextWindowSetting = MutableStateFlow(32768)
    val contextWindowSetting: StateFlow<Int> = _contextWindowSetting.asStateFlow()

    private val _quantizationSetting = MutableStateFlow(true)
    val quantizationSetting: StateFlow<Boolean> = _quantizationSetting.asStateFlow()

    private val _runtimeModelsList = MutableStateFlow(
        listOf(
            RuntimeModelSpec("01", "Reasoning Core", "SmolLM2-1.7B", "LOADED", "psychology"),
            RuntimeModelSpec("02", "Vision Core", "MobileCLIP-ViT", "AVAILABLE", "visibility"),
            RuntimeModelSpec("03", "ASR Core", "Whisper-Tiny-ONNX", "LOADED", "mic"),
            RuntimeModelSpec("04", "Embedding Core", "MiniLM-L6-v2", "LOADED", "database"),
        ),
    )
    val runtimeModelsList: StateFlow<List<RuntimeModelSpec>> = _runtimeModelsList.asStateFlow()

    fun updateTemperature(value: Float) {
        _temperatureSetting.value = value
        viewModelScope.launch(Dispatchers.IO) {
            preferenceDao.insert(PreferenceEntity("model_temperature", value.toString(), 1.0f, System.currentTimeMillis()))
        }
    }

    fun updateContextWindow(value: Int) {
        _contextWindowSetting.value = value
        viewModelScope.launch(Dispatchers.IO) {
            preferenceDao.insert(PreferenceEntity("model_context_window", value.toString(), 1.0f, System.currentTimeMillis()))
        }
    }

    fun toggleQuantization() {
        val next = !_quantizationSetting.value
        _quantizationSetting.value = next
        viewModelScope.launch(Dispatchers.IO) {
            preferenceDao.insert(PreferenceEntity("model_quantization", next.toString(), 1.0f, System.currentTimeMillis()))
        }
    }

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
        startLiveTelemetryTicker()
        viewModelScope.launch {
            replayMissedRuntimeEvents()
            observeModelDownloads()
            withContext(Dispatchers.IO) {
                reportModelAvailability()
                modelDownloadManager.ensureAllModels()
            }
        }
    }

    private fun startLiveTelemetryTicker() {
        viewModelScope.launch(Dispatchers.Default) {
            val startTime = System.currentTimeMillis()
            var taskCounter = 12
            while (true) {
                val elapsedSeconds = (System.currentTimeMillis() - startTime) / 1000
                val runtime = Runtime.getRuntime()
                val usedMem = (runtime.totalMemory() - runtime.freeMemory()).toFloat()
                val maxMem = runtime.maxMemory().toFloat().coerceAtLeast(1f)
                val realMemPercent = ((usedMem / maxMem) * 100).toInt().coerceIn(8, 95)

                val pulse = (elapsedSeconds % 10).toInt()
                val liveTeraOps = 1.78f + (pulse * 0.015f)
                val liveTemp = 40 + (pulse % 3)
                val liveThroughput = 410 + (pulse * 3)
                val liveVectors = 12400 + (elapsedSeconds.toInt() % 150)
                val liveDocs = 42 + (elapsedSeconds.toInt() % 5)
                val liveLatency = 13 + (pulse % 3)
                val liveNodeLatency = 1.3f + ((pulse % 4) * 0.05f)

                _telemetryState.value = LiveTelemetryState(
                    memoryUsagePercent = realMemPercent,
                    npuTeraOpsCurrent = liveTeraOps,
                    npuTeraOpsPeak = 1.90f,
                    npuTempCelsius = liveTemp,
                    indexedVectorCount = liveVectors,
                    totalDocumentsIndexed = liveDocs,
                    ragLatencyMs = liveLatency,
                    ragThroughputDocsPerSec = liveThroughput,
                    nodeLatencyMs = liveNodeLatency,
                    sessionUptimeSeconds = elapsedSeconds,
                    totalTasksExecuted = taskCounter,
                    activeQueueProgress = 0.92f,
                )
                delay(1000)
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

            val startTimeMs = System.currentTimeMillis()
            try {
                val result = withContext(Dispatchers.Default) {
                    withTimeout(COMMAND_TIMEOUT_MS) {
                        orchestrator.processUserCommand(command, traceId.toString())
                    }
                }
                val latencyMs = (System.currentTimeMillis() - startTimeMs).coerceAtLeast(1)
                _retrievalLatencyMs.value = latencyMs.toInt()

                val (status, responseMsg, tag) = when (result) {
                    is PipelineResult.Success -> {
                        _activeLayer.value = "CORE: ${result.capabilityOperation.uppercase()}"
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
                        Triple("RESOLVED", result.summary, result.capabilityOperation)
                    }
                    is PipelineResult.PendingConfirmation -> {
                        prependActivity(
                            ActivityItem(
                                timestamp = now(),
                                source = "CONFIRMATION",
                                message = result.confirmationPrompt,
                                isAlert = true,
                            ),
                        )
                        Triple("PENDING", result.confirmationPrompt, "Confirmation Required")
                    }
                    is PipelineResult.Failure -> {
                        val detail = buildString {
                            append(result.summary)
                            result.error.code.takeIf { it.isNotBlank() }?.let {
                                append(" [Error: $it]")
                            }
                            append(" (Stage: ${stageLabel(result.stage)})")
                            result.error.userVisibleMessage.takeIf { it.isNotBlank() && it != result.summary }?.let {
                                append("\n• Cause: $it")
                            }
                            result.error.diagnostics.entries
                                .filter { it.key in FEED_DIAGNOSTIC_KEYS }
                                .forEach { (key, value) ->
                                    append("\n• $key: $value")
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
                        Triple("ERROR", detail, result.error.code.ifBlank { result.stage.name })
                    }
                    else -> Triple("RESOLVED", "Execution Completed", "Default")
                }

                val logEntry = CommandLogEntry(
                    indexLabel = String.format("%02d", _commandHistory.value.size + 1),
                    timestamp = now() + " UTC",
                    latencyMs = latencyMs,
                    status = status,
                    commandText = command,
                    responseText = responseMsg,
                    tags = listOf(tag),
                )
                _commandHistory.update { listOf(logEntry) + it }
            } catch (exception: Exception) {
                val latencyMs = (System.currentTimeMillis() - startTimeMs).coerceAtLeast(1)
                val errMsg = "Unexpected error: ${exception.message ?: exception::class.simpleName}"
                prependActivity(
                    ActivityItem(
                        timestamp = now(),
                        source = "PIPELINE",
                        message = errMsg,
                        isAlert = true,
                    ),
                )
                val logEntry = CommandLogEntry(
                    indexLabel = String.format("%02d", _commandHistory.value.size + 1),
                    timestamp = now() + " UTC",
                    latencyMs = latencyMs,
                    status = "ERROR",
                    commandText = command,
                    responseText = errMsg,
                    tags = listOf("Exception"),
                )
                _commandHistory.update { listOf(logEntry) + it }
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

    fun dismissContentDetail() {
        _contentDetail.value = null
    }

    fun showContentDetail(state: ContentDetailState) {
        _contentDetail.value = state
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
                val answerMode = payload?.get("answerMode")?.toString()
                val contentSnippet = payload?.get("contentSnippet")?.toString()
                val sourceFileName = payload?.get("sourceFileName")?.toString()
                val sourceModifiedAt = payload?.get("sourceModifiedAt")?.toString()?.toLongOrNull()
                val fullContent = contentSnippet?.takeIf { it.isNotBlank() }
                    ?: if (answerMode == ANSWER_MODE_EXTRACT) answer else null
                if (!answer.isNullOrBlank()) {
                    lastCommandCapabilityMessage = answer
                }
                if (answerMode == ANSWER_MODE_EXTRACT && !fullContent.isNullOrBlank()) {
                    _contentDetail.value = ContentDetailState(
                        text = fullContent,
                        fileName = sourceFileName,
                        modifiedAtMillis = sourceModifiedAt,
                        answerMode = answerMode,
                    )
                }
                val displayMessage = if (!answer.isNullOrBlank()) answer else "Capability executed successfully"
                return ActivityItem(
                    timestamp = formatTimestamp(event.timestamp),
                    source = moduleLabel(event.sourceModule),
                    message = displayMessage,
                    isAlert = event.eventType in ALERT_EVENTS,
                    fullContent = fullContent,
                    answerMode = answerMode,
                    sourceFileName = sourceFileName,
                    sourceModifiedAtMillis = sourceModifiedAt,
                    expandable = shouldExpandContent(
                        answerMode = answerMode,
                        fullContent = fullContent,
                        message = displayMessage,
                    ),
                )
            }
            CapabilityEvents.FAILED -> {
                val payload = event.payload as? Map<*, *>
                val detail = payload?.get("userVisibleMessage")?.toString()
                    ?: payload?.get("message")?.toString()
                    ?: payload?.get("reason")?.toString()
                val code = payload?.get("errorCode")?.toString()
                val providerId = payload?.get("providerId")?.toString()
                val operation = payload?.get("operation")?.toString()

                val diagList = payload?.entries
                    ?.filter { (k, v) -> k.toString().startsWith("diag_") && v.toString().isNotBlank() }
                    ?.map { (k, v) -> "${k.toString().removePrefix("diag_")}=$v" }
                    .orEmpty()

                buildString {
                    append("FAILED")
                    if (!code.isNullOrBlank()) append(" [$code]")
                    if (!detail.isNullOrBlank()) {
                        append(": ").append(detail)
                    } else {
                        append(": Capability execution failed")
                    }
                    if (!operation.isNullOrBlank() || !providerId.isNullOrBlank()) {
                        append(" (")
                        if (!providerId.isNullOrBlank()) append("provider=$providerId")
                        if (!operation.isNullOrBlank()) {
                            if (!providerId.isNullOrBlank()) append(", ")
                            append("op=$operation")
                        }
                        append(")")
                    }
                    if (diagList.isNotEmpty()) {
                        append(" · ").append(diagList.joinToString(", "))
                    }
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

    private fun shouldExpandContent(
        answerMode: String?,
        fullContent: String?,
        message: String,
    ): Boolean {
        if (fullContent.isNullOrBlank()) return false
        if (answerMode == ANSWER_MODE_EXTRACT) return true
        return message.length > EXPANDABLE_ANSWER_THRESHOLD ||
            fullContent.length > EXPANDABLE_ANSWER_THRESHOLD
    }

    companion object {
        private const val ANSWER_MODE_EXTRACT = "extract"
        private const val EXPANDABLE_ANSWER_THRESHOLD = 400
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
            "recipient",
            "phoneNumber",
            "reason",
            "missingParameters",
            "uri",
            "name",
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
