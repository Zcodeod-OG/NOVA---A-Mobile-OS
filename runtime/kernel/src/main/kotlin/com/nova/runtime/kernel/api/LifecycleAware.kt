package com.nova.runtime.kernel.api

/**
 * Services that participate in runtime lifecycle transitions.
 */
interface LifecycleAware {
    suspend fun onStart()
    suspend fun onStop()
}
