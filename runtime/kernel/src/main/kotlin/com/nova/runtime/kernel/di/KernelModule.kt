package com.nova.runtime.kernel.di

import com.nova.runtime.events.EventBus
import com.nova.runtime.events.InMemoryEventBus
import com.nova.runtime.kernel.config.ConfigurationManager
import com.nova.runtime.kernel.config.InMemoryConfigurationManager
import com.nova.runtime.kernel.config.KernelConfigKeys
import com.nova.runtime.kernel.lifecycle.DefaultLifecycleManager
import com.nova.runtime.kernel.lifecycle.LifecycleManager
import com.nova.runtime.utils.logging.LogLevel
import com.nova.runtime.utils.logging.NovaLogger
import com.nova.runtime.utils.logging.StructuredLogger
import com.nova.runtime.kernel.module.DefaultModuleRegistry
import com.nova.runtime.kernel.module.ModuleRegistry
import com.nova.runtime.kernel.registry.DefaultServiceRegistry
import com.nova.runtime.kernel.registry.ServiceRegistry
import com.nova.runtime.kernel.trace.DefaultTraceIdGenerator
import com.nova.runtime.kernel.trace.TraceContextHolder
import com.nova.runtime.kernel.trace.TraceIdGenerator
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * Koin DI modules for kernel infrastructure per TDD §4.
 */
val loggingModule = module {
    single<NovaLogger> {
        val config = get<ConfigurationManager>()
        val levelName = config.get(KernelConfigKeys.LOG_LEVEL) ?: LogLevel.DEBUG.name
        val level = runCatching { LogLevel.valueOf(levelName) }.getOrDefault(LogLevel.DEBUG)
        StructuredLogger(minLevel = level)
    }
}

val traceModule = module {
    single<TraceIdGenerator> { DefaultTraceIdGenerator() }
    single { TraceContextHolder(get()) }
}

val configurationModule = module {
    single<ConfigurationManager> {
        InMemoryConfigurationManager(
            mapOf(
                KernelConfigKeys.RUNTIME_VERSION to "0.1.0",
                KernelConfigKeys.LOG_LEVEL to LogLevel.DEBUG.name,
                KernelConfigKeys.EVENT_BUS_ASYNC to "true",
            ),
        )
    }
}

val eventBusModule = module {
    single { InMemoryEventBus(get()) } bind EventBus::class
}

val registryModule = module {
    singleOf(::DefaultServiceRegistry) bind ServiceRegistry::class
    singleOf(::DefaultModuleRegistry) bind ModuleRegistry::class
}

val lifecycleModule = module {
    single<LifecycleManager> {
        DefaultLifecycleManager(get(), get(), get())
    }
}

val kernelModule = module {
    includes(
        configurationModule,
        loggingModule,
        traceModule,
        eventBusModule,
        registryModule,
        lifecycleModule,
    )
}
