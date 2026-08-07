package com.nova.runtime.ai.native.search.provider

import com.nova.runtime.ai.model.EmbeddingGenerator
import com.nova.runtime.ai.model.HashEmbeddingGenerator
import com.nova.runtime.ai.native.indexing.EmbeddingIndexer
import com.nova.runtime.ai.native.search.SearchIndexPipeline
import com.nova.runtime.ai.native.search.SemanticSearchService
import com.nova.runtime.ai.native.storage.CosineVectorIndex
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.nova.runtime.capability.model.CapabilityExecutionRequest
import com.nova.runtime.storage.entities.DocumentEntity
import com.nova.runtime.storage.search.DocumentSearchService
import com.nova.runtime.utils.logging.NoOpRuntimeLogger
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SearchCapabilityProviderTest {
    private val traceId = UUID.randomUUID()
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun documentProvider_rejectsMissingQuery() =
        runTest {
            val provider = createDocumentProvider()

            val validation = provider.validate(
                CapabilityExecutionRequest(
                    operation = "search",
                    parameters = emptyMap(),
                    traceId = traceId,
                ),
            )

            assert(validation is com.nova.runtime.capability.model.CapabilityValidationResult.Invalid)
        }

    @Test
    fun documentProvider_executesSemanticSearchEvenWhenEmbeddingModelNotPreloaded() =
        runTest {
            val provider = createDocumentProvider(embeddingLoaded = false)

            val response = provider.execute(
                CapabilityExecutionRequest(
                    operation = "search",
                    parameters = mapOf("query" to "budget"),
                    traceId = traceId,
                ),
            )

            assert(response is com.nova.runtime.capability.model.CapabilityExecutionResponse.Success)
            val output = (response as com.nova.runtime.capability.model.CapabilityExecutionResponse.Success).output
            assertEquals("search.documents", output["capabilityType"])
            assertEquals("search", output["operation"])
            assertEquals("semantic", output["searchMode"])
        }

    @Test
    fun documentProvider_executesSemanticSearchWhenEmbeddingModelAvailable() =
        runTest {
            val provider = createDocumentProvider(embeddingLoaded = true)

            val response = provider.execute(
                CapabilityExecutionRequest(
                    operation = "search",
                    parameters = mapOf("query" to "budget"),
                    traceId = traceId,
                ),
            )

            assert(response is com.nova.runtime.capability.model.CapabilityExecutionResponse.Success)
            val output = (response as com.nova.runtime.capability.model.CapabilityExecutionResponse.Success).output
            assertEquals("semantic", output["searchMode"])
        }

    private suspend fun createDocumentProvider(embeddingLoaded: Boolean = false): DocumentSearchCapabilityProvider {
        val embeddingGenerator = object : EmbeddingGenerator by HashEmbeddingGenerator() {
            override val isLoaded: Boolean = embeddingLoaded
        }
        val vectorIndex = CosineVectorIndex()
        val documentRepository = FakeDocumentRepository()
        val documentId = UUID.randomUUID()
        documentRepository.records[documentId] =
            DocumentEntity(
                id = documentId,
                path = "/docs/budget.pdf",
                name = "Budget Report",
                extension = "pdf",
                mimeType = "application/pdf",
                size = 100L,
                checksum = "abc",
                createdAt = 1L,
                modifiedAt = 2L,
                indexedAt = null,
                projectId = null,
                embeddingId = null,
                importance = 0,
            )
        val indexer = EmbeddingIndexer(
            embeddingGenerator = embeddingGenerator,
            embeddingRepository = FakeEmbeddingRepository(),
            vectorIndex = vectorIndex,
            ocrEngine = FakeOcrEngine(),
        )
        val pipeline = SearchIndexPipeline(
            embeddingIndexer = indexer,
            photoDao = FakePhotoDao(),
            documentDao = FakeDocumentDao(documentRepository),
            photoRepository = FakePhotoRepository(),
            documentRepository = documentRepository,
        )
        pipeline.indexDocument(documentRepository.records.getValue(documentId))
        val semanticSearchService = SemanticSearchService(
            embeddingGenerator = embeddingGenerator,
            vectorIndex = vectorIndex,
            photoRepository = FakePhotoRepository(),
            documentRepository = documentRepository,
            searchIndexPipeline = pipeline,
            mediaStoreIngestionService = createNoOpIngestionService(pipeline, embeddingGenerator, vectorIndex),
            logger = NoOpRuntimeLogger(),
        )
        return DocumentSearchCapabilityProvider(
            documentSearchService = DocumentSearchService(FakeDocumentDao(), NoOpRuntimeLogger()),
            semanticSearchService = semanticSearchService,
        )
    }

    private fun createNoOpIngestionService(
        pipeline: SearchIndexPipeline,
        embeddingGenerator: EmbeddingGenerator,
        vectorIndex: CosineVectorIndex,
    ): com.nova.runtime.ai.native.ingestion.MediaStoreIngestionService =
        com.nova.runtime.ai.native.ingestion.MediaStoreIngestionService(
            mediaStoreQuery = com.nova.runtime.storage.search.NoOpMediaStoreQueryPort(),
            downloadsQuery = com.nova.runtime.storage.search.NoOpDownloadsQueryPort(),
            photoDao = FakePhotoDao(),
            documentDao = FakeDocumentDao(),
            photoRepository = FakePhotoRepository(),
            documentRepository = FakeDocumentRepository(),
            embeddingIndexer = EmbeddingIndexer(
                embeddingGenerator = embeddingGenerator,
                embeddingRepository = FakeEmbeddingRepository(),
                vectorIndex = vectorIndex,
                ocrEngine = FakeOcrEngine(),
            ),
            searchIndexPipeline = pipeline,
            context = context,
            logger = NoOpRuntimeLogger(),
        )

    private class FakeOcrEngine : com.nova.runtime.ai.model.OcrEngine {
        override suspend fun recognize(imageBytes: ByteArray) =
            com.nova.runtime.ai.model.OcrResult.Failure("unused")
    }

    private class FakeEmbeddingRepository : com.nova.runtime.storage.repository.EmbeddingRepository {
        override suspend fun insert(embedding: com.nova.runtime.storage.entities.EmbeddingEntity) = Unit
        override suspend fun update(embedding: com.nova.runtime.storage.entities.EmbeddingEntity) = Unit
        override suspend fun delete(embeddingId: UUID) = Unit
        override suspend fun getById(embeddingId: UUID) = null
        override fun observeById(embeddingId: UUID) =
            kotlinx.coroutines.flow.emptyFlow<com.nova.runtime.storage.entities.EmbeddingEntity?>()
    }

    private class FakePhotoRepository : com.nova.runtime.storage.repository.PhotoRepository {
        override suspend fun insert(photo: com.nova.runtime.storage.entities.PhotoEntity) = Unit
        override suspend fun update(photo: com.nova.runtime.storage.entities.PhotoEntity) = Unit
        override suspend fun delete(id: UUID) = Unit
        override suspend fun getById(id: UUID) = null
        override fun observeById(id: UUID) =
            kotlinx.coroutines.flow.emptyFlow<com.nova.runtime.storage.entities.PhotoEntity?>()
        override suspend fun search(query: String) = emptyList<com.nova.runtime.storage.entities.PhotoEntity>()
    }

    private class FakeDocumentRepository : com.nova.runtime.storage.repository.DocumentRepository {
        val records = mutableMapOf<UUID, DocumentEntity>()

        override suspend fun insert(document: DocumentEntity) {
            records[document.id] = document
        }

        override suspend fun update(document: DocumentEntity) {
            records[document.id] = document
        }

        override suspend fun delete(id: UUID) = Unit
        override suspend fun getById(id: UUID): DocumentEntity? = records[id]
        override fun observeById(id: UUID) =
            kotlinx.coroutines.flow.emptyFlow<DocumentEntity?>()
        override suspend fun search(query: String) = emptyList<DocumentEntity>()
    }

    private class FakeDocumentDao(
        private val repository: FakeDocumentRepository = FakeDocumentRepository(),
    ) : com.nova.runtime.storage.dao.DocumentDao {
        override suspend fun insert(document: DocumentEntity) = repository.insert(document)
        override suspend fun update(document: DocumentEntity) = repository.update(document)
        override suspend fun delete(document: DocumentEntity) = Unit
        override suspend fun getById(id: UUID) = repository.getById(id)
        override fun observeById(id: UUID) = repository.observeById(id)
        override suspend fun getByPath(path: String) = repository.records.values.firstOrNull { it.path == path }
        override suspend fun searchByName(query: String) = emptyList<DocumentEntity>()
        override suspend fun searchFullText(query: String, limit: Int, offset: Int) =
            emptyList<DocumentEntity>()
        override suspend fun countFullText(query: String) = 0
        override suspend fun getByProjectId(projectId: UUID) = emptyList<DocumentEntity>()
        override suspend fun listUnindexed(limit: Int) =
            repository.records.values.filter { it.embeddingId == null }.take(limit)
    }

    private class FakePhotoDao : com.nova.runtime.storage.dao.PhotoDao {
        override suspend fun insert(photo: com.nova.runtime.storage.entities.PhotoEntity) = Unit
        override suspend fun update(photo: com.nova.runtime.storage.entities.PhotoEntity) = Unit
        override suspend fun delete(photo: com.nova.runtime.storage.entities.PhotoEntity) = Unit
        override suspend fun getById(id: UUID) = null
        override fun observeById(id: UUID) =
            kotlinx.coroutines.flow.emptyFlow<com.nova.runtime.storage.entities.PhotoEntity?>()
        override suspend fun getByUri(uri: String) = null
        override suspend fun searchByOcr(query: String) = emptyList<com.nova.runtime.storage.entities.PhotoEntity>()
        override suspend fun searchByOcrPaged(query: String, limit: Int, offset: Int) =
            emptyList<com.nova.runtime.storage.entities.PhotoEntity>()
        override suspend fun countByOcr(query: String) = 0
        override suspend fun listRecent(limit: Int) = emptyList<com.nova.runtime.storage.entities.PhotoEntity>()
        override suspend fun listUnindexedWithOcr(limit: Int) = emptyList<com.nova.runtime.storage.entities.PhotoEntity>()
    }
}
