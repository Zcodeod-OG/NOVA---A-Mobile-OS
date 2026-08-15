package com.nova.runtime.ai.native.indexing

import com.nova.runtime.ai.model.EmbeddingGenerator
import com.nova.runtime.ai.model.EmbeddingResult
import com.nova.runtime.ai.model.HashEmbeddingGenerator
import com.nova.runtime.ai.model.HashImageEmbeddingGenerator
import com.nova.runtime.ai.model.ImageEmbeddingGenerator
import com.nova.runtime.ai.model.OcrEngine
import com.nova.runtime.ai.model.OcrResult
import com.nova.runtime.ai.native.storage.CosineVectorIndex
import com.nova.runtime.storage.repository.EmbeddingRepository
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EmbeddingIndexerTest {
    private val embeddingGenerator: EmbeddingGenerator = HashEmbeddingGenerator(dimension = 32)
    private val imageEmbeddingGenerator: ImageEmbeddingGenerator = HashImageEmbeddingGenerator(dimension = 32)
    private val vectorIndex = CosineVectorIndex()
    private val embeddingRepository = FakeEmbeddingRepository()

    @Test
    fun indexText_persistsVectorBlob() = runTest {
        val indexer = createIndexer(NoOcrEngine())
        val objectId = UUID.randomUUID()

        val embeddingId = indexer.indexText(objectId, "document", "invoice total due")

        assertNotNull(embeddingId)
        val stored = embeddingRepository.lastInserted
        assertNotNull(stored?.vectorBlob)
        assertEquals(1, vectorIndex.size())
    }

    @Test
    fun indexPhoto_withoutOcr_stillIndexesImageEmbedding() =
        runTest {
            val indexer = createIndexer(NoOcrEngine())
            val objectId = UUID.randomUUID()
            val visualConcept = "golden retriever puppy"

            val result = indexer.indexPhoto(objectId, visualConcept.toByteArray())

            assertNull(result.ocrText)
            assertNotNull(result.imageEmbeddingId)
            assertNotNull(result.embeddingId)
            assertEquals(1, vectorIndex.size())
        }

    @Test
    fun indexPhoto_withOcr_indexesDualEmbeddings() =
        runTest {
            val indexer = createIndexer(FixedOcrEngine("invoice total due"))
            val objectId = UUID.randomUUID()

            val result = indexer.indexPhoto(objectId, "ignored-image-bytes".toByteArray())

            assertEquals("invoice total due", result.ocrText)
            assertNotNull(result.ocrEmbeddingId)
            assertNotNull(result.imageEmbeddingId)
            assertTrue(vectorIndex.size() >= 2)
        }

    private fun createIndexer(ocrEngine: OcrEngine): EmbeddingIndexer =
        EmbeddingIndexer(
            embeddingGenerator = embeddingGenerator,
            imageEmbeddingGenerator = imageEmbeddingGenerator,
            embeddingRepository = embeddingRepository,
            vectorIndex = vectorIndex,
            ocrEngine = ocrEngine,
        )

    private class NoOcrEngine : OcrEngine {
        override suspend fun recognize(imageBytes: ByteArray): OcrResult =
            OcrResult.Failure("no text detected")
    }

    private class FixedOcrEngine(
        private val text: String,
    ) : OcrEngine {
        override suspend fun recognize(imageBytes: ByteArray): OcrResult = OcrResult.Success(text)
    }

    private class FakeEmbeddingRepository : EmbeddingRepository {
        var lastInserted: com.nova.runtime.storage.entities.EmbeddingEntity? = null

        override suspend fun insert(embedding: com.nova.runtime.storage.entities.EmbeddingEntity) {
            lastInserted = embedding
        }
        override suspend fun update(embedding: com.nova.runtime.storage.entities.EmbeddingEntity) = Unit
        override suspend fun delete(embeddingId: UUID) = Unit
        override suspend fun getById(embeddingId: UUID) = null
        override fun observeById(embeddingId: UUID): Flow<com.nova.runtime.storage.entities.EmbeddingEntity?> = emptyFlow()
        override suspend fun listWithPersistedVectors(): List<com.nova.runtime.storage.entities.EmbeddingEntity> =
            listOfNotNull(lastInserted).filter { it.vectorBlob != null }
    }
}
