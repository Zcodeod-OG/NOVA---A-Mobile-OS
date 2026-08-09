package com.nova.runtime.ai.native.search

import com.nova.runtime.ai.model.DocumentExtractionResult
import com.nova.runtime.ai.model.DocumentTextExtractor
import com.nova.runtime.ai.native.indexing.EmbeddingIndexer
import com.nova.runtime.ai.native.indexing.EmbeddingMetadata
import com.nova.runtime.ai.native.ingestion.PhotoImageLoader
import com.nova.runtime.storage.dao.DocumentDao
import com.nova.runtime.storage.dao.PhotoDao
import com.nova.runtime.storage.entities.DocumentEntity
import com.nova.runtime.storage.entities.PhotoEntity
import com.nova.runtime.storage.repository.DocumentRepository
import com.nova.runtime.storage.repository.PhotoRepository
import com.nova.runtime.storage.search.ContentExtractStatus
import com.nova.runtime.storage.search.DocumentContentNormalizer
import com.nova.runtime.storage.search.DocumentSummaryGenerator

/**
 * Ensures photos and documents are embedded and indexed before semantic search (DPS §9).
 *
 * Documents use two-stage retrieval:
 * - Stage A discovery embedding: `name + summary` ([EmbeddingMetadata.KIND_SUMMARY])
 * - Stage B answers / snippets: full [DocumentEntity.contentText]
 */
class SearchIndexPipeline(
    private val embeddingIndexer: EmbeddingIndexer,
    private val photoDao: PhotoDao,
    private val documentDao: DocumentDao,
    private val photoRepository: PhotoRepository,
    private val documentRepository: DocumentRepository,
    private val photoImageLoader: PhotoImageLoader,
    private val documentTextExtractor: DocumentTextExtractor? = null,
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
        val unindexed = documentDao.listUnindexed(limit)
        val seen = unindexed.mapTo(mutableSetOf()) { it.id }
        var remaining = (limit - unindexed.size).coerceAtLeast(0)

        // Backfill: docs embedded before content extraction have embeddingId set but
        // contentText null — re-queue extractable ones within the same battery-sane batch.
        val needsContent = if (remaining > 0) {
            documentDao.listMissingContentText(remaining).filter { it.id !in seen }.also { batch ->
                seen += batch.map { it.id }
                remaining = (remaining - batch.size).coerceAtLeast(0)
            }
        } else {
            emptyList()
        }

        // Backfill: docs missing discovery summaries (post-migration or content-only index).
        val needsSummary = if (remaining > 0) {
            documentDao.listMissingSummary(remaining).filter { it.id !in seen }
        } else {
            emptyList()
        }

        (unindexed + needsContent + needsSummary).forEach { document -> indexDocument(document) }
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

    suspend fun indexDocument(document: DocumentEntity, forceExtract: Boolean = false) {
        val withContent = withExtractedContent(document, forceExtract)
        val contentChanged = withContent.contentText != document.contentText ||
            withContent.contentExtractStatus != document.contentExtractStatus ||
            withContent.contentExtractedAt != document.contentExtractedAt
        val forceSummary = document.summary.isNullOrBlank() || contentChanged
        val enriched = withGeneratedSummary(withContent, forceRegenerate = forceSummary)
        val summaryChanged = enriched.summary != document.summary

        // Already embedded, summary stable, and extraction yielded nothing usable — persist markers only.
        if (document.embeddingId != null &&
            enriched.contentText.isNullOrBlank() &&
            !summaryChanged &&
            !contentChanged
        ) {
            return
        }
        if (document.embeddingId != null &&
            enriched.contentText.isNullOrBlank() &&
            (contentChanged || summaryChanged)
        ) {
            documentRepository.update(enriched)
            // Still try discovery embed from name/summary when content is empty.
        }
        // Already embedded with unchanged discovery text — nothing to do.
        if (document.embeddingId != null && !contentChanged && !summaryChanged) return

        val text = buildDocumentDiscoveryText(enriched)
        val embeddingId = embeddingIndexer.indexText(
            objectId = enriched.id,
            objectType = OBJECT_TYPE_DOCUMENT,
            text = text,
            embeddingKind = EmbeddingMetadata.KIND_SUMMARY,
            replaceExisting = document.embeddingId != null,
        ) ?: run {
            // Persist extracted content/summary even when embedding fails so keyword search can use it.
            if (contentChanged || summaryChanged) {
                documentRepository.update(enriched)
            }
            return
        }
        documentRepository.update(
            enriched.copy(embeddingId = embeddingId, indexedAt = System.currentTimeMillis()),
        )
    }

    /** Extracts document content (PDF/image via OCR, text/HTML/DOCX) when missing or blank. */
    private suspend fun withExtractedContent(
        document: DocumentEntity,
        forceExtract: Boolean = false,
    ): DocumentEntity {
        val existing = document.contentText
        if (!forceExtract &&
            document.contentExtractStatus == ContentExtractStatus.SUCCESS &&
            !existing.isNullOrBlank()
        ) {
            val plain = DocumentContentNormalizer.toPlainText(existing)
            return if (plain != existing) {
                document.copy(contentText = plain)
            } else {
                document
            }
        }
        val extractor = documentTextExtractor ?: return document
        if (!DocumentTextExtractor.isExtractable(document.mimeType, document.extension)) {
            return document
        }
        val result = extractor.extract(
            uri = document.path,
            mimeType = document.mimeType,
            extension = document.extension,
        )
        return applyExtractionResult(document, result)
    }

    private fun applyExtractionResult(
        document: DocumentEntity,
        result: DocumentExtractionResult,
    ): DocumentEntity {
        val now = System.currentTimeMillis()
        return when (result) {
            is DocumentExtractionResult.Success -> {
                val plain = DocumentContentNormalizer.toPlainText(result.text)
                document.copy(
                    contentText = plain,
                    contentExtractStatus = ContentExtractStatus.SUCCESS,
                    contentExtractedAt = now,
                )
            }
            DocumentExtractionResult.Empty ->
                document.copy(
                    contentText = "",
                    contentExtractStatus = ContentExtractStatus.EMPTY,
                    contentExtractedAt = now,
                )
            is DocumentExtractionResult.Unreadable ->
                // Keep status FAILED with empty body so URI-permission recovery can retry.
                document.copy(
                    contentText = "",
                    contentExtractStatus = ContentExtractStatus.FAILED,
                    contentExtractedAt = now,
                )
            DocumentExtractionResult.Unsupported -> document
        }
    }

    private fun withGeneratedSummary(
        document: DocumentEntity,
        forceRegenerate: Boolean,
    ): DocumentEntity {
        if (!forceRegenerate && !document.summary.isNullOrBlank()) {
            return document
        }
        val summary = DocumentSummaryGenerator.generate(
            name = document.name,
            contentText = document.contentText,
        )
        return document.copy(summary = summary)
    }

    /** Stage A discovery text: filename + short extractive summary (not full OCR body). */
    private fun buildDocumentDiscoveryText(document: DocumentEntity): String {
        val summary = document.summary?.takeIf { it.isNotBlank() }
            ?: DocumentSummaryGenerator.generate(document.name, document.contentText)
        return listOf(document.name, summary)
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(" ")
            .take(DISCOVERY_EMBED_CHARS)
    }

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

        /** Discovery embeddings stay short — MiniLM truncates past ~512 tokens anyway. */
        const val DISCOVERY_EMBED_CHARS = 512
    }
}
