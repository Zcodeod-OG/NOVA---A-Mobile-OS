package com.nova.runtime.ai.embedding

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.min

/** DPS §7 — brute-force cosine KNN over in-memory float vectors (MVP). */
data class VectorIndexEntry(
    val embeddingId: UUID,
    val vector: FloatArray,
    val metadata: Map<String, String>,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is VectorIndexEntry) return false
        return embeddingId == other.embeddingId &&
            vector.contentEquals(other.vector) &&
            metadata == other.metadata
    }

    override fun hashCode(): Int {
        var result = embeddingId.hashCode()
        result = 31 * result + vector.contentHashCode()
        result = 31 * result + metadata.hashCode()
        return result
    }
}

data class VectorSearchQuery(
    val queryVector: FloatArray,
    val k: Int,
    val metadataFilter: Map<String, String> = emptyMap(),
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is VectorSearchQuery) return false
        return queryVector.contentEquals(other.queryVector) &&
            k == other.k &&
            metadataFilter == other.metadataFilter
    }

    override fun hashCode(): Int {
        var result = queryVector.contentHashCode()
        result = 31 * result + k
        result = 31 * result + metadataFilter.hashCode()
        return result
    }
}

data class VectorSearchHit(
    val embeddingId: UUID,
    val score: Float,
    val metadata: Map<String, String>,
)

class InMemoryVectorIndex {
    private val entries = ConcurrentHashMap<UUID, VectorIndexEntry>()

    fun insert(embeddingId: UUID, vector: FloatArray, metadata: Map<String, String> = emptyMap()) {
        entries[embeddingId] = VectorIndexEntry(
            embeddingId = embeddingId,
            vector = VectorMath.l2Normalize(vector.copyOf()),
            metadata = metadata,
        )
    }

    fun search(request: VectorSearchQuery): List<VectorSearchHit> {
        val normalizedQuery = VectorMath.l2Normalize(request.queryVector.copyOf())
        val topK = request.k.coerceAtLeast(1)

        return entries.values
            .asSequence()
            .filter { entry -> request.metadataFilter.all { (key, value) -> entry.metadata[key] == value } }
            .map { entry ->
                VectorSearchHit(
                    embeddingId = entry.embeddingId,
                    score = VectorMath.cosineSimilarity(normalizedQuery, entry.vector),
                    metadata = entry.metadata,
                )
            }
            .sortedByDescending { it.score }
            .take(topK)
            .toList()
    }

    fun delete(embeddingId: UUID) {
        entries.remove(embeddingId)
    }

    fun deleteByObjectId(objectId: UUID) {
        val objectIdString = objectId.toString()
        entries.entries.removeIf { (_, entry) -> entry.metadata["objectId"] == objectIdString }
    }

    fun size(): Int = entries.size

    fun clear() = entries.clear()

    fun snapshot(limit: Int = Int.MAX_VALUE): List<VectorIndexEntry> =
        entries.values.take(min(limit, entries.size))
}
