package com.nova.runtime.ai.native.ingestion

import android.content.Context
import android.net.Uri
import com.nova.runtime.ai.native.indexing.EmbeddingIndexer
import com.nova.runtime.ai.native.search.SearchIndexPipeline
import com.nova.runtime.models.RuntimeModule
import com.nova.runtime.storage.dao.DocumentDao
import com.nova.runtime.storage.dao.PhotoDao
import com.nova.runtime.storage.entities.DocumentEntity
import com.nova.runtime.storage.entities.PhotoEntity
import com.nova.runtime.storage.repository.DocumentRepository
import com.nova.runtime.storage.repository.PhotoRepository
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
 * Syncs gallery photos and download documents from MediaStore into Room,
 * runs OCR/embedding for photos, then delegates vector indexing to [SearchIndexPipeline].
 */
class MediaStoreIngestionService(
    private val mediaStoreQuery: MediaStoreQueryPort,
    private val downloadsQuery: DownloadsQueryPort,
    private val photoDao: PhotoDao,
    private val documentDao: DocumentDao,
    private val photoRepository: PhotoRepository,
    private val documentRepository: DocumentRepository,
    private val embeddingIndexer: EmbeddingIndexer,
    private val searchIndexPipeline: SearchIndexPipeline,
    private val context: Context,
    private val logger: NovaLogger,
) {
    private val syncMutex = Mutex()

    @Volatile
    private var hasSynced = false

    suspend fun ensureSynced(limit: Int = DEFAULT_BATCH_LIMIT) {
        if (hasSynced) return
        syncMutex.withLock {
            if (hasSynced) return
            syncAll(limit)
            hasSynced = true
        }
    }

    suspend fun syncAll(limit: Int = DEFAULT_BATCH_LIMIT): IngestionResult {
        val photoResult = ingestPhotos(limit)
        val documentResult = ingestDocuments(limit)
        searchIndexPipeline.ensureIndexed(
            SearchIndexPipeline.IndexRequest(
                indexPhotos = true,
                indexDocuments = true,
                photoLimit = limit,
                documentLimit = limit,
            ),
        )
        val result = IngestionResult(
            photosIngested = photoResult.ingested,
            photosSkipped = photoResult.skipped,
            documentsIngested = documentResult.ingested,
            documentsSkipped = documentResult.skipped,
        )
        logger.info(
            module = RuntimeModule.STORAGE.name,
            message = "MediaStore ingestion completed",
            metadata = mapOf(
                "photosIngested" to result.photosIngested.toString(),
                "photosSkipped" to result.photosSkipped.toString(),
                "documentsIngested" to result.documentsIngested.toString(),
                "documentsSkipped" to result.documentsSkipped.toString(),
            ),
        )
        hasSynced = true
        return result
    }

    suspend fun ingestPhotos(limit: Int = DEFAULT_BATCH_LIMIT): ItemIngestionResult =
        withContext(Dispatchers.IO) {
            val items = mediaStoreQuery.queryImages(limit).items
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
            ItemIngestionResult(ingested = ingested, skipped = skipped)
        }

    suspend fun ingestDocuments(limit: Int = DEFAULT_BATCH_LIMIT): ItemIngestionResult =
        withContext(Dispatchers.IO) {
            val items = downloadsQuery.queryDownloads(limit).items
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
            ItemIngestionResult(ingested = ingested, skipped = skipped)
        }

    private suspend fun ingestPhotoItem(item: MediaImageItem): Boolean {
        val imageBytes = readImageBytes(item.uri) ?: return false
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
        if (indexResult.embeddingId == null && !indexResult.ocrText.isNullOrBlank()) {
            searchIndexPipeline.indexPhoto(photo)
        }
        return true
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

    private suspend fun readImageBytes(uriString: String): ByteArray? =
        withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openInputStream(Uri.parse(uriString))?.use { stream ->
                    val bytes = stream.readBytes()
                    if (bytes.size > MAX_IMAGE_BYTES) {
                        bytes.copyOf(MAX_IMAGE_BYTES)
                    } else {
                        bytes
                    }
                }
            }.getOrNull()
        }

    private fun stableId(value: String): UUID = UUID.nameUUIDFromBytes(value.toByteArray())

    data class IngestionResult(
        val photosIngested: Int,
        val photosSkipped: Int,
        val documentsIngested: Int,
        val documentsSkipped: Int,
    )

    data class ItemIngestionResult(
        val ingested: Int,
        val skipped: Int,
    )

    companion object {
        const val DEFAULT_BATCH_LIMIT = SearchIndexPipeline.DEFAULT_BATCH_LIMIT
        private const val MAX_IMAGE_BYTES = 10 * 1024 * 1024
    }
}
