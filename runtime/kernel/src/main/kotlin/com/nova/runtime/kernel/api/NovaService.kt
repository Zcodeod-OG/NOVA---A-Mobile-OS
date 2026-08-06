package com.nova.runtime.kernel.api

import com.nova.runtime.models.RuntimeModule

/**
 * Base contract for all runtime services registered with the kernel.
 */
interface NovaService {
    val module: RuntimeModule
    val serviceName: String get() = module.name
}
