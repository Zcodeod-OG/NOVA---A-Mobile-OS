package com.nova.runtime.ai.native.ingestion

import com.nova.runtime.events.EventBus
import com.nova.runtime.events.RuntimeEvent
import com.nova.runtime.events.storage.StorageEvents
import com.nova.runtime.models.EventPriority
import com.nova.runtime.models.RuntimeModule
import com.nova.runtime.utils.logging.NovaLogger
import java.util.UUID
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Orchestrates paginated, resumable full-device media indexing across all categories.
 * Each [runBatch] processes one page for the current category and advances the checkpoint.
 */
class FullDeviceIndexer(
    private val ingestionService: MediaStoreIngestionService,
    private val checkpointStore: IndexingCheckpointStore,
    private val eventBus: EventBus,
    private val logger: NovaLogger,
) {
    private val batchMutex = Mutex()

    suspend fun runBatch(batchSize: Int = DEFAULT_BATCH_SIZE): BatchResult =
        batchMutex.withLock {
            val category = checkpointStore.getCurrentCategory()
            val offset = checkpointStore.getOffset(category)
            val traceId = UUID.randomUUID()

            publishProgress(
                traceId = traceId,
                category = category,
                message = "Indexing ${category.name.lowercase()}…",
                indexedInBatch = 0,
                offset = offset,
            )

            val itemResult =
                when (category) {
                    IndexCategory.PHOTOS -> ingestionService.ingestPhotos(batchSize, offset)
                    IndexCategory.VIDEOS -> ingestionService.ingestVideos(batchSize, offset)
                    IndexCategory.AUDIO -> ingestionService.ingestAudio(batchSize, offset)
                    IndexCategory.DOWNLOADS -> ingestionService.ingestDownloads(batchSize, offset)
                    IndexCategory.DOCUMENTS -> ingestionService.ingestFiles(batchSize, offset)
                }

            val hasMoreInCategory = itemResult.queried >= batchSize
            if (hasMoreInCategory) {
                checkpointStore.setOffset(category, offset + batchSize)
            } else {
                checkpointStore.resetCategory(category)
                val nextCategory = checkpointStore.advanceToNextCategory()
                if (nextCategory == IndexCategory.PHOTOS) {
                    checkpointStore.markFullCycleComplete()
                }
            }

            if (itemResult.ingested > 0) {
                checkpointStore.incrementTotalIndexed(itemResult.ingested)
            }

            ingestionService.indexPendingEmbeddings(batchSize)

            val result =
                BatchResult(
                    category = category,
                    ingested = itemResult.ingested,
                    skipped = itemResult.skipped,
                    queried = itemResult.queried,
                    totalIndexed = checkpointStore.getTotalIndexed(),
                    hasMore = hasMoreInCategory || checkpointStore.getCurrentCategory() != category,
                    offset = checkpointStore.getOffset(checkpointStore.getCurrentCategory()),
                )

            publishProgress(
                traceId = traceId,
                category = category,
                message = buildProgressMessage(result),
                indexedInBatch = itemResult.ingested,
                offset = result.offset,
            )

            logger.info(
                module = RuntimeModule.STORAGE.name,
                message = "Full-device indexing batch completed",
                traceId = traceId,
                metadata =
                    mapOf(
                        "category" to category.name,
                        "ingested" to itemResult.ingested.toString(),
                        "skipped" to itemResult.skipped.toString(),
                        "queried" to itemResult.queried.toString(),
                        "totalIndexed" to result.totalIndexed.toString(),
                        "hasMore" to result.hasMore.toString(),
                    ),
            )
            result
        }

    /** Runs small priority batches so search-on-query stays responsive. */
    suspend fun runPrioritySync(batchSize: Int = PRIORITY_BATCH_SIZE): IngestionSummary {
        val photos = ingestionService.ingestPhotos(batchSize, offset = 0)
        val documents = ingestionService.ingestDownloads(batchSize, offset = 0)
        ingestionService.indexPendingEmbeddings(batchSize)
        return IngestionSummary(
            photosIngested = photos.ingested,
            documentsIngested = documents.ingested,
        )
    }

    private suspend fun publishProgress(
        traceId: UUID,
        category: IndexCategory,
        message: String,
        indexedInBatch: Int,
        offset: Int,
    ) {
        eventBus.publish(
            RuntimeEvent(
                traceId = traceId,
                sourceModule = RuntimeModule.STORAGE,
                eventType = StorageEvents.INDEXING_PROGRESS,
                priority = EventPriority.NORMAL,
                payload =
                    mapOf(
                        "message" to message,
                        "category" to category.name,
                        "indexedInBatch" to indexedInBatch.toString(),
                        "totalIndexed" to checkpointStore.getTotalIndexed().toString(),
                        "offset" to offset.toString(),
                    ),
            ),
        )
    }

    private fun buildProgressMessage(result: BatchResult): String {
        val action = if (result.ingested > 0) "Indexed ${result.ingested}" else "Scanned"
        return "$action ${result.category.name.lowercase()} items (${result.totalIndexed} total indexed)"
    }

    data class BatchResult(
        val category: IndexCategory,
        val ingested: Int,
        val skipped: Int,
        val queried: Int,
        val totalIndexed: Long,
        val hasMore: Boolean,
        val offset: Int,
    )

    data class IngestionSummary(
        val photosIngested: Int,
        val documentsIngested: Int,
    )

    companion object {
        const val DEFAULT_BATCH_SIZE = 50
        const val PRIORITY_BATCH_SIZE = 25
    }
}

typealias IngestionResult = MediaStoreIngestionService.IngestionResult
