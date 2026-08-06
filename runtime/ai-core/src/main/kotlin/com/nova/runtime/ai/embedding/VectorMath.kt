package com.nova.runtime.ai.embedding

/** Pure-Kotlin vector math for on-device cosine similarity search (DPS §7). */
object VectorMath {
    fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        require(a.size == b.size) { "Vector dimensions must match: ${a.size} vs ${b.size}" }
        if (a.isEmpty()) return 0f

        var dot = 0.0
        var normA = 0.0
        var normB = 0.0
        for (i in a.indices) {
            dot += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        val denominator = kotlin.math.sqrt(normA) * kotlin.math.sqrt(normB)
        return if (denominator == 0.0) 0f else (dot / denominator).toFloat()
    }

    fun l2Normalize(vector: FloatArray): FloatArray {
        var sumSquares = 0.0
        for (value in vector) {
            sumSquares += value * value
        }
        val norm = kotlin.math.sqrt(sumSquares)
        if (norm == 0.0) return vector.copyOf()
        return FloatArray(vector.size) { index -> (vector[index] / norm).toFloat() }
    }

    fun meanPool(tokenEmbeddings: Array<FloatArray>): FloatArray {
        if (tokenEmbeddings.isEmpty()) return floatArrayOf()
        val dimension = tokenEmbeddings[0].size
        val pooled = FloatArray(dimension)
        for (embedding in tokenEmbeddings) {
            require(embedding.size == dimension) { "All token embeddings must share dimension $dimension" }
            for (i in embedding.indices) {
                pooled[i] += embedding[i]
            }
        }
        val count = tokenEmbeddings.size.toFloat()
        for (i in pooled.indices) {
            pooled[i] /= count
        }
        return l2Normalize(pooled)
    }
}
