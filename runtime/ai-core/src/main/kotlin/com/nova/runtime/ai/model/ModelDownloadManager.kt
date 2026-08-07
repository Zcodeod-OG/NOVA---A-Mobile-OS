package com.nova.runtime.ai.model

import kotlinx.coroutines.flow.StateFlow

/** Ensures all ONNX models are present under [ModelLoadConfig.modelsDirectory]. */
interface ModelDownloadManager {
    val state: StateFlow<ModelDownloadSessionState>

    /** Copies bundled assets and downloads missing remote models when network is available. */
    suspend fun ensureAllModels()

    fun cancel()
}
