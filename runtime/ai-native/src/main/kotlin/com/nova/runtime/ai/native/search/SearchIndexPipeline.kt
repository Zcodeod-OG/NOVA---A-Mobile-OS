package com.nova.runtime.ai.native.search

import com.nova.runtime.ai.native.indexing.EmbeddingIndexer
import com.nova.runtime.ai.native.indexing.EmbeddingMetadata
import com.nova.runtime.ai.native.ingestion.PhotoImageLoader
import com.nova.runtime.storage.dao.DocumentDao
import com.nova.runtime.storage.dao.PhotoDao
import com.nova.runtime.storage.entities.DocumentEntity
import com.nova.runtime.storage.entities.PhotoEntity
import com.nova.runtime.storage.repository.DocumentRepository
import com.nova.runtime.storage.repository.PhotoRepository

/**
 * Ensures photos and documents are embedded and indexed before semantic search (DPS §9).
 */
class SearchIndexPipeline(
    private val embeddingIndexer: EmbeddingIndexer,
    private val photoDao: PhotoDao,
    private val documentDao: DocumentDao,
    private val photoRepository: PhotoRepository,
    private val documentRepository: DocumentRepository,
    private val photoImageLoader: PhotoImageLoader,
) {
    suspend fun ensureIndexed(request: IndexRequest) {
        if (request.indexPhotos) {
            indexPhotos(request.photoLimit)
        }
        if (request.indexDocuments) {
            indexDocuments(request.documentLimit)
        }
    }

    suspend fun indexPhotos(limit: Int = DEFAULT_BATCH_LIMIT) {
        val pending = photoDao.listUnindexed(limit)
        pending.forEach { photo -> indexPhoto(photo) }
    }

    suspend fun indexDocuments(limit: Int = DEFAULT_BATCH_LIMIT) {
        val pending = documentDao.listUnindexed(limit)
        pending.forEach { document -> indexDocument(document) }
    }

    suspend fun indexPhoto(photo: PhotoEntity) {
        val imageBytes = photoImageLoader.loadBytes(photo.uri)
        if (imageBytes != null) {
            val result = embeddingIndexer.indexPhoto(photo.id, imageBytes)
            if (result.embeddingId == null) return
            photoRepository.update(
                photo.copy(
                    ocrText = result.ocrText ?: photo.ocrText,
                    embeddingId = result.embeddingId,
                ),
            )
            return
        }

        val text = photo.ocrText?.takeIf { it.isNotBlank() } ?: return
        val embeddingId = embeddingIndexer.indexText(
            objectId = photo.id,
            objectType = OBJECT_TYPE_PHOTO,
            text = text,
            embeddingKind = EmbeddingMetadata.KIND_OCR,
        ) ?: return
        photoRepository.update(photo.copy(embeddingId = embeddingId))
    }

    suspend fun indexDocument(document: DocumentEntity) {
        val text = buildDocumentIndexText(document)
        val embeddingId = embeddingIndexer.indexText(document.id, OBJECT_TYPE_DOCUMENT, text) ?: return
        documentRepository.update(document.copy(embeddingId = embeddingId, indexedAt = System.currentTimeMillis()))
    }

    private fun buildDocumentIndexText(document: DocumentEntity): String =
        listOf(document.name, document.path, document.extension)
            .filter { it.isNotBlank() }
            .joinToString(" ")

    data class IndexRequest(
        val indexPhotos: Boolean = true,
        val indexDocuments: Boolean = true,
        val photoLimit: Int = DEFAULT_BATCH_LIMIT,
        val documentLimit: Int = DEFAULT_BATCH_LIMIT,
    )

    companion object {
        const val OBJECT_TYPE_PHOTO = "photo"
        const val OBJECT_TYPE_DOCUMENT = "document"
        const val DEFAULT_BATCH_LIMIT = 50
    }
}
