package com.nova.runtime.app

import android.app.Application
import android.util.Log
import com.nova.runtime.ai.model.EmbeddingGenerator
import com.nova.runtime.ai.model.LocalLlmEngine
import com.nova.runtime.ai.model.ModelDownloadManager
import com.nova.runtime.ai.model.ModelDownloadPhase
import com.nova.runtime.ai.native.indexing.VectorIndexHydrator
import com.nova.runtime.ai.native.llm.MediaPipeLocalLlmEngine
import com.nova.runtime.ai.native.onnx.OnnxEmbeddingGenerator
import com.nova.runtime.ai.native.search.di.SearchCapabilityRegistrar
import com.nova.runtime.android.di.androidCapabilityModule
import com.nova.runtime.app.di.aiIntegrationModule
import com.nova.runtime.app.di.personalOsModule
import com.nova.runtime.app.di.appUiModule
import com.nova.runtime.app.di.executionPersistenceModule
import com.nova.runtime.app.di.runtimeModule
import com.nova.runtime.app.indexing.MediaIndexingScheduler
import com.nova.runtime.app.indexing.MessageIndexingScheduler
import com.nova.runtime.android.notificationAdapter.MessageIngestionBridge
import com.nova.runtime.android.notificationAdapter.WhatsAppMessageIngestionService
import com.nova.runtime.capability.registry.CapabilityRegistry
import com.nova.runtime.kernel.RuntimeKernel
import com.nova.runtime.storage.di.storageModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.core.Koin

class NovaApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@NovaApplication)
            allowOverride(true)
            modules(
                runtimeModule,
                // Must be top-level (not nested via includes) so production providers
                // replace the stub CapabilityRegistry from capabilityModule.
                androidCapabilityModule,
                storageModule(this@NovaApplication),
                executionPersistenceModule,
                aiIntegrationModule,
                personalOsModule,
                appUiModule,
            )
        }
        val koin = GlobalContext.get()
        // Register search providers synchronously so nova_command / cold-start pipelines
        // can resolve search.documents before the async bootstrap finishes.
        koin.get<SearchCapabilityRegistrar>()
        applicationScope.launch {
            runCatching {
                koin.get<VectorIndexHydrator>().hydrateFromDatabase()
            }.onFailure { error ->
                Log.w(TAG, "Vector index hydration failed: ${error.message}")
            }

            val registry = koin.get<CapabilityRegistry>()
            val providerIds = registry.all().map { it.metadata.name }
            Log.i(TAG, "Capability providers: $providerIds")
            check(providerIds.any { it.startsWith("android-") }) {
                "Android capability providers missing — DI override failed. Got: $providerIds"
            }
            check(providerIds.any { it.startsWith("search-") }) {
                "Search capability providers missing — SearchCapabilityRegistrar failed. Got: $providerIds"
            }
            koin.get<RuntimeKernel>().bootstrap()
            MessageIngestionBridge.install { messages ->
                koin.get<WhatsAppMessageIngestionService>().ingest(messages)
                MessageIndexingScheduler.enqueueFollowUp(this@NovaApplication)
            }
            preWarmModelsWhenReady(koin)
            MediaIndexingScheduler.startFullIndexing(this@NovaApplication)
            MessageIndexingScheduler.start(this@NovaApplication)
        }
    }

    private suspend fun preWarmModelsWhenReady(koin: Koin) {
        val downloadManager = koin.get<ModelDownloadManager>()
        withTimeoutOrNull(MODEL_PREWARM_WAIT_MS) {
            downloadManager.state.first { session ->
                session.phase == ModelDownloadPhase.COMPLETE ||
                    session.readiness.canRunHeavyInference ||
                    session.phase == ModelDownloadPhase.FAILED ||
                    session.phase == ModelDownloadPhase.OFFLINE
            }
        }
        runCatching {
            val embeddingGenerator = koin.get<EmbeddingGenerator>()
            if (embeddingGenerator is OnnxEmbeddingGenerator) {
                embeddingGenerator.preWarm()
            }
            val localLlm = koin.get<LocalLlmEngine>()
            if (localLlm is MediaPipeLocalLlmEngine) {
                localLlm.preWarm()
            }
        }.onFailure { error ->
            Log.w(TAG, "Model pre-warm failed: ${error.message}")
        }
    }

    private companion object {
        const val TAG = "NOVA/DI"
        const val MODEL_PREWARM_WAIT_MS = 120_000L
    }
}
