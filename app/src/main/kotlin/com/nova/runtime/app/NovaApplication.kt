package com.nova.runtime.app

import android.app.Application
import com.nova.runtime.ai.native.search.di.SearchCapabilityRegistrar
import com.nova.runtime.app.di.aiIntegrationModule
import com.nova.runtime.app.di.appUiModule
import com.nova.runtime.app.di.executionPersistenceModule
import com.nova.runtime.app.di.runtimeModule
import com.nova.runtime.app.indexing.MediaIndexingScheduler
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
            MediaIndexingScheduler.startFullIndexing(this@NovaApplication)
        }
    }
}
