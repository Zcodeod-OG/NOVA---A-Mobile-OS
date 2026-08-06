package com.nova.runtime.app.di

import com.nova.runtime.ai.native.di.aiNativeModule
import com.nova.runtime.ai.native.search.di.searchModule
import com.nova.runtime.android.ocrAdapter.OcrAdapter
import com.nova.runtime.android.ocrAdapter.OcrAdapterImpl
import org.koin.dsl.module

/** Wires on-device AI services into Android adapters and overrides inference stubs. */
val aiIntegrationModule = module {
    includes(aiNativeModule, searchModule)

    single<OcrAdapter>(override = true) {
        OcrAdapterImpl(get())
    }
}
