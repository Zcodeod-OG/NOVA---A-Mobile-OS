package com.nova.runtime.execution.model

/** Configurable execution runtime settings. */
data class ExecutionConfig(
    val workerPoolSize: Int = 4,
    val defaultMaxRetries: Int = 3,
    val defaultBackoffMs: Long = 100L,
)
