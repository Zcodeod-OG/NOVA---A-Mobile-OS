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
import com.nova.runtime.ai.native.indexing.MessageEmbeddingIndexer
import java.util.concurrent.TimeUnit
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** Background WorkManager job that embeds ingested WhatsApp/Gmail message bodies. */
class MessageIndexingWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params), KoinComponent {
    private val messageEmbeddingIndexer: MessageEmbeddingIndexer by inject()

    override suspend fun doWork(): Result =
        runCatching {
            val indexed = messageEmbeddingIndexer.indexBatch()
            if (indexed > 0) {
                MessageIndexingScheduler.enqueueFollowUp(applicationContext)
            }
            Result.success()
        }.getOrElse { Result.retry() }
}

object MessageIndexingScheduler {
    private const val WORK_NAME = "nova_message_embedding_index"
    private const val PERIODIC_WORK_NAME = "nova_message_embedding_periodic"

    fun start(applicationContext: Context) {
        val constraints =
            Constraints.Builder()
                .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                .build()
        val request =
            OneTimeWorkRequestBuilder<MessageIndexingWorker>()
                .setConstraints(constraints)
                .build()
        WorkManager.getInstance(applicationContext).enqueueUniqueWork(
            WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request,
        )

        val periodic =
            PeriodicWorkRequestBuilder<MessageIndexingWorker>(2, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()
        WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            periodic,
        )
    }

    fun enqueueFollowUp(applicationContext: Context) {
        val request = OneTimeWorkRequestBuilder<MessageIndexingWorker>().build()
        WorkManager.getInstance(applicationContext).enqueue(request)
    }
}
