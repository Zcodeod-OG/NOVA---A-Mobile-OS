package com.nova.runtime.ai.model

/** On-device image embedding for cross-modal photo search (DPS §4.3). */
interface ImageEmbeddingGenerator {
    val modelVersion: String
    val dimension: Int
    val isLoaded: Boolean

    /** Embed visual content from raw image bytes. */
    suspend fun embedImage(imageBytes: ByteArray): EmbeddingResult

    /** Embed a text query into the shared image embedding space (CLIP-style). */
    suspend fun embedQuery(text: String): EmbeddingResult
}

/**
 * Deterministic hash-based fallback for dev/CI when the vision ONNX model is absent.
 * UTF-8 image payloads embed via the same space as [embedQuery] for testability.
 */
class HashImageEmbeddingGenerator(
    override val dimension: Int = ModelAssetPaths.DEFAULT_EMBEDDING_DIMENSION,
    override val modelVersion: String = "hash-image-fallback-v1",
) : ImageEmbeddingGenerator {
    private val textHasher = HashEmbeddingGenerator(dimension, modelVersion)

    override val isLoaded: Boolean = true

    override suspend fun embedImage(imageBytes: ByteArray): EmbeddingResult {
        if (imageBytes.isEmpty()) {
            return EmbeddingResult.Failure("Cannot embed empty image")
        }
        val concept = runCatching { imageBytes.decodeToString().trim() }.getOrNull()
            ?.takeIf { it.isNotBlank() }
        return if (concept != null) {
            embedQuery(concept)
        } else {
            embedRawBytes(imageBytes)
        }
    }

    override suspend fun embedQuery(text: String): EmbeddingResult = textHasher.embed(text)

    private fun embedRawBytes(bytes: ByteArray): EmbeddingResult {
        val vector = FloatArray(dimension)
        for (i in bytes.indices) {
            val bucket = (bytes[i].toInt() and 0xFF + i * 31) % dimension
            vector[bucket] += 1f
        }
        var norm = 0f
        for (value in vector) {
            norm += value * value
        }
        norm = kotlin.math.sqrt(norm.toDouble()).toFloat().coerceAtLeast(1e-6f)
        for (i in vector.indices) {
            vector[i] /= norm
        }
        return EmbeddingResult.Success(vector, modelVersion, dimension)
    }
}
