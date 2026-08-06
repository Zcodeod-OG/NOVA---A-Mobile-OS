package com.nova.runtime.kernel

import com.nova.runtime.events.InMemoryEventBus
import com.nova.runtime.kernel.api.RuntimeModuleDescriptor
import com.nova.runtime.kernel.config.ConfigurationManager
import com.nova.runtime.kernel.config.InMemoryConfigurationManager
import com.nova.runtime.kernel.config.KernelConfigKeys
import com.nova.runtime.kernel.lifecycle.DefaultLifecycleManager
import com.nova.runtime.kernel.lifecycle.LifecycleManager
import com.nova.runtime.kernel.module.DefaultModuleRegistrationContext
import com.nova.runtime.kernel.module.DefaultModuleRegistry
import com.nova.runtime.kernel.module.ModuleRegistry
import com.nova.runtime.kernel.registry.DefaultServiceRegistry
import com.nova.runtime.kernel.registry.ServiceRegistry
import com.nova.runtime.kernel.trace.DefaultTraceIdGenerator
import com.nova.runtime.kernel.trace.TraceContextHolder
import com.nova.runtime.kernel.trace.TraceIdGenerator
import com.nova.runtime.utils.logging.LogLevel
import com.nova.runtime.utils.logging.NovaLogger
import com.nova.runtime.utils.logging.StructuredLogger
import java.util.UUID

/**
 * Facade for runtime kernel bootstrap and shutdown.
 */
class RuntimeKernel(
    val serviceRegistry: ServiceRegistry,
    val eventBus: InMemoryEventBus,
    val lifecycleManager: LifecycleManager,
    val configurationManager: ConfigurationManager,
    val moduleRegistry: ModuleRegistry,
    val traceContextHolder: TraceContextHolder,
    val logger: NovaLogger,
) {
    suspend fun bootstrap(
        modules: List<RuntimeModuleDescriptor> = emptyList(),
        configuration: Map<String, String> = defaultConfiguration(),
        traceId: UUID = UUID.randomUUID(),
    ) {
        configuration.forEach { (key, value) -> configurationManager.set(key, value) }
        modules.forEach { moduleRegistry.register(it) }

        val context = DefaultModuleRegistrationContext(serviceRegistry, eventBus, logger)
        moduleRegistry.installAll(context)
        lifecycleManager.initialize(traceId)
    }

    suspend fun start(traceId: UUID = UUID.randomUUID()) {
        lifecycleManager.start(traceId)
    }

    suspend fun shutdown(traceId: UUID = UUID.randomUUID()) {
        lifecycleManager.stop(traceId)
        eventBus.shutdown()
    }

    companion object {
        fun defaultConfiguration(): Map<String, String> = mapOf(
            KernelConfigKeys.RUNTIME_VERSION to "0.1.0",
            KernelConfigKeys.LOG_LEVEL to LogLevel.DEBUG.name,
            KernelConfigKeys.EVENT_BUS_ASYNC to "true",
        )

        fun create(
            configuration: Map<String, String> = defaultConfiguration(),
            logLevel: LogLevel = LogLevel.DEBUG,
        ): RuntimeKernel {
            val logger = StructuredLogger(minLevel = logLevel)
            val serviceRegistry = DefaultServiceRegistry()
            val eventBus = InMemoryEventBus(logger)
            val configurationManager = InMemoryConfigurationManager(configuration)
            val moduleRegistry = DefaultModuleRegistry()
            val traceContextHolder = TraceContextHolder(DefaultTraceIdGenerator())
            val lifecycleManager = DefaultLifecycleManager(serviceRegistry, eventBus, logger)

            return RuntimeKernel(
                serviceRegistry = serviceRegistry,
                eventBus = eventBus,
                lifecycleManager = lifecycleManager,
                configurationManager = configurationManager,
                moduleRegistry = moduleRegistry,
                traceContextHolder = traceContextHolder,
                logger = logger,
            )
        }
    }
}

/**
 * Kernel infrastructure service marker for registry lookups.
 */
interface KernelService : com.nova.runtime.kernel.api.NovaService {
    override val module: com.nova.runtime.models.RuntimeModule get() = com.nova.runtime.models.RuntimeModule.KERNEL
}
