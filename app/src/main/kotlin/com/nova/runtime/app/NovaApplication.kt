package com.nova.runtime.app

import android.app.Application
import com.nova.runtime.ai.native.ingestion.MediaStoreIngestionService
import com.nova.runtime.ai.native.search.di.SearchCapabilityRegistrar
import com.nova.runtime.app.di.aiIntegrationModule
import com.nova.runtime.app.di.appUiModule
import com.nova.runtime.app.di.executionPersistenceModule
import com.nova.runtime.app.di.runtimeModule
import com.nova.runtime.events.EventBus
import com.nova.runtime.events.RuntimeEvent
import com.nova.runtime.events.storage.StorageEvents
import com.nova.runtime.kernel.RuntimeKernel
import com.nova.runtime.models.EventPriority
import com.nova.runtime.models.RuntimeModule
import com.nova.runtime.storage.di.storageModule
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin

class NovaApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@NovaApplication)
            allowOverride(true)
            modules(
                runtimeModule,
                storageModule(this@NovaApplication),
                executionPersistenceModule,
                aiIntegrationModule,
                appUiModule,
            )
        }
        applicationScope.launch {
            val koin = GlobalContext.get()
            koin.get<SearchCapabilityRegistrar>()
            koin.get<RuntimeKernel>().bootstrap()
            startBackgroundIngestion(koin.get(), koin.get())
        }
    }

    private suspend fun startBackgroundIngestion(
        ingestionService: MediaStoreIngestionService,
        eventBus: EventBus,
    ) {
        val traceId = UUID.randomUUID()
        eventBus.publish(
            RuntimeEvent(
                traceId = traceId,
                sourceModule = RuntimeModule.STORAGE,
                eventType = StorageEvents.INDEXING_STARTED,
                priority = EventPriority.NORMAL,
                payload = mapOf("message" to "Indexing gallery photos and downloads…"),
            ),
        )
        val result = runCatching { ingestionService.syncAll(limit = 100) }
        eventBus.publish(
            RuntimeEvent(
                traceId = traceId,
                sourceModule = RuntimeModule.STORAGE,
                eventType = StorageEvents.INDEXING_COMPLETED,
                priority = EventPriority.NORMAL,
                payload = result.fold(
                    onSuccess = { ingestion ->
                        mapOf(
                            "message" to "Indexed ${ingestion.photosIngested} photos, ${ingestion.documentsIngested} documents",
                            "photosIngested" to ingestion.photosIngested.toString(),
                            "documentsIngested" to ingestion.documentsIngested.toString(),
                        )
                    },
                    onFailure = { error ->
                        mapOf("message" to "Indexing failed: ${error.message ?: "unknown error"}")
                    },
                ),
            ),
        )
    }
}
