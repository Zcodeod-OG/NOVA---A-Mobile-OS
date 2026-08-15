package com.nova.runtime.kernel.registry

import com.nova.runtime.kernel.api.NovaService
import kotlin.reflect.KClass

/**
 * Lifecycle hooks invoked when services are registered with the kernel.
 */
fun interface ServiceRegistrationListener {
    fun onRegistered(serviceType: KClass<out NovaService>, service: NovaService)
}
