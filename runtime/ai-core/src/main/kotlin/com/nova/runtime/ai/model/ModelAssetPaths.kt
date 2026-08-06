package com.nova.runtime.ai.model

/**
 * Expected on-device model filenames per TDD §20 / AIS §5.
 *
 * Models are user-supplied and loaded from [ModelLoadConfig.modelsDirectory]
 * or copied from app assets on first launch.
 */
object ModelAssetPaths {
    const val EMBEDDING_MODEL = "embedding-mini.onnx"
    const val LLM_LIGHT_MODEL = "llm-light.onnx"
    const val LLM_FULL_MODEL = "llm-full.onnx"
    const val WHISPER_MODEL = "whisper-tiny.onnx"

    const val EMBEDDING_MODEL_VERSION = "all-MiniLM-L6-v2-onnx"
    const val LLM_LIGHT_MODEL_VERSION = "llm-light-onnx-v1"
    const val LLM_FULL_MODEL_VERSION = "llm-full-onnx-v1"
    const val WHISPER_MODEL_VERSION = "whisper-tiny-onnx-v1"

    const val DEFAULT_EMBEDDING_DIMENSION = 384

    val ALL_REQUIRED = listOf(
        EMBEDDING_MODEL,
        LLM_LIGHT_MODEL,
        LLM_FULL_MODEL,
        WHISPER_MODEL,
    )
}

data class ModelLoadConfig(
    val modelsDirectory: String,
    val copyFromAssetsOnFirstLaunch: Boolean = true,
)

data class ModelAvailability(
    val fileName: String,
    val path: String,
    val available: Boolean,
    val sizeBytes: Long = 0L,
)
