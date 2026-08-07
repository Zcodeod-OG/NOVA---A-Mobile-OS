package com.nova.runtime.ai.native.ingestion

import com.nova.runtime.ai.native.indexing.EmbeddingIndexer
import com.nova.runtime.ai.native.search.SearchIndexPipeline
import com.nova.runtime.models.RuntimeModule
import com.nova.runtime.storage.dao.DocumentDao
import com.nova.runtime.storage.dao.PhotoDao
import com.nova.runtime.storage.entities.DocumentEntity
import com.nova.runtime.storage.entities.PhotoEntity
import com.nova.runtime.storage.repository.DocumentRepository
import com.nova.runtime.storage.repository.PhotoRepository
import com.nova.runtime.storage.search.DocumentsQueryPort
import com.nova.runtime.storage.search.DownloadItem
import com.nova.runtime.storage.search.DownloadsQueryPort
import com.nova.runtime.storage.search.MediaImageItem
import com.nova.runtime.storage.search.MediaStoreQueryPort
import com.nova.runtime.utils.logging.NovaLogger
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Syncs gallery photos, videos, audio, downloads, and documents from MediaStore into Room,
 * runs OCR/embedding for photos, then delegates vector indexing to [SearchIndexPipeline].
 */
class MediaStoreIngestionService(
    private val mediaStoreQuery: MediaStoreQueryPort,
    private val downloadsQuery: DownloadsQueryPort,
    private val documentsQuery: DocumentsQueryPort,
    private val photoDao: PhotoDao,
    private val documentDao: DocumentDao,
    private val photoRepository: PhotoRepository,
    private val documentRepository: DocumentRepository,
    private val embeddingIndexer: EmbeddingIndexer,
    private val searchIndexPipeline: SearchIndexPipeline,
    private val photoImageLoader: PhotoImageLoader,
    private val logger: NovaLogger,
) {
    private val syncMutex = Mutex()

    suspend fun ensureSynced(limit: Int = FullDeviceIndexer.PRIORITY_BATCH_SIZE) {
        syncMutex.withLock {
            ingestPhotos(limit, offset = 0)
            ingestDownloads(limit, offset = 0)
            indexPendingEmbeddings(limit)
        }
    }

    suspend fun syncAll(limit: Int = FullDeviceIndexer.DEFAULT_BATCH_SIZE): IngestionResult {
        val photoResult = ingestPhotos(limit, offset = 0)
        val videoResult = ingestVideos(limit, offset = 0)
        val audioResult = ingestAudio(limit, offset = 0)
        val downloadResult = ingestDownloads(limit, offset = 0)
        val fileResult = ingestFiles(limit, offset = 0)
        indexPendingEmbeddings(limit)
        val result =
            IngestionResult(
                photosIngested = photoResult.ingested,
                photosSkipped = photoResult.skipped,
                videosIngested = videoResult.ingested,
                videosSkipped = videoResult.skipped,
                audioIngested = audioResult.ingested,
                audioSkipped = audioResult.skipped,
                documentsIngested = downloadResult.ingested + fileResult.ingested,
                documentsSkipped = downloadResult.skipped + fileResult.skipped,
            )
        logger.info(
            module = RuntimeModule.STORAGE.name,
            message = "MediaStore ingestion completed",
            metadata =
                mapOf(
                    "photosIngested" to result.photosIngested.toString(),
                    "videosIngested" to result.videosIngested.toString(),
                    "audioIngested" to result.audioIngested.toString(),
                    "documentsIngested" to result.documentsIngested.toString(),
                ),
        )
        return result
    }

    suspend fun ingestPhotos(limit: Int, offset: Int = 0): ItemIngestionResult =
        withContext(Dispatchers.IO) {
            val items = mediaStoreQuery.queryImages(limit, offset).items
            ingestPhotoItems(items)
        }

    suspend fun ingestVideos(limit: Int, offset: Int = 0): ItemIngestionResult =
        withContext(Dispatchers.IO) {
            val items = mediaStoreQuery.queryVideos(limit, offset).items
            ingestMediaAsDocuments(items, defaultPrefix = "video")
        }

    suspend fun ingestAudio(limit: Int, offset: Int = 0): ItemIngestionResult =
        withContext(Dispatchers.IO) {
            val items = mediaStoreQuery.queryAudio(limit, offset).items
            ingestMediaAsDocuments(items, defaultPrefix = "audio")
        }

    suspend fun ingestDownloads(limit: Int, offset: Int = 0): ItemIngestionResult =
        withContext(Dispatchers.IO) {
            val items = downloadsQuery.queryDownloads(limit, offset).items
            ingestDownloadItems(items)
        }

    suspend fun ingestFiles(limit: Int, offset: Int = 0): ItemIngestionResult =
        withContext(Dispatchers.IO) {
            val items = documentsQuery.queryDocuments(limit, offset).items
            ingestDownloadItems(items)
        }

    /** @deprecated Use [ingestDownloads] — kept for test compatibility. */
    suspend fun ingestDocuments(limit: Int, offset: Int = 0): ItemIngestionResult =
        ingestDownloads(limit, offset)

    suspend fun indexPendingEmbeddings(limit: Int = SearchIndexPipeline.DEFAULT_BATCH_LIMIT) {
        searchIndexPipeline.ensureIndexed(
            SearchIndexPipeline.IndexRequest(
                indexPhotos = true,
                indexDocuments = true,
                photoLimit = limit,
                documentLimit = limit,
            ),
        )
    }

    private suspend fun ingestPhotoItems(items: List<MediaImageItem>): ItemIngestionResult {
        var ingested = 0
        var skipped = 0
        items.forEach { item ->
            if (photoDao.getByUri(item.uri) != null) {
                skipped++
                return@forEach
            }
            if (ingestPhotoItem(item)) {
                ingested++
            }
        }
        return ItemIngestionResult(ingested = ingested, skipped = skipped, queried = items.size)
    }

    private suspend fun ingestMediaAsDocuments(
        items: List<MediaImageItem>,
        defaultPrefix: String,
    ): ItemIngestionResult {
        var ingested = 0
        var skipped = 0
        items.forEach { item ->
            if (documentDao.getByPath(item.uri) != null) {
                skipped++
                return@forEach
            }
            if (ingestMediaDocumentItem(item, defaultPrefix)) {
                ingested++
            }
        }
        return ItemIngestionResult(ingested = ingested, skipped = skipped, queried = items.size)
    }

    private suspend fun ingestDownloadItems(items: List<DownloadItem>): ItemIngestionResult {
        var ingested = 0
        var skipped = 0
        items.forEach { item ->
            if (documentDao.getByPath(item.uri) != null) {
                skipped++
                return@forEach
            }
            if (ingestDocumentItem(item)) {
                ingested++
            }
        }
        return ItemIngestionResult(ingested = ingested, skipped = skipped, queried = items.size)
    }

    private suspend fun ingestPhotoItem(item: MediaImageItem): Boolean {
        val imageBytes = photoImageLoader.loadBytes(item.uri) ?: return false
        val photoId = stableId(item.uri)
        val indexResult = embeddingIndexer.indexPhoto(photoId, imageBytes)
        val photo =
            PhotoEntity(
                id = photoId,
                uri = item.uri,
                takenAt = item.dateAdded,
                width = null,
                height = null,
                latitude = null,
                longitude = null,
                ocrText = indexResult.ocrText,
                embeddingId = indexResult.embeddingId,
                favorite = false,
            )
        photoRepository.insert(photo)
        if (indexResult.embeddingId == null) {
            searchIndexPipeline.indexPhoto(photo)
        }
        return true
    }

    private suspend fun ingestMediaDocumentItem(
        item: MediaImageItem,
        defaultPrefix: String,
    ): Boolean {
        val name = item.displayName?.takeIf { it.isNotBlank() } ?: "$defaultPrefix-${item.mediaId}"
        return ingestDocumentItem(
            DownloadItem(
                downloadId = item.mediaId,
                uri = item.uri,
                displayName = name,
                mimeType = item.mimeType,
                dateAdded = item.dateAdded,
                size = item.size,
            ),
        )
    }

    private suspend fun ingestDocumentItem(item: DownloadItem): Boolean {
        val name = item.displayName?.takeIf { it.isNotBlank() } ?: "download-${item.downloadId}"
        val extension = name.substringAfterLast('.', "").lowercase()
        val documentId = stableId(item.uri)
        val timestamp = item.dateAdded * 1000L
        val document =
            DocumentEntity(
                id = documentId,
                path = item.uri,
                name = name,
                extension = extension,
                mimeType = item.mimeType.orEmpty(),
                size = item.size,
                checksum = "",
                createdAt = timestamp,
                modifiedAt = timestamp,
                indexedAt = null,
                projectId = null,
                embeddingId = null,
                importance = 0,
            )
        documentRepository.insert(document)
        searchIndexPipeline.indexDocument(document)
        return true
    }

    private fun stableId(value: String): UUID = UUID.nameUUIDFromBytes(value.toByteArray())

    data class IngestionResult(
        val photosIngested: Int,
        val photosSkipped: Int = 0,
        val videosIngested: Int = 0,
        val videosSkipped: Int = 0,
        val audioIngested: Int = 0,
        val audioSkipped: Int = 0,
        val documentsIngested: Int,
        val documentsSkipped: Int = 0,
    )

    data class ItemIngestionResult(
        val ingested: Int,
        val skipped: Int,
        val queried: Int = ingested + skipped,
    )

    companion object {
        const val DEFAULT_BATCH_LIMIT = SearchIndexPipeline.DEFAULT_BATCH_LIMIT
    }
}
