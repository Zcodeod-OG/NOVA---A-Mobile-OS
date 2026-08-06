package com.nova.runtime.kernel.registry

import com.nova.runtime.error.NovaException
import com.nova.runtime.error.NovaErrors
import com.nova.runtime.kernel.api.NovaService
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass

interface ServiceRegistry {
    fun <T : NovaService> register(serviceType: KClass<T>, instance: T)
    fun <T : NovaService> register(serviceType: Class<T>, instance: T) =
        register(serviceType.kotlin, instance)

    fun <T : NovaService> get(serviceType: KClass<out T>): T
    fun <T : NovaService> get(serviceType: Class<out T>): T = get(serviceType.kotlin)

    fun <T : NovaService> getOrNull(serviceType: KClass<out T>): T?
    fun contains(serviceType: KClass<out NovaService>): Boolean
    fun registeredTypes(): Set<KClass<out NovaService>>
    fun clear()
}

class DefaultServiceRegistry : ServiceRegistry {
    private val services = ConcurrentHashMap<KClass<out NovaService>, NovaService>()

    override fun <T : NovaService> register(serviceType: KClass<T>, instance: T) {
        val previous = services.putIfAbsent(serviceType, instance)
        if (previous != null) {
            throw NovaException(NovaErrors.duplicateService(serviceType.simpleName ?: "Unknown"))
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T : NovaService> get(serviceType: KClass<out T>): T {
        val service = services[serviceType]
            ?: throw NovaException(NovaErrors.serviceNotFound(serviceType.simpleName ?: "Unknown"))
        return service as T
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T : NovaService> getOrNull(serviceType: KClass<out T>): T? =
        services[serviceType] as? T

    override fun contains(serviceType: KClass<out NovaService>): Boolean =
        services.containsKey(serviceType)

    override fun registeredTypes(): Set<KClass<out NovaService>> = services.keys.toSet()

    override fun clear() {
        services.clear()
    }
}
