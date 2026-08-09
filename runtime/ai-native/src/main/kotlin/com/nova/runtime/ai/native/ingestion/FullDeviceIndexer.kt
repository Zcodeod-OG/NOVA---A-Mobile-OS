package com.nova.runtime.ai.native.ingestion

import com.nova.runtime.events.EventBus
import com.nova.runtime.events.RuntimeEvent
import com.nova.runtime.events.storage.StorageEvents
import com.nova.runtime.models.EventPriority
import com.nova.runtime.models.RuntimeModule
import com.nova.runtime.storage.dao.DocumentDao
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
    private val documentDao: DocumentDao? = null,
) {
    private val batchMutex = Mutex()

    suspend fun runBatch(batchSize: Int = DEFAULT_BATCH_SIZE): BatchResult =
        batchMutex.withLock {
            val category = checkpointStore.getCurrentCategory()
            val offset = checkpointStore.getOffset(category)
            val traceId = UUID.randomUUID()

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

            val summaryStats = loadSummaryStats()
            val hasMore = hasMoreInCategory || checkpointStore.getCurrentCategory() != category
            val result =
                BatchResult(
                    category = category,
                    ingested = itemResult.ingested,
                    skipped = itemResult.skipped,
                    queried = itemResult.queried,
                    totalIndexed = checkpointStore.getTotalIndexed(),
                    hasMore = hasMore,
                    offset = checkpointStore.getOffset(checkpointStore.getCurrentCategory()),
                    summariesReady = summaryStats.ready,
                    summariesPending = summaryStats.pending,
                    documentsTotal = summaryStats.total,
                )

            publishProgress(
                traceId = traceId,
                category = category,
                message = buildProgressMessage(result),
                indexedInBatch = itemResult.ingested,
                offset = result.offset,
                summaryStats = summaryStats,
                hasMore = hasMore,
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
                        "summariesReady" to summaryStats.ready.toString(),
                        "summariesPending" to summaryStats.pending.toString(),
                    ),
            )
            result
        }

    /** Runs small priority batches so search-on-query stays responsive. */
    suspend fun runPrioritySync(batchSize: Int = PRIORITY_BATCH_SIZE): IngestionSummary {
        // Force Downloads + Files before search so "mess menu" PDFs are visible immediately.
        val photos = ingestionService.ingestPhotos(batchSize, offset = 0)
        val downloads = ingestionService.ingestDownloads(batchSize, offset = 0)
        val files = ingestionService.ingestFiles(batchSize, offset = 0)
        ingestionService.indexPendingEmbeddings(batchSize)
        val documentsIngested = downloads.ingested + files.ingested
        if (downloads.queried == 0 && files.queried == 0) {
            logger.warn(
                module = RuntimeModule.STORAGE.name,
                message = "Priority document sync saw 0 MediaStore files — grant All files access so Downloads/PDFs are visible",
                metadata = mapOf(
                    "downloadsQueried" to downloads.queried.toString(),
                    "filesQueried" to files.queried.toString(),
                ),
            )
        } else {
            logger.info(
                module = RuntimeModule.STORAGE.name,
                message = "Priority document sync completed",
                metadata = mapOf(
                    "downloadsQueried" to downloads.queried.toString(),
                    "downloadsIngested" to downloads.ingested.toString(),
                    "filesQueried" to files.queried.toString(),
                    "filesIngested" to files.ingested.toString(),
                    "documentsIngested" to documentsIngested.toString(),
                ),
            )
        }
        return IngestionSummary(
            photosIngested = photos.ingested,
            documentsIngested = documentsIngested,
        )
    }

    private suspend fun publishProgress(
        traceId: UUID,
        category: IndexCategory,
        message: String,
        indexedInBatch: Int,
        offset: Int,
        summaryStats: SummaryStats,
        hasMore: Boolean,
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
                        "summariesReady" to summaryStats.ready.toString(),
                        "summariesPending" to summaryStats.pending.toString(),
                        "documentsTotal" to summaryStats.total.toString(),
                        "hasMore" to hasMore.toString(),
                    ),
            ),
        )
    }

    private fun buildProgressMessage(result: BatchResult): String {
        val base = "Indexing ${result.category.displayName} · ${result.totalIndexed}"
        if (result.documentsTotal <= 0 || result.summariesPending <= 0) return base
        return "$base · Summaries ${result.summariesReady}/${result.documentsTotal}"
    }

    private suspend fun loadSummaryStats(): SummaryStats {
        val dao = documentDao ?: return SummaryStats(0, 0, 0)
        return runCatching {
            val total = dao.countAll()
            val ready = dao.countWithSummary()
            val pending = dao.countMissingSummary()
            SummaryStats(total = total, ready = ready, pending = pending)
        }.getOrDefault(SummaryStats(0, 0, 0))
    }

    data class BatchResult(
        val category: IndexCategory,
        val ingested: Int,
        val skipped: Int,
        val queried: Int,
        val totalIndexed: Long,
        val hasMore: Boolean,
        val offset: Int,
        val summariesReady: Int = 0,
        val summariesPending: Int = 0,
        val documentsTotal: Int = 0,
    )

    data class IngestionSummary(
        val photosIngested: Int,
        val documentsIngested: Int,
    )

    data class SummaryStats(
        val total: Int,
        val ready: Int,
        val pending: Int,
    )

    companion object {
        const val DEFAULT_BATCH_SIZE = 50
        const val PRIORITY_BATCH_SIZE = 75
    }
}

typealias IngestionResult = MediaStoreIngestionService.IngestionResult
