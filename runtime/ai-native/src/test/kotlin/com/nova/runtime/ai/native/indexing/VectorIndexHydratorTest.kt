package com.nova.runtime.ai.native.indexing

import com.nova.runtime.ai.native.storage.CosineVectorIndex
import com.nova.runtime.storage.entities.EmbeddingEntity
import com.nova.runtime.storage.repository.EmbeddingRepository
import com.nova.runtime.storage.vector.VectorBlobCodec
import com.nova.runtime.utils.logging.NoOpRuntimeLogger
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class VectorIndexHydratorTest {
    @Test
    fun hydrateFromDatabase_loadsPersistedVectors() = runTest {
        val objectId = UUID.randomUUID()
        val embeddingId = UUID.randomUUID()
        val vector = floatArrayOf(0.2f, 0.4f, 0.6f)
        val repository = FakeEmbeddingRepository(
            listOf(
                EmbeddingEntity(
                    embeddingId = embeddingId,
                    objectType = "document",
                    objectId = objectId,
                    modelVersion = "test-v1",
                    dimension = vector.size,
                    createdAt = 1L,
                    vectorBlob = VectorBlobCodec.encode(vector),
                    embeddingKind = EmbeddingMetadata.KIND_SUMMARY,
                ),
            ),
        )
        val vectorIndex = CosineVectorIndex()

        VectorIndexHydrator(
            embeddingRepository = repository,
            vectorIndex = vectorIndex,
            logger = NoOpRuntimeLogger(),
        ).hydrateFromDatabase()

        assertEquals(1, vectorIndex.size())
        val hits = vectorIndex.search(
            com.nova.runtime.storage.vector.VectorSearchRequest(
                queryVector = vector,
                k = 1,
                metadataFilter = mapOf(
                    EmbeddingMetadata.OBJECT_TYPE to "document",
                    EmbeddingMetadata.EMBEDDING_KIND to EmbeddingMetadata.KIND_SUMMARY,
                ),
            ),
        )
        assertEquals(1, hits.size)
        assertEquals(objectId, hits.first().objectId)
    }

    private class FakeEmbeddingRepository(
        private val persisted: List<EmbeddingEntity>,
    ) : EmbeddingRepository {
        override suspend fun insert(embedding: EmbeddingEntity) = Unit
        override suspend fun update(embedding: EmbeddingEntity) = Unit
        override suspend fun delete(embeddingId: UUID) = Unit
        override suspend fun getById(embeddingId: UUID): EmbeddingEntity? = null
        override fun observeById(embeddingId: UUID): Flow<EmbeddingEntity?> = emptyFlow()
        override suspend fun listWithPersistedVectors(): List<EmbeddingEntity> = persisted
    }
}
