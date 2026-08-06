package com.nova.runtime.ai.model

sealed class EmbeddingResult {
    data class Success(
        val vector: FloatArray,
        val modelVersion: String,
        val dimension: Int,
    ) : EmbeddingResult() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Success) return false
            return vector.contentEquals(other.vector) &&
                modelVersion == other.modelVersion &&
                dimension == other.dimension
        }

        override fun hashCode(): Int {
            var result = vector.contentHashCode()
            result = 31 * result + modelVersion.hashCode()
            result = 31 * result + dimension
            return result
        }
    }

    data class Failure(val message: String, val recoverable: Boolean = true) : EmbeddingResult()
}

/** On-device text embedding generation contract (DPS §4.3). */
interface EmbeddingGenerator {
    val modelVersion: String
    val dimension: Int
    val isLoaded: Boolean

    suspend fun embed(text: String): EmbeddingResult
}

/** Deterministic hash-based fallback for dev/CI when ONNX model is absent. */
class HashEmbeddingGenerator(
    override val dimension: Int = ModelAssetPaths.DEFAULT_EMBEDDING_DIMENSION,
    override val modelVersion: String = "hash-fallback-v1",
) : EmbeddingGenerator {
    override val isLoaded: Boolean = true

    override suspend fun embed(text: String): EmbeddingResult {
        if (text.isBlank()) {
            return EmbeddingResult.Failure("Cannot embed empty text")
        }
        val vector = FloatArray(dimension)
        val normalized = text.trim().lowercase()
        for (i in normalized.indices) {
            val bucket = (normalized[i].code + i * 31) % dimension
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
