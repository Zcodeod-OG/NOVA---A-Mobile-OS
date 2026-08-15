package com.nova.runtime.ai.model

enum class ModelDownloadPhase {
    IDLE,
    PREPARING,
    COPYING_ASSET,
    DOWNLOADING,
    COMPLETE,
    OFFLINE,
    FAILED,
}

enum class ModelFilePhase {
    PENDING,
    SKIPPED,
    COPYING,
    DOWNLOADING,
    COMPLETE,
    FAILED,
}

data class ModelFileProgress(
    val fileName: String,
    val phase: ModelFilePhase = ModelFilePhase.PENDING,
    val bytesDownloaded: Long = 0L,
    val totalBytes: Long? = null,
    val errorMessage: String? = null,
)

data class ModelReadiness(
    val embeddingReady: Boolean = false,
    val whisperReady: Boolean = false,
    val llmLightReady: Boolean = false,
    val llmFullReady: Boolean = false,
) {
    val coreReady: Boolean get() = embeddingReady
    val voiceReady: Boolean get() = whisperReady
    val inferenceLightReady: Boolean get() = llmLightReady
    val inferenceFullReady: Boolean get() = llmFullReady

    val canRunHeavyInference: Boolean get() = llmLightReady || llmFullReady

    val prefersLocalWhisper: Boolean get() = whisperReady
}

data class ModelDownloadSessionState(
    val phase: ModelDownloadPhase = ModelDownloadPhase.IDLE,
    val files: List<ModelFileProgress> = ModelDownloadCatalog.ENTRIES.map {
        ModelFileProgress(fileName = it.fileName)
    },
    val overallProgress: Float = 0f,
    val message: String? = null,
    val readiness: ModelReadiness = ModelReadiness(),
    val isActive: Boolean = false,
) {
    val showOverlay: Boolean
        get() = phase in setOf(
            ModelDownloadPhase.PREPARING,
            ModelDownloadPhase.COPYING_ASSET,
            ModelDownloadPhase.DOWNLOADING,
        )
}
