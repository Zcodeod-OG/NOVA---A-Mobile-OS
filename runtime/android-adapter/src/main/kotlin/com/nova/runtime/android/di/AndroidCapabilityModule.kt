package com.nova.runtime.android.di

import com.nova.runtime.android.capability.provider.productionCapabilityProviders
import com.nova.runtime.android.AndroidAdapterLayer
import com.nova.runtime.capability.provider.CapabilityProvider
import com.nova.runtime.capability.registry.CapabilityRegistry
import com.nova.runtime.capability.registry.DefaultCapabilityRegistry
import com.nova.runtime.utils.logging.NovaLogger
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/** Overrides capability registry with Android-backed production providers. */
val androidCapabilityModule = module {
    single<List<CapabilityProvider>> {
        productionCapabilityProviders(
            context = androidContext(),
            adapters = get<AndroidAdapterLayer>(),
            logger = get<NovaLogger>(),
        )
    }
    single<CapabilityRegistry> {
        DefaultCapabilityRegistry(initialProviders = get())
    }
}
