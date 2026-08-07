package com.nova.runtime.capability.di

import com.nova.runtime.capability.CapabilityFramework
import com.nova.runtime.capability.CapabilityFrameworkImpl
import com.nova.runtime.capability.events.CapabilityEventPublisher
import com.nova.runtime.capability.health.CapabilityHealthMonitor
import com.nova.runtime.capability.health.DefaultCapabilityHealthMonitor
import com.nova.runtime.capability.lifecycle.CapabilityLifecycleManager
import com.nova.runtime.capability.lifecycle.DefaultCapabilityLifecycleManager
import com.nova.runtime.capability.provider.defaultStubProviders
import com.nova.runtime.capability.registry.CapabilityRegistry
import com.nova.runtime.capability.registry.DefaultCapabilityRegistry
import com.nova.runtime.capability.resolver.CapabilityProviderResolver
import com.nova.runtime.capability.resolver.DefaultCapabilityProviderResolver
import com.nova.runtime.capability.transaction.CapabilityTransactionManager
import com.nova.runtime.capability.transaction.DefaultCapabilityTransactionManager
import org.koin.dsl.module

/** Koin DI wiring for Capability Framework per MSP §11. */
val capabilityModule = module {
    single<CapabilityRegistry> {
        DefaultCapabilityRegistry(initialProviders = defaultStubProviders())
    }
    single<CapabilityLifecycleManager> { DefaultCapabilityLifecycleManager(get()) }
    single<CapabilityProviderResolver> {
        DefaultCapabilityProviderResolver(
            registry = get(),
            lifecycleManager = get(),
        )
    }
    single<CapabilityHealthMonitor> {
        DefaultCapabilityHealthMonitor(
            registry = get(),
            lifecycleManager = get(),
        )
    }
    single<CapabilityTransactionManager> { DefaultCapabilityTransactionManager() }
    single { CapabilityEventPublisher(get()) }
    single<CapabilityFramework> {
        CapabilityFrameworkImpl(
            registry = get(),
            resolver = get(),
            lifecycleManager = get(),
            healthMonitor = get(),
            transactionManager = get(),
            eventPublisher = get(),
            logger = get(),
        )
    }
}
