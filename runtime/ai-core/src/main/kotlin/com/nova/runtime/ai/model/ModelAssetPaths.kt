package com.nova.runtime.ai.model

/**
 * Expected on-device model filenames per TDD §20 / AIS §5.
 *
 * Models are user-supplied and loaded from [ModelLoadConfig.modelsDirectory]
 * or copied from app assets on first launch.
 */
object ModelAssetPaths {
    const val EMBEDDING_MODEL = "embedding-mini.onnx"
    const val EMBEDDING_VOCAB = "vocab.txt"
    const val LLM_LIGHT_MODEL = "llm-light.onnx"
    const val LLM_FULL_MODEL = "llm-full.onnx"
    const val WHISPER_MODEL = "whisper-tiny.onnx"

    /**
     * MediaPipe Gemma 3 1B IT int4 task bundle for grounded document Q&A.
     * Not used by the stub ONNX generative path — MediaPipeLocalLlmEngine loads this.
     * Source: litert-community/Gemma3-1B-IT (Hugging Face, gated — accept Gemma license).
     */
    const val GEMMA_TASK_MODEL = "Gemma3-1B-IT_multi-prefill-seq_q4_ekv2048.task"
    const val GEMMA_TASK_MIN_BYTES = 200_000_000L

    /**
     * MobileCLIP-S0 image encoder ONNX export (~15 MB).
     * Input: pixel_values float32 [1, 3, 224, 224] NCHW, ImageNet-normalized RGB.
     * Output: image_embeds float32 [1, 512] (L2-normalized by runtime if needed).
     * Download/conversion owned by Agent 2 — not bundled in MVP assets.
     */
    const val IMAGE_EMBEDDING_MODEL = "image-encoder-mobileclip-s0.onnx"

    const val EMBEDDING_MODEL_VERSION = "all-MiniLM-L6-v2-onnx"
    const val LLM_LIGHT_MODEL_VERSION = "llm-light-onnx-v1"
    const val LLM_FULL_MODEL_VERSION = "llm-full-onnx-v1"
    const val WHISPER_MODEL_VERSION = "whisper-tiny-onnx-v1"
    const val GEMMA_TASK_MODEL_VERSION = "gemma-3-1b-it-int4-mediapipe-v1"
    const val IMAGE_EMBEDDING_MODEL_VERSION = "mobileclip-s0-image-onnx-v1"

    const val DEFAULT_EMBEDDING_DIMENSION = 384
    const val DEFAULT_IMAGE_EMBEDDING_DIMENSION = 512
    const val IMAGE_EMBEDDING_INPUT_SIZE = 224

    /** Classpath / test resource path for all-MiniLM-L6-v2 vocab. */
    const val TOKENIZER_VOCAB = "tokenizer/vocab.txt"

    /** Android assets path for MiniLM vocab (under `app/src/main/assets/`). */
    const val TOKENIZER_VOCAB_ASSET = "tokenizer/vocab.txt"

    const val TOKENIZER_CONFIG_ASSET = "tokenizer/tokenizer_config.json"

    const val TOKENIZER_SPECIAL_TOKENS_ASSET = "tokenizer/special_tokens_map.json"

    val ALL_REQUIRED = listOf(
        EMBEDDING_MODEL,
        LLM_LIGHT_MODEL,
        LLM_FULL_MODEL,
        WHISPER_MODEL,
    )

    /** Bundled in APK assets — copied to filesDir on first run. */
    val ASSET_BUNDLED = listOf(EMBEDDING_MODEL)

    /** Fetched over HTTP on first run when network is available. */
    val REMOTE_DOWNLOAD = listOf(
        WHISPER_MODEL,
        GEMMA_TASK_MODEL,
        LLM_LIGHT_MODEL,
        LLM_FULL_MODEL,
    )

    /** Optional vision models — hash fallback used when absent. */
    val ALL_OPTIONAL = listOf(IMAGE_EMBEDDING_MODEL)
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
