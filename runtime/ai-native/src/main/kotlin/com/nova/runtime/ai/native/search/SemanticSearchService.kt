package com.nova.runtime.ai.native.search

import com.nova.runtime.ai.model.EmbeddingGenerator
import com.nova.runtime.ai.model.EmbeddingResult
import com.nova.runtime.ai.native.ingestion.MediaStoreIngestionService
import com.nova.runtime.ai.native.storage.CosineVectorIndex
import com.nova.runtime.models.RuntimeModule
import com.nova.runtime.storage.repository.DocumentRepository
import com.nova.runtime.storage.repository.PhotoRepository
import com.nova.runtime.storage.search.SearchPage
import com.nova.runtime.storage.search.SearchRequest
import com.nova.runtime.storage.search.SemanticSearchHit
import com.nova.runtime.storage.vector.VectorSearchRequest
import com.nova.runtime.utils.logging.NovaLogger
import java.util.UUID
import kotlin.system.measureTimeMillis

/** Semantic KNN search over indexed photo and document embeddings (DPS §7). */
class SemanticSearchService(
    private val embeddingGenerator: EmbeddingGenerator,
    private val vectorIndex: CosineVectorIndex,
    private val photoRepository: PhotoRepository,
    private val documentRepository: DocumentRepository,
    private val searchIndexPipeline: SearchIndexPipeline,
    private val mediaStoreIngestionService: MediaStoreIngestionService,
    private val logger: NovaLogger,
) {
    suspend fun search(
        request: SearchRequest,
        traceId: UUID,
        objectTypes: Set<String> = DEFAULT_OBJECT_TYPES,
    ): SearchPage<SemanticSearchHit> {
        val normalizedQuery = request.query.trim()
        var page: SearchPage<SemanticSearchHit> = emptyPage(request)

        val latencyMs = measureTimeMillis {
            if (request.indexOnQuery) {
                mediaStoreIngestionService.ensureSynced()
                searchIndexPipeline.ensureIndexed(
                    SearchIndexPipeline.IndexRequest(
                        indexPhotos = OBJECT_TYPE_PHOTO in objectTypes,
                        indexDocuments = OBJECT_TYPE_DOCUMENT in objectTypes,
                    ),
                )
            }

            val queryVector = embedQuery(normalizedQuery, traceId) ?: return@measureTimeMillis
            val fetchK = (request.offset + request.limit).coerceAtMost(SearchRequest.MAX_LIMIT)
            val rawHits = vectorIndex.search(
                VectorSearchRequest(
                    queryVector = queryVector,
                    k = fetchK.coerceAtLeast(1),
                ),
            ).filter { hit -> hit.objectType in objectTypes }

            val hits = rawHits
                .drop(request.offset)
                .take(request.limit)
                .mapNotNull { hit -> resolveHit(hit.objectId, hit.objectType, hit.score) }

            page = SearchPage(
                items = hits,
                totalCount = rawHits.size,
                query = normalizedQuery,
                limit = request.limit,
                offset = request.offset,
            )
        }

        logger.info(
            module = RuntimeModule.STORAGE.name,
            message = "Semantic search completed",
            traceId = traceId,
            durationMs = latencyMs,
            metadata = mapOf(
                "query" to normalizedQuery,
                "count" to page.count.toString(),
                "totalCount" to page.totalCount.toString(),
                "objectTypes" to objectTypes.joinToString(","),
            ),
        )

        return page
    }

    private suspend fun embedQuery(query: String, traceId: UUID): FloatArray? =
        when (val result = embeddingGenerator.embed(query)) {
            is EmbeddingResult.Success -> result.vector
            is EmbeddingResult.Failure -> {
                logger.warn(
                    module = RuntimeModule.STORAGE.name,
                    message = "Query embedding failed: ${result.message}",
                    traceId = traceId,
                )
                null
            }
        }

    private suspend fun resolveHit(
        objectId: UUID,
        objectType: String,
        score: Float,
    ): SemanticSearchHit? =
        when (objectType) {
            OBJECT_TYPE_PHOTO -> {
                val photo = photoRepository.getById(objectId) ?: return null
                SemanticSearchHit(
                    objectId = objectId,
                    objectType = objectType,
                    score = score,
                    title = photo.uri.substringAfterLast('/'),
                    snippet = photo.ocrText?.take(120),
                )
            }
            OBJECT_TYPE_DOCUMENT -> {
                val document = documentRepository.getById(objectId) ?: return null
                SemanticSearchHit(
                    objectId = objectId,
                    objectType = objectType,
                    score = score,
                    title = document.name,
                    snippet = document.path,
                )
            }
            else -> null
        }

    private fun emptyPage(request: SearchRequest): SearchPage<SemanticSearchHit> =
        SearchPage(
            items = emptyList(),
            totalCount = 0,
            query = request.query.trim(),
            limit = request.limit,
            offset = request.offset,
        )

    companion object {
        const val OBJECT_TYPE_PHOTO = SearchIndexPipeline.OBJECT_TYPE_PHOTO
        const val OBJECT_TYPE_DOCUMENT = SearchIndexPipeline.OBJECT_TYPE_DOCUMENT
        val DEFAULT_OBJECT_TYPES = setOf(OBJECT_TYPE_PHOTO, OBJECT_TYPE_DOCUMENT)
    }
}
