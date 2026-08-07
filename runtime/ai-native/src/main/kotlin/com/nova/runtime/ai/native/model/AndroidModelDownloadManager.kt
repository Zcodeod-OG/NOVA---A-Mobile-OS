package com.nova.runtime.ai.native.model

import android.content.Context
import com.nova.runtime.ai.model.ModelAssetPaths
import com.nova.runtime.ai.model.ModelDownloadCatalog
import com.nova.runtime.ai.model.ModelDownloadManager
import com.nova.runtime.ai.model.ModelDownloadPhase
import com.nova.runtime.ai.model.ModelDownloadSessionState
import com.nova.runtime.ai.model.ModelFilePhase
import com.nova.runtime.ai.model.ModelFileProgress
import com.nova.runtime.ai.model.ModelLoader
import com.nova.runtime.ai.model.ModelReadiness
import com.nova.runtime.ai.model.ResumableFileDownloader
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** First-run model provisioning: asset copy + resumable HuggingFace downloads into filesDir/models. */
class AndroidModelDownloadManager(
    context: Context,
    private val modelLoader: ModelLoader,
    private val downloader: ResumableFileDownloader = ResumableFileDownloader(),
    private val connectivity: NetworkAvailabilityChecker = AndroidNetworkConnectivity(context),
    modelsDirectory: File = File(context.filesDir, "models"),
) : ModelDownloadManager {

    private val modelsDir = modelsDirectory.also { it.mkdirs() }
    private val mutex = Mutex()
    private val cancelled = AtomicBoolean(false)

    private val _state = MutableStateFlow(ModelDownloadSessionState())
    override val state: StateFlow<ModelDownloadSessionState> = _state.asStateFlow()

    override suspend fun ensureAllModels() = mutex.withLock {
        if (_state.value.phase == ModelDownloadPhase.COMPLETE && isFullyReady(_state.value.readiness)) {
            return
        }

        cancelled.set(false)
        updatePhase(
            phase = ModelDownloadPhase.PREPARING,
            message = "Checking on-device model inventory…",
            isActive = true,
        )

        val initialProgress = buildInitialProgress()
        _state.update { current ->
            current.copy(
                files = initialProgress,
                overallProgress = computeOverallProgress(initialProgress),
                readiness = readinessFromProgress(initialProgress),
            )
        }

        copyBundledAssets()

        val afterAssets = refreshProgress()
        _state.update { current ->
            current.copy(
                files = afterAssets,
                overallProgress = computeOverallProgress(afterAssets),
                readiness = readinessFromProgress(afterAssets),
            )
        }

        val pendingRemote = afterAssets.filter { entry ->
            val catalogEntry = ModelDownloadCatalog.entryFor(entry.fileName) ?: return@filter false
            !catalogEntry.copyFromAssets && entry.phase != ModelFilePhase.COMPLETE
        }

        if (pendingRemote.isEmpty()) {
            finishSuccess("All NOVA models are ready.")
            return
        }

        if (!connectivity.isInternetAvailable()) {
            finishOffline(pendingRemote.map { it.fileName })
            return
        }

        updatePhase(
            phase = ModelDownloadPhase.DOWNLOADING,
            message = "Downloading missing ONNX models (local-first)…",
            isActive = true,
        )

        for (pending in pendingRemote) {
            if (cancelled.get()) {
                throw CancellationException("Model downloads cancelled")
            }

            val catalogEntry = ModelDownloadCatalog.entryFor(pending.fileName) ?: continue
            val target = File(modelsDir, pending.fileName)

            if (target.isFile && target.length() >= catalogEntry.minimumValidBytes) {
                markFileComplete(pending.fileName)
                continue
            }

            updateFileProgress(pending.fileName) {
                it.copy(
                    phase = ModelFilePhase.DOWNLOADING,
                    bytesDownloaded = File(modelsDir, "${pending.fileName}.part").takeIf { file -> file.isFile }
                        ?.length()
                        ?: 0L,
                    totalBytes = catalogEntry.expectedSizeBytes,
                    errorMessage = null,
                )
            }

            val result = downloader.download(
                url = catalogEntry.downloadUrl!!,
                targetFile = target,
                minimumValidBytes = catalogEntry.minimumValidBytes,
            ) { downloaded, total ->
                updateFileProgress(pending.fileName) {
                    it.copy(
                        phase = ModelFilePhase.DOWNLOADING,
                        bytesDownloaded = downloaded,
                        totalBytes = total ?: catalogEntry.expectedSizeBytes,
                    )
                }
                refreshOverallProgress()
            }

            if (result.isFailure) {
                val error = result.exceptionOrNull()?.message ?: "Download failed"
                updateFileProgress(pending.fileName) {
                    it.copy(phase = ModelFilePhase.FAILED, errorMessage = error)
                }
                finishFailed("Failed to download ${pending.fileName}: $error")
                return
            }

            markFileComplete(pending.fileName)
        }

        finishSuccess("All NOVA models are ready.")
    }

    override fun cancel() {
        cancelled.set(true)
        _state.update { current ->
            current.copy(
                phase = ModelDownloadPhase.IDLE,
                message = "Model download cancelled",
                isActive = false,
            )
        }
    }

    private suspend fun copyBundledAssets() {
        updatePhase(
            phase = ModelDownloadPhase.COPYING_ASSET,
            message = "Copying bundled embedding model from assets…",
            isActive = true,
        )

        ModelDownloadCatalog.ASSET_FILE_NAMES.forEach { fileName ->
            updateFileProgress(fileName) {
                it.copy(phase = ModelFilePhase.COPYING)
            }
        }

        modelLoader.ensureModelsFromAssets(ModelDownloadCatalog.ASSET_FILE_NAMES)

        ModelDownloadCatalog.ASSET_FILE_NAMES.forEach { fileName ->
            val target = File(modelsDir, fileName)
            val catalogEntry = ModelDownloadCatalog.entryFor(fileName)
            if (target.isFile && catalogEntry != null && target.length() >= catalogEntry.minimumValidBytes) {
                markFileComplete(fileName)
            } else {
                updateFileProgress(fileName) {
                    it.copy(
                        phase = ModelFilePhase.FAILED,
                        errorMessage = "Bundled asset missing — add ${fileName} to assets/models/",
                    )
                }
            }
        }
    }

    private fun buildInitialProgress(): List<ModelFileProgress> =
        ModelDownloadCatalog.ENTRIES.map { entry ->
            val target = File(modelsDir, entry.fileName)
            if (target.isFile && target.length() >= entry.minimumValidBytes) {
                ModelFileProgress(
                    fileName = entry.fileName,
                    phase = ModelFilePhase.COMPLETE,
                    bytesDownloaded = target.length(),
                    totalBytes = target.length(),
                )
            } else {
                ModelFileProgress(fileName = entry.fileName)
            }
        }

    private suspend fun refreshProgress(): List<ModelFileProgress> = buildInitialProgress()

    private fun markFileComplete(fileName: String) {
        val target = File(modelsDir, fileName)
        updateFileProgress(fileName) {
            it.copy(
                phase = ModelFilePhase.COMPLETE,
                bytesDownloaded = target.length(),
                totalBytes = target.length(),
                errorMessage = null,
            )
        }
        refreshOverallProgress()
    }

    private fun updateFileProgress(
        fileName: String,
        transform: (ModelFileProgress) -> ModelFileProgress,
    ) {
        _state.update { current ->
            val files = current.files.map { progress ->
                if (progress.fileName == fileName) transform(progress) else progress
            }
            current.copy(
                files = files,
                overallProgress = computeOverallProgress(files),
                readiness = readinessFromProgress(files),
            )
        }
    }

    private fun refreshOverallProgress() {
        _state.update { current ->
            current.copy(
                overallProgress = computeOverallProgress(current.files),
                readiness = readinessFromProgress(current.files),
            )
        }
    }

    private fun updatePhase(
        phase: ModelDownloadPhase,
        message: String,
        isActive: Boolean,
    ) {
        _state.update { current ->
            current.copy(
                phase = phase,
                message = message,
                isActive = isActive,
            )
        }
    }

    private fun finishSuccess(message: String) {
        val files = _state.value.files
        _state.update { current ->
            current.copy(
                phase = ModelDownloadPhase.COMPLETE,
                message = message,
                files = files,
                overallProgress = 1f,
                readiness = readinessFromProgress(files),
                isActive = false,
            )
        }
    }

    private fun finishOffline(missing: List<String>) {
        val files = _state.value.files
        _state.update { current ->
            current.copy(
                phase = ModelDownloadPhase.OFFLINE,
                message = "No network — ${missing.size} model(s) pending: ${missing.joinToString()}",
                files = files,
                overallProgress = computeOverallProgress(files),
                readiness = readinessFromProgress(files),
                isActive = false,
            )
        }
    }

    private fun finishFailed(message: String) {
        val files = _state.value.files
        _state.update { current ->
            current.copy(
                phase = ModelDownloadPhase.FAILED,
                message = message,
                files = files,
                overallProgress = computeOverallProgress(files),
                readiness = readinessFromProgress(files),
                isActive = false,
            )
        }
    }

    private fun computeOverallProgress(files: List<ModelFileProgress>): Float {
        if (files.isEmpty()) return 0f
        val totalWeight = files.sumOf { progress ->
            val entry = ModelDownloadCatalog.entryFor(progress.fileName)
            entry?.expectedSizeBytes ?: 1L
        }.toFloat().coerceAtLeast(1f)

        val completedWeight = files.sumOf { progress ->
            val entry = ModelDownloadCatalog.entryFor(progress.fileName) ?: return@sumOf 0L
            when (progress.phase) {
                ModelFilePhase.COMPLETE, ModelFilePhase.SKIPPED ->
                    entry.expectedSizeBytes
                ModelFilePhase.DOWNLOADING, ModelFilePhase.COPYING ->
                    progress.bytesDownloaded.coerceAtMost(entry.expectedSizeBytes)
                else -> 0L
            }
        }.toFloat()

        return (completedWeight / totalWeight).coerceIn(0f, 1f)
    }

    private fun readinessFromProgress(files: List<ModelFileProgress>): ModelReadiness {
        fun ready(fileName: String): Boolean =
            files.firstOrNull { it.fileName == fileName }?.phase == ModelFilePhase.COMPLETE

        return ModelReadiness(
            embeddingReady = ready(ModelAssetPaths.EMBEDDING_MODEL),
            whisperReady = ready(ModelAssetPaths.WHISPER_MODEL),
            llmLightReady = ready(ModelAssetPaths.LLM_LIGHT_MODEL),
            llmFullReady = ready(ModelAssetPaths.LLM_FULL_MODEL),
        )
    }

    private fun isFullyReady(readiness: ModelReadiness): Boolean =
        readiness.embeddingReady &&
            readiness.whisperReady &&
            readiness.llmLightReady &&
            readiness.llmFullReady
}
