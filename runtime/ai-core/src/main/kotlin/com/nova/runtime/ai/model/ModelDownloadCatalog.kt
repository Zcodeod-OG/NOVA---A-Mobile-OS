package com.nova.runtime.ai.model

/**
 * Remote and bundled model sources aligned with [scripts/download-gemma-task.sh],
 * [scripts/download-llm-onnx.sh], and [scripts/download-whisper-onnx.sh].
 */
object ModelDownloadCatalog {

    data class Entry(
        val fileName: String,
        val downloadUrl: String?,
        val expectedSizeBytes: Long,
        val minimumValidBytes: Long,
        val copyFromAssets: Boolean,
        val tier: ModelReadinessTier,
        val isRequired: Boolean = true,
    )

    val ENTRIES: List<Entry> = listOf(
        Entry(
            fileName = ModelAssetPaths.EMBEDDING_MODEL,
            downloadUrl = null,
            expectedSizeBytes = 23_000_000L,
            minimumValidBytes = 1_000_000L,
            copyFromAssets = true,
            tier = ModelReadinessTier.CORE,
            isRequired = true,
        ),
        Entry(
            fileName = ModelAssetPaths.WHISPER_MODEL,
            downloadUrl = WHISPER_URL,
            expectedSizeBytes = 40_000_000L,
            minimumValidBytes = 10_000_000L,
            copyFromAssets = false,
            tier = ModelReadinessTier.VOICE,
            isRequired = true,
        ),
        Entry(
            fileName = ModelAssetPaths.LLM_LIGHT_MODEL,
            downloadUrl = LLM_LIGHT_URL,
            expectedSizeBytes = 480_000_000L,
            minimumValidBytes = 100_000_000L,
            copyFromAssets = false,
            tier = ModelReadinessTier.INFERENCE_LIGHT,
            isRequired = true,
        ),
        Entry(
            fileName = ModelAssetPaths.LLM_FULL_MODEL,
            downloadUrl = LLM_FULL_URL,
            expectedSizeBytes = 1_200_000_000L,
            minimumValidBytes = 500_000_000L,
            copyFromAssets = false,
            tier = ModelReadinessTier.INFERENCE_FULL,
            isRequired = false,
        ),
        Entry(
            fileName = ModelAssetPaths.GEMMA_TASK_MODEL,
            downloadUrl = GEMMA_TASK_URL,
            expectedSizeBytes = 550_000_000L,
            minimumValidBytes = ModelAssetPaths.GEMMA_TASK_MIN_BYTES,
            copyFromAssets = false,
            tier = ModelReadinessTier.INFERENCE_LIGHT,
            isRequired = false,
        ),
    )

    val ASSET_FILE_NAMES: List<String> = ENTRIES.filter { it.copyFromAssets }.map { it.fileName }

    val REMOTE_FILE_NAMES: List<String> = ENTRIES.filter { !it.copyFromAssets }.map { it.fileName }

    fun entryFor(fileName: String): Entry? = ENTRIES.firstOrNull { it.fileName == fileName }

    private const val LLM_LIGHT_URL =
        "https://huggingface.co/onnx-community/Qwen2.5-0.5B-Instruct/resolve/main/onnx/model_q4f16.onnx"

    private const val LLM_FULL_URL =
        "https://huggingface.co/onnx-community/Qwen2.5-1.5B-Instruct/resolve/main/onnx/model_q4.onnx"

    private const val WHISPER_URL =
        "https://huggingface.co/onnx-community/whisper-tiny.en/resolve/main/onnx/encoder_model.onnx"

    /** MediaPipe-compatible Gemma 3 1B IT (int4 .task) for grounded document answers. */
    private const val GEMMA_TASK_URL =
        "https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/main/" +
            "Gemma3-1B-IT_multi-prefill-seq_q4_ekv2048.task"
}

enum class ModelReadinessTier {
    CORE,
    VOICE,
    INFERENCE_LIGHT,
    INFERENCE_FULL,
}
