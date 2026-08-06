package com.nova.runtime.ai.native.storage

import com.nova.runtime.ai.embedding.InMemoryVectorIndex
import com.nova.runtime.ai.embedding.VectorSearchQuery
import com.nova.runtime.storage.vector.VectorIndex
import com.nova.runtime.storage.vector.VectorSearchRequest
import com.nova.runtime.storage.vector.VectorSearchResult
import java.util.UUID

/** Bridges DPS [VectorIndex] contract to [InMemoryVectorIndex] cosine search. */
class CosineVectorIndex(
    private val delegate: InMemoryVectorIndex = InMemoryVectorIndex(),
) : VectorIndex {
    override suspend fun insert(
        embeddingId: UUID,
        vector: FloatArray,
        metadata: Map<String, String>,
    ) {
        delegate.insert(embeddingId, vector, metadata)
    }

    override suspend fun search(request: VectorSearchRequest): List<VectorSearchResult> =
        delegate.search(
            VectorSearchQuery(
                queryVector = request.queryVector,
                k = request.k,
                metadataFilter = request.metadataFilter,
            ),
        ).map { hit ->
            VectorSearchResult(
                embeddingId = hit.embeddingId,
                objectId = UUID.fromString(
                    hit.metadata["objectId"]
                        ?: "00000000-0000-0000-0000-000000000000",
                ),
                objectType = hit.metadata["objectType"] ?: "unknown",
                score = hit.score,
                metadata = hit.metadata,
            )
        }

    override suspend fun delete(embeddingId: UUID) {
        delegate.delete(embeddingId)
    }

    override suspend fun deleteByObjectId(objectId: UUID) {
        delegate.deleteByObjectId(objectId)
    }

    fun size(): Int = delegate.size()
}
