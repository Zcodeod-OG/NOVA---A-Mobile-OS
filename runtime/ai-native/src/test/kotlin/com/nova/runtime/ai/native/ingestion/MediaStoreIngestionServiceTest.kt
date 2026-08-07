package com.nova.runtime.ai.native.ingestion

import com.nova.runtime.ai.model.EmbeddingGenerator
import com.nova.runtime.ai.model.EmbeddingResult
import com.nova.runtime.ai.model.HashEmbeddingGenerator
import com.nova.runtime.ai.model.HashImageEmbeddingGenerator
import com.nova.runtime.ai.model.ImageEmbeddingGenerator
import com.nova.runtime.ai.model.OcrEngine
import com.nova.runtime.ai.model.OcrResult
import com.nova.runtime.ai.native.indexing.EmbeddingIndexer
import com.nova.runtime.ai.native.search.SearchIndexPipeline
import com.nova.runtime.ai.native.storage.CosineVectorIndex
import com.nova.runtime.storage.dao.DocumentDao
import com.nova.runtime.storage.dao.PhotoDao
import com.nova.runtime.storage.entities.DocumentEntity
import com.nova.runtime.storage.entities.PhotoEntity
import com.nova.runtime.storage.repository.DocumentRepository
import com.nova.runtime.storage.repository.EmbeddingRepository
import com.nova.runtime.storage.repository.PhotoRepository
import com.nova.runtime.storage.search.DownloadItem
import com.nova.runtime.storage.search.DownloadQueryResult
import com.nova.runtime.storage.search.DownloadsQueryPort
import com.nova.runtime.storage.search.MediaImageItem
import com.nova.runtime.storage.search.MediaImageQueryResult
import com.nova.runtime.storage.search.MediaStoreQueryPort
import com.nova.runtime.storage.search.NoOpDocumentsQueryPort
import com.nova.runtime.utils.logging.NoOpRuntimeLogger
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import android.content.Context
import androidx.test.core.app.ApplicationProvider

import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class MediaStoreIngestionServiceTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }
    private val photoRepository = RecordingPhotoRepository()
    private val documentRepository = RecordingDocumentRepository()
    private val vectorIndex = CosineVectorIndex()
    private val embeddingGenerator: EmbeddingGenerator = HashEmbeddingGenerator(dimension = 32)
    private val imageEmbeddingGenerator: ImageEmbeddingGenerator = HashImageEmbeddingGenerator(dimension = 32)
    private val indexer =
        EmbeddingIndexer(
            embeddingGenerator = embeddingGenerator,
            imageEmbeddingGenerator = imageEmbeddingGenerator,
            embeddingRepository = FakeEmbeddingRepository(),
            vectorIndex = vectorIndex,
            ocrEngine = StubOcrEngine(),
        )
    private val pipeline =
        SearchIndexPipeline(
            embeddingIndexer = indexer,
            photoDao = FakePhotoDao(photoRepository),
            documentDao = FakeDocumentDao(documentRepository),
            photoRepository = photoRepository,
            documentRepository = documentRepository,
            photoImageLoader = PhotoImageLoader { "beach vacation text".toByteArray() },
        )

    @Test
    fun ingestPhotos_skipsExistingUris() =
        runTest {
            val existingUri = "content://media/external/images/media/1"
            photoRepository.records[UUID.randomUUID()] =
                PhotoEntity(
                    id = UUID.randomUUID(),
                    uri = existingUri,
                    takenAt = 1L,
                    width = null,
                    height = null,
                    latitude = null,
                    longitude = null,
                    ocrText = "existing",
                    embeddingId = UUID.randomUUID(),
                    favorite = false,
                )

            val service =
                createService(
                    mediaItems =
                        listOf(
                            MediaImageItem(1, existingUri, "existing.jpg", "image/jpeg", 1L, 100L),
                        ),
                )

            val result = service.ingestPhotos(limit = 10)

            assertEquals(0, result.ingested)
            assertEquals(1, result.skipped)
        }

    @Test
    fun ingestDocuments_insertsMetadataAndEmbedding() =
        runTest {
            val service =
                createService(
                    downloads =
                        listOf(
                            DownloadItem(
                                downloadId = 10L,
                                uri = "content://media/external/downloads/10",
                                displayName = "Quarterly Invoice.pdf",
                                mimeType = "application/pdf",
                                dateAdded = 100L,
                                size = 4096L,
                            ),
                        ),
                )

            val result = service.ingestDocuments(limit = 10)

            assertEquals(1, result.ingested)
            assertEquals(0, result.skipped)
            val document = documentRepository.records.values.single()
            assertEquals("Quarterly Invoice.pdf", document.name)
            assertEquals("pdf", document.extension)
            assertNotNull(document.embeddingId)
        }

    private fun createService(
        mediaItems: List<MediaImageItem> = emptyList(),
        downloads: List<DownloadItem> = emptyList(),
    ): MediaStoreIngestionService =
        MediaStoreIngestionService(
            mediaStoreQuery = FakeMediaStoreQueryPort(mediaItems),
            downloadsQuery = FakeDownloadsQueryPort(downloads),
            documentsQuery = NoOpDocumentsQueryPort(),
            photoDao = FakePhotoDao(photoRepository),
            documentDao = FakeDocumentDao(documentRepository),
            photoRepository = photoRepository,
            documentRepository = documentRepository,
            embeddingIndexer = indexer,
            searchIndexPipeline = pipeline,
            photoImageLoader = PhotoImageLoader { "beach vacation text".toByteArray() },
            logger = NoOpRuntimeLogger(),
        )

    private class StubOcrEngine : OcrEngine {
        override suspend fun recognize(imageBytes: ByteArray): OcrResult =
            OcrResult.Success("beach vacation text")
    }

    private class FakeMediaStoreQueryPort(
        private val items: List<MediaImageItem>,
    ) : MediaStoreQueryPort {
        override suspend fun queryImages(limit: Int, offset: Int): MediaImageQueryResult =
            MediaImageQueryResult(items.drop(offset).take(limit))

        override suspend fun queryVideos(limit: Int, offset: Int): MediaImageQueryResult =
            MediaImageQueryResult(emptyList())

        override suspend fun queryAudio(limit: Int, offset: Int): MediaImageQueryResult =
            MediaImageQueryResult(emptyList())
    }

    private class FakeDownloadsQueryPort(
        private val items: List<DownloadItem>,
    ) : DownloadsQueryPort {
        override suspend fun queryDownloads(limit: Int, offset: Int): DownloadQueryResult =
            DownloadQueryResult(items.drop(offset).take(limit))
    }

    private class RecordingPhotoRepository : PhotoRepository {
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

    private class RecordingDocumentRepository : DocumentRepository {
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
        override suspend fun insert(embedding: com.nova.runtime.storage.entities.EmbeddingEntity) = Unit
        override suspend fun update(embedding: com.nova.runtime.storage.entities.EmbeddingEntity) = Unit
        override suspend fun delete(embeddingId: UUID) = Unit
        override suspend fun getById(embeddingId: UUID) = null
        override fun observeById(embeddingId: UUID): Flow<com.nova.runtime.storage.entities.EmbeddingEntity?> = emptyFlow()
    }

    private class FakePhotoDao(
        private val repository: RecordingPhotoRepository,
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
        private val repository: RecordingDocumentRepository,
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
    }
}
