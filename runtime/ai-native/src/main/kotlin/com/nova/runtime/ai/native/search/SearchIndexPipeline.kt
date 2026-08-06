package com.nova.runtime.ai.native.search

import com.nova.runtime.ai.native.indexing.EmbeddingIndexer
import com.nova.runtime.storage.dao.DocumentDao
import com.nova.runtime.storage.dao.PhotoDao
import com.nova.runtime.storage.entities.DocumentEntity
import com.nova.runtime.storage.entities.PhotoEntity
import com.nova.runtime.storage.repository.DocumentRepository
import com.nova.runtime.storage.repository.PhotoRepository
import java.util.UUID

/**
 * Ensures photos and documents are embedded and indexed before semantic search (DPS §9).
 */
class SearchIndexPipeline(
    private val embeddingIndexer: EmbeddingIndexer,
    private val photoDao: PhotoDao,
    private val documentDao: DocumentDao,
    private val photoRepository: PhotoRepository,
    private val documentRepository: DocumentRepository,
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
        val pending = photoDao.listUnindexedWithOcr(limit)
        pending.forEach { photo -> indexPhoto(photo) }
    }

    suspend fun indexDocuments(limit: Int = DEFAULT_BATCH_LIMIT) {
        val pending = documentDao.listUnindexed(limit)
        pending.forEach { document -> indexDocument(document) }
    }

    suspend fun indexPhoto(photo: PhotoEntity) {
        val text = photo.ocrText?.takeIf { it.isNotBlank() } ?: return
        val embeddingId = embeddingIndexer.indexText(photo.id, OBJECT_TYPE_PHOTO, text) ?: return
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
