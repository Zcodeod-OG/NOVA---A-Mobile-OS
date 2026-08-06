package com.nova.runtime.app

import android.app.Application
import com.nova.runtime.app.di.runtimeModule
import com.nova.runtime.kernel.RuntimeKernel
import com.nova.runtime.storage.di.storageModule
import kotlinx.coroutines.runBlocking
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class NovaApplication : Application() {
    private val runtimeKernel: RuntimeKernel by inject()

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@NovaApplication)
            modules(runtimeModule, storageModule(this@NovaApplication))
        }
        runBlocking {
            runtimeKernel.bootstrap()
        }
    }
}
