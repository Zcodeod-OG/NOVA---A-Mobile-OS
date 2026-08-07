package com.nova.runtime.ai.native.search

import com.nova.runtime.ai.model.EmbeddingGenerator
import com.nova.runtime.ai.model.EmbeddingResult
import com.nova.runtime.ai.model.ImageEmbeddingGenerator
import com.nova.runtime.ai.native.indexing.EmbeddingMetadata
import com.nova.runtime.ai.native.ingestion.FullDeviceIndexer
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
    private val imageEmbeddingGenerator: ImageEmbeddingGenerator,
    private val vectorIndex: CosineVectorIndex,
    private val photoRepository: PhotoRepository,
    private val documentRepository: DocumentRepository,
    private val searchIndexPipeline: SearchIndexPipeline,
    private val mediaStoreIngestionService: MediaStoreIngestionService,
    private val fullDeviceIndexer: FullDeviceIndexer? = null,
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
                fullDeviceIndexer?.runPrioritySync() ?: mediaStoreIngestionService.ensureSynced()
                searchIndexPipeline.ensureIndexed(
                    SearchIndexPipeline.IndexRequest(
                        indexPhotos = OBJECT_TYPE_PHOTO in objectTypes,
                        indexDocuments = OBJECT_TYPE_DOCUMENT in objectTypes,
                    ),
                )
            }

            val fetchK = (request.offset + request.limit).coerceAtMost(SearchRequest.MAX_LIMIT)
            val rawHits = searchIndexedObjects(normalizedQuery, traceId, objectTypes, fetchK)
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

    private suspend fun searchIndexedObjects(
        query: String,
        traceId: UUID,
        objectTypes: Set<String>,
        fetchK: Int,
    ): List<RankedObjectHit> {
        if (query.isBlank()) return emptyList()

        val hits = mutableListOf<RankedObjectHit>()
        val k = fetchK.coerceAtLeast(1)

        if (OBJECT_TYPE_DOCUMENT in objectTypes) {
            embedTextQuery(query, traceId)?.let { queryVector ->
                hits += vectorIndex.search(
                    VectorSearchRequest(
                        queryVector = queryVector,
                        k = k,
                        metadataFilter = mapOf(EmbeddingMetadata.OBJECT_TYPE to OBJECT_TYPE_DOCUMENT),
                    ),
                ).map { RankedObjectHit(it.objectId, it.objectType, it.score) }
            }
        }

        if (OBJECT_TYPE_PHOTO in objectTypes) {
            embedImageQuery(query, traceId)?.let { queryVector ->
                hits += vectorIndex.search(
                    VectorSearchRequest(
                        queryVector = queryVector,
                        k = k,
                        metadataFilter = mapOf(
                            EmbeddingMetadata.OBJECT_TYPE to OBJECT_TYPE_PHOTO,
                            EmbeddingMetadata.EMBEDDING_KIND to EmbeddingMetadata.KIND_IMAGE,
                        ),
                    ),
                ).map { RankedObjectHit(it.objectId, it.objectType, it.score) }
            }

            embedTextQuery(query, traceId)?.let { queryVector ->
                hits += vectorIndex.search(
                    VectorSearchRequest(
                        queryVector = queryVector,
                        k = k,
                        metadataFilter = mapOf(
                            EmbeddingMetadata.OBJECT_TYPE to OBJECT_TYPE_PHOTO,
                            EmbeddingMetadata.EMBEDDING_KIND to EmbeddingMetadata.KIND_OCR,
                        ),
                    ),
                ).map { RankedObjectHit(it.objectId, it.objectType, it.score) }
            }
        }

        return hits
            .groupBy { it.objectId to it.objectType }
            .map { (_, grouped) -> grouped.maxBy { it.score } }
            .sortedByDescending { it.score }
            .take(k)
    }

    private suspend fun embedTextQuery(query: String, traceId: UUID): FloatArray? =
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

    private suspend fun embedImageQuery(query: String, traceId: UUID): FloatArray? =
        when (val result = imageEmbeddingGenerator.embedQuery(query)) {
            is EmbeddingResult.Success -> result.vector
            is EmbeddingResult.Failure -> {
                logger.warn(
                    module = RuntimeModule.STORAGE.name,
                    message = "Image query embedding failed: ${result.message}",
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

    private data class RankedObjectHit(
        val objectId: UUID,
        val objectType: String,
        val score: Float,
    )

    companion object {
        const val OBJECT_TYPE_PHOTO = SearchIndexPipeline.OBJECT_TYPE_PHOTO
        const val OBJECT_TYPE_DOCUMENT = SearchIndexPipeline.OBJECT_TYPE_DOCUMENT
        val DEFAULT_OBJECT_TYPES = setOf(OBJECT_TYPE_PHOTO, OBJECT_TYPE_DOCUMENT)
    }
}
