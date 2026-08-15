package com.nova.runtime.storage.vector

import java.util.UUID

/** DPS §7 — supported vector search modes (implementation deferred). */
enum class VectorSearchType {
    KNN,
    HYBRID,
    METADATA_FILTER,
}

/** DPS §7 — vector search request contract. */
data class VectorSearchRequest(
    val queryVector: FloatArray,
    val k: Int,
    val searchType: VectorSearchType = VectorSearchType.KNN,
    val metadataFilter: Map<String, String> = emptyMap(),
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is VectorSearchRequest) return false
        return queryVector.contentEquals(other.queryVector) &&
            k == other.k &&
            searchType == other.searchType &&
            metadataFilter == other.metadataFilter
    }

    override fun hashCode(): Int {
        var result = queryVector.contentHashCode()
        result = 31 * result + k
        result = 31 * result + searchType.hashCode()
        result = 31 * result + metadataFilter.hashCode()
        return result
    }
}

/** DPS §7 — vector search result entry. */
data class VectorSearchResult(
    val embeddingId: UUID,
    val objectId: UUID,
    val objectType: String,
    val score: Float,
    val metadata: Map<String, String> = emptyMap(),
)

/** DPS §4.3 — semantic similarity index (vectors stored externally from SQLite). */
interface VectorIndex {
    suspend fun insert(
        embeddingId: UUID,
        vector: FloatArray,
        metadata: Map<String, String> = emptyMap(),
    )

    suspend fun search(request: VectorSearchRequest): List<VectorSearchResult>

    suspend fun delete(embeddingId: UUID)

    suspend fun deleteByObjectId(objectId: UUID)
}

/** Placeholder implementation until HNSW index is integrated. */
class NoOpVectorIndex : VectorIndex {
    override suspend fun insert(
        embeddingId: UUID,
        vector: FloatArray,
        metadata: Map<String, String>,
    ) = Unit

    override suspend fun search(request: VectorSearchRequest): List<VectorSearchResult> = emptyList()

    override suspend fun delete(embeddingId: UUID) = Unit

    override suspend fun deleteByObjectId(objectId: UUID) = Unit
}
