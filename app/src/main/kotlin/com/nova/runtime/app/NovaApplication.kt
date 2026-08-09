package com.nova.runtime.app

import android.app.Application
import android.util.Log
import com.nova.runtime.ai.native.search.di.SearchCapabilityRegistrar
import com.nova.runtime.android.di.androidCapabilityModule
import com.nova.runtime.app.di.aiIntegrationModule
import com.nova.runtime.app.di.appUiModule
import com.nova.runtime.app.di.executionPersistenceModule
import com.nova.runtime.app.di.runtimeModule
import com.nova.runtime.app.indexing.MediaIndexingScheduler
import com.nova.runtime.capability.registry.CapabilityRegistry
import com.nova.runtime.kernel.RuntimeKernel
import com.nova.runtime.storage.di.storageModule
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
                // Must be top-level (not nested via includes) so production providers
                // replace the stub CapabilityRegistry from capabilityModule.
                androidCapabilityModule,
                storageModule(this@NovaApplication),
                executionPersistenceModule,
                aiIntegrationModule,
                appUiModule,
            )
        }
        val koin = GlobalContext.get()
        // Register search providers synchronously so nova_command / cold-start pipelines
        // can resolve search.documents before the async bootstrap finishes.
        koin.get<SearchCapabilityRegistrar>()
        applicationScope.launch {
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
            MediaIndexingScheduler.startFullIndexing(this@NovaApplication)
        }
    }

    private companion object {
        const val TAG = "NOVA/DI"
    }
}
