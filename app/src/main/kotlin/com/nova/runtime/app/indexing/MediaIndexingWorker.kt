package com.nova.runtime.app.indexing

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.nova.runtime.ai.native.ingestion.FullDeviceIndexer
import com.nova.runtime.events.EventBus
import com.nova.runtime.events.RuntimeEvent
import com.nova.runtime.events.storage.StorageEvents
import com.nova.runtime.models.EventPriority
import com.nova.runtime.models.RuntimeModule
import java.util.UUID
import java.util.concurrent.TimeUnit
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** Background WorkManager job for incremental full-device media indexing (DPS §10). */
class MediaIndexingWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params), KoinComponent {
    private val indexer: FullDeviceIndexer by inject()
    private val eventBus: EventBus by inject()

    override suspend fun doWork(): Result {
        val traceId = UUID.randomUUID()
        eventBus.publish(
            RuntimeEvent(
                traceId = traceId,
                sourceModule = RuntimeModule.STORAGE,
                eventType = StorageEvents.INDEXING_STARTED,
                priority = EventPriority.NORMAL,
                payload = mapOf("message" to "Background media indexing batch started"),
            ),
        )

        return runCatching { indexer.runBatch() }
            .fold(
                onSuccess = { batch ->
                    if (batch.hasMore) {
                        MediaIndexingScheduler.enqueueNextBatch(applicationContext)
                    } else {
                        eventBus.publish(
                            RuntimeEvent(
                                traceId = traceId,
                                sourceModule = RuntimeModule.STORAGE,
                                eventType = StorageEvents.INDEXING_COMPLETED,
                                priority = EventPriority.NORMAL,
                                payload =
                                    mapOf(
                                        "message" to "Full-device indexing cycle complete (${batch.totalIndexed} items indexed)",
                                        "totalIndexed" to batch.totalIndexed.toString(),
                                    ),
                            ),
                        )
                    }
                    Result.success()
                },
                onFailure = { error ->
                    eventBus.publish(
                        RuntimeEvent(
                            traceId = traceId,
                            sourceModule = RuntimeModule.STORAGE,
                            eventType = StorageEvents.INDEXING_COMPLETED,
                            priority = EventPriority.NORMAL,
                            payload =
                                mapOf(
                                    "message" to "Indexing batch failed: ${error.message ?: "unknown error"}",
                                ),
                        ),
                    )
                    Result.retry()
                },
            )
    }
}

object MediaIndexingScheduler {
    private const val UNIQUE_BATCH_WORK = "nova_media_indexing_batch"
    private const val UNIQUE_PERIODIC_WORK = "nova_media_indexing_periodic"

    fun startFullIndexing(context: Context) {
        enqueueNextBatch(context)
        schedulePeriodic(context)
    }

    fun enqueueNextBatch(context: Context) {
        val request =
            OneTimeWorkRequestBuilder<MediaIndexingWorker>()
                .setConstraints(defaultConstraints())
                .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            UNIQUE_BATCH_WORK,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            request,
        )
    }

    private fun schedulePeriodic(context: Context) {
        val request =
            PeriodicWorkRequestBuilder<MediaIndexingWorker>(6, TimeUnit.HOURS)
                .setConstraints(defaultConstraints())
                .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_PERIODIC_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    private fun defaultConstraints(): Constraints =
        Constraints.Builder()
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .build()
}
