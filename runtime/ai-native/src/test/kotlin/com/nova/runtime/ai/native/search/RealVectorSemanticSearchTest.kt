package com.nova.runtime.ai.native.search

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.nova.runtime.ai.embedding.VectorMath
import com.nova.runtime.ai.model.EmbeddingResult
import com.nova.runtime.ai.model.FileSystemModelLoader
import com.nova.runtime.ai.model.HashImageEmbeddingGenerator
import com.nova.runtime.ai.model.ModelAssetPaths
import com.nova.runtime.ai.model.ModelLoadConfig
import com.nova.runtime.ai.model.OcrEngine
import com.nova.runtime.ai.model.OcrResult
import com.nova.runtime.ai.native.indexing.EmbeddingIndexer
import com.nova.runtime.ai.native.indexing.EmbeddingMetadata
import com.nova.runtime.ai.native.ingestion.PhotoImageLoader
import com.nova.runtime.ai.native.ingestion.MediaStoreIngestionService
import com.nova.runtime.ai.native.onnx.OnnxEmbeddingGenerator
import com.nova.runtime.ai.native.storage.CosineVectorIndex
import com.nova.runtime.ai.native.tokenizer.MiniLmTokenizer
import com.nova.runtime.ai.tokenizer.MiniLmTokenizer as CoreMiniLmTokenizer
import com.nova.runtime.storage.dao.DocumentDao
import com.nova.runtime.storage.dao.PhotoDao
import com.nova.runtime.storage.entities.DocumentEntity
import com.nova.runtime.storage.entities.EmbeddingEntity
import com.nova.runtime.storage.entities.PhotoEntity
import com.nova.runtime.storage.repository.DocumentRepository
import com.nova.runtime.storage.repository.EmbeddingRepository
import com.nova.runtime.storage.repository.PhotoRepository
import com.nova.runtime.storage.search.SearchRequest
import com.nova.runtime.utils.logging.NoOpRuntimeLogger
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class RealVectorSemanticSearchTest {
    private lateinit var context: Context
    private lateinit var embeddingGenerator: OnnxEmbeddingGenerator
    private lateinit var vectorIndex: CosineVectorIndex
    private lateinit var photoRepository: FakePhotoRepository
    private lateinit var documentRepository: FakeDocumentRepository
    private lateinit var embeddingRepository: FakeEmbeddingRepository
    private lateinit var service: SemanticSearchService

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        embeddingGenerator = createEmbeddingGenerator()
        vectorIndex = CosineVectorIndex()
        photoRepository = FakePhotoRepository()
        documentRepository = FakeDocumentRepository()
        embeddingRepository = FakeEmbeddingRepository()
        val indexer = EmbeddingIndexer(
            embeddingGenerator = embeddingGenerator,
            imageEmbeddingGenerator = HashImageEmbeddingGenerator(dimension = ModelAssetPaths.DEFAULT_EMBEDDING_DIMENSION),
            embeddingRepository = embeddingRepository,
            vectorIndex = vectorIndex,
            ocrEngine = StubOcrEngine(),
        )
        val pipeline = SearchIndexPipeline(
            embeddingIndexer = indexer,
            photoDao = FakePhotoDao(photoRepository),
            documentDao = FakeDocumentDao(documentRepository),
            photoRepository = photoRepository,
            documentRepository = documentRepository,
            photoImageLoader = PhotoImageLoader { null },
        )
        val ingestionService = MediaStoreIngestionService(
            mediaStoreQuery = com.nova.runtime.storage.search.NoOpMediaStoreQueryPort(),
            downloadsQuery = com.nova.runtime.storage.search.NoOpDownloadsQueryPort(),
            documentsQuery = com.nova.runtime.storage.search.NoOpDocumentsQueryPort(),
            photoDao = FakePhotoDao(photoRepository),
            documentDao = FakeDocumentDao(documentRepository),
            photoRepository = photoRepository,
            documentRepository = documentRepository,
            embeddingIndexer = indexer,
            searchIndexPipeline = pipeline,
            photoImageLoader = PhotoImageLoader { null },
            logger = NoOpRuntimeLogger(),
        )
        service = SemanticSearchService(
            embeddingGenerator = embeddingGenerator,
            imageEmbeddingGenerator = HashImageEmbeddingGenerator(dimension = ModelAssetPaths.DEFAULT_EMBEDDING_DIMENSION),
            vectorIndex = vectorIndex,
            photoRepository = photoRepository,
            documentRepository = documentRepository,
            documentDao = FakeDocumentDao(documentRepository),
            searchIndexPipeline = pipeline,
            mediaStoreIngestionService = ingestionService,
            fullDeviceIndexer = null,
            logger = NoOpRuntimeLogger(),
        )
    }

    @Test
    fun search_withRealEmbeddings_ranksSemanticallySimilarDocumentFirst() = runTest {
        if (!embeddingGenerator.isLoaded) {
            println("Skipping real-vector semantic search: ONNX embedding model unavailable")
            return@runTest
        }

        val invoiceId = UUID.randomUUID()
        val beachId = UUID.randomUUID()
        documentRepository.records[invoiceId] = sampleDocument(invoiceId, "Quarterly Invoice", "/docs/invoice.pdf")
        photoRepository.records[beachId] = samplePhoto(beachId, "sunset beach photo", "content://photo/beach")

        indexText(invoiceId, "document", "Quarterly Invoice financial report")
        indexText(beachId, "photo", "sunset beach vacation", EmbeddingMetadata.KIND_OCR)

        val page = service.search(
            request = SearchRequest(query = "invoice financial", limit = 5, indexOnQuery = false),
            traceId = UUID.randomUUID(),
            objectTypes = setOf(SemanticSearchService.OBJECT_TYPE_DOCUMENT),
        )

        assertTrue(page.count >= 1)
        assertEquals("document", page.items.first().objectType)

        val invoiceVector = requireSuccess(embeddingGenerator.embed("Quarterly Invoice financial report")).vector
        val beachVector = requireSuccess(embeddingGenerator.embed("sunset beach vacation")).vector
        val queryVector = requireSuccess(embeddingGenerator.embed("invoice financial")).vector
        val invoiceScore = VectorMath.cosineSimilarity(queryVector, invoiceVector)
        val beachScore = VectorMath.cosineSimilarity(queryVector, beachVector)
        assertTrue(invoiceScore > beachScore)
    }

    private suspend fun indexText(
        objectId: UUID,
        objectType: String,
        text: String,
        embeddingKind: String? = null,
    ) {
        val result = requireSuccess(embeddingGenerator.embed(text))
        val embeddingId = UUID.randomUUID()
        embeddingRepository.insert(
            EmbeddingEntity(
                embeddingId = embeddingId,
                objectType = objectType,
                objectId = objectId,
                modelVersion = result.modelVersion,
                dimension = result.dimension,
                createdAt = System.currentTimeMillis(),
            ),
        )
        val metadata = buildMap {
            put(EmbeddingMetadata.OBJECT_ID, objectId.toString())
            put(EmbeddingMetadata.OBJECT_TYPE, objectType)
            if (embeddingKind != null) {
                put(EmbeddingMetadata.EMBEDDING_KIND, embeddingKind)
            }
        }
        vectorIndex.insert(
            embeddingId = embeddingId,
            vector = result.vector,
            metadata = metadata,
        )
    }

    private fun requireSuccess(result: EmbeddingResult): EmbeddingResult.Success {
        assertTrue(result is EmbeddingResult.Success)
        return result as EmbeddingResult.Success
    }

    private fun createEmbeddingGenerator(): OnnxEmbeddingGenerator {
        val tokenizer = MiniLmTokenizer.fromTokenizer(CoreMiniLmTokenizer.fromClasspath())
        val modelFile = locateEmbeddingModel()
        val modelsDir = createTempDir(prefix = "nova-real-search")
        if (modelFile != null) {
            modelFile.copyTo(File(modelsDir, ModelAssetPaths.EMBEDDING_MODEL), overwrite = true)
        }
        return OnnxEmbeddingGenerator(
            modelLoader = FileSystemModelLoader(ModelLoadConfig(modelsDirectory = modelsDir.absolutePath)),
            tokenizer = tokenizer,
            logger = NoOpRuntimeLogger(),
        )
    }

    private fun locateEmbeddingModel(): File? {
        val candidates = listOf(
            File("../../app/src/main/assets/models/${ModelAssetPaths.EMBEDDING_MODEL}"),
            File("../../../app/src/main/assets/models/${ModelAssetPaths.EMBEDDING_MODEL}"),
        )
        return candidates.firstOrNull { it.isFile }
    }

    private fun sampleDocument(id: UUID, name: String, path: String): DocumentEntity =
        DocumentEntity(
            id = id,
            path = path,
            name = name,
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

    private fun samplePhoto(id: UUID, ocrText: String, uri: String): PhotoEntity =
        PhotoEntity(
            id = id,
            uri = uri,
            takenAt = 10L,
            width = 100,
            height = 100,
            latitude = null,
            longitude = null,
            ocrText = ocrText,
            embeddingId = null,
            favorite = false,
        )

    private class StubOcrEngine : OcrEngine {
        override suspend fun recognize(imageBytes: ByteArray): OcrResult =
            OcrResult.Failure("unused in semantic search test")
    }

    private class FakePhotoRepository : PhotoRepository {
        val records = mutableMapOf<UUID, PhotoEntity>()

        override suspend fun insert(photo: PhotoEntity) {
            records[photo.id] = photo
        }

        override suspend fun update(photo: PhotoEntity) {
            records[photo.id] = photo
        }

        override suspend fun delete(id: UUID) {
            records.remove(id)
        }

        override suspend fun getById(id: UUID): PhotoEntity? = records[id]

        override fun observeById(id: UUID): Flow<PhotoEntity?> = emptyFlow()

        override suspend fun search(query: String): List<PhotoEntity> = emptyList()
    }

    private class FakeDocumentRepository : DocumentRepository {
        val records = mutableMapOf<UUID, DocumentEntity>()

        override suspend fun insert(document: DocumentEntity) {
            records[document.id] = document
        }

        override suspend fun update(document: DocumentEntity) {
            records[document.id] = document
        }

        override suspend fun delete(id: UUID) {
            records.remove(id)
        }

        override suspend fun getById(id: UUID): DocumentEntity? = records[id]

        override fun observeById(id: UUID): Flow<DocumentEntity?> = emptyFlow()

        override suspend fun search(query: String): List<DocumentEntity> = emptyList()
    }

    private class FakeEmbeddingRepository : EmbeddingRepository {
        override suspend fun insert(embedding: EmbeddingEntity) = Unit
        override suspend fun update(embedding: EmbeddingEntity) = Unit
        override suspend fun delete(embeddingId: UUID) = Unit
        override suspend fun getById(embeddingId: UUID) = null
        override fun observeById(embeddingId: UUID): Flow<EmbeddingEntity?> = emptyFlow()
    }

    private class FakePhotoDao(
        private val repository: FakePhotoRepository,
    ) : PhotoDao {
        override suspend fun insert(photo: PhotoEntity) = repository.insert(photo)
        override suspend fun update(photo: PhotoEntity) = repository.update(photo)
        override suspend fun delete(photo: PhotoEntity) = repository.delete(photo.id)
        override suspend fun getById(id: UUID) = repository.getById(id)
        override fun observeById(id: UUID) = repository.observeById(id)
        override suspend fun getByUri(uri: String) = repository.records.values.firstOrNull { it.uri == uri }
        override suspend fun searchByOcr(query: String) = emptyList<PhotoEntity>()
        override suspend fun searchByOcrPaged(query: String, limit: Int, offset: Int) = emptyList<PhotoEntity>()
        override suspend fun countByOcr(query: String) = 0
        override suspend fun listRecent(limit: Int) = repository.records.values.take(limit)
        override suspend fun listUnindexedWithOcr(limit: Int) =
            repository.records.values.filter { it.embeddingId == null && !it.ocrText.isNullOrBlank() }.take(limit)
        override suspend fun listUnindexed(limit: Int) =
            repository.records.values.filter { it.embeddingId == null }.take(limit)
    }

    private class FakeDocumentDao(
        private val repository: FakeDocumentRepository,
    ) : DocumentDao {
        override suspend fun insert(document: DocumentEntity) = repository.insert(document)
        override suspend fun update(document: DocumentEntity) = repository.update(document)
        override suspend fun delete(document: DocumentEntity) = repository.delete(document.id)
        override suspend fun getById(id: UUID) = repository.getById(id)
        override fun observeById(id: UUID) = repository.observeById(id)
        override suspend fun getByPath(path: String) = repository.records.values.firstOrNull { it.path == path }
        override suspend fun searchByName(query: String) = emptyList<DocumentEntity>()
        override suspend fun searchFullText(query: String, limit: Int, offset: Int) = emptyList<DocumentEntity>()
        override suspend fun countFullText(query: String) = 0
        override suspend fun getByProjectId(projectId: UUID) = emptyList<DocumentEntity>()
        override suspend fun listUnindexed(limit: Int) =
            repository.records.values.filter { it.embeddingId == null }.take(limit)
        override suspend fun listMissingContentText(limit: Int) =
            repository.records.values.filter { it.contentText == null }.take(limit)
        override suspend fun listMissingSummary(limit: Int) =
            repository.records.values.filter { it.summary == null }.take(limit)
        override suspend fun countAll() = repository.records.size
        override suspend fun countWithSummary() =
            repository.records.values.count { !it.summary.isNullOrBlank() }
        override suspend fun countMissingSummary() =
            repository.records.values.count { it.summary.isNullOrBlank() }
    }
}
