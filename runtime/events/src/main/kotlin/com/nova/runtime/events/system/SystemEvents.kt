package com.nova.runtime.events.system

import com.nova.runtime.models.EventPriority
import com.nova.runtime.models.RuntimeModule
import java.util.UUID

/**
 * Well-known system event type constants per EMS §7.
 */
object SystemEvents {
    const val RUNTIME_STARTED = "RuntimeStarted"
    const val RUNTIME_READY = "RuntimeReady"
    const val RUNTIME_STOPPING = "RuntimeStopping"
    const val RUNTIME_SHUTDOWN = "RuntimeShutdown"
    const val RUNTIME_ERROR = "RuntimeError"
    const val SHUTDOWN_REQUESTED = "ShutdownRequested"

    val ALL: Set<String> = setOf(
        RUNTIME_STARTED,
        RUNTIME_READY,
        RUNTIME_STOPPING,
        RUNTIME_SHUTDOWN,
        RUNTIME_ERROR,
        SHUTDOWN_REQUESTED,
    )
}

data class RuntimeErrorPayload(
    val code: String,
    val message: String,
)

fun runtimeErrorEvent(
    traceId: UUID,
    code: String,
    message: String,
    correlationId: UUID? = null,
) = com.nova.runtime.events.RuntimeEvent(
    traceId = traceId,
    correlationId = correlationId,
    sourceModule = RuntimeModule.KERNEL,
    eventType = SystemEvents.RUNTIME_ERROR,
    priority = EventPriority.CRITICAL,
    payload = RuntimeErrorPayload(code, message),
)
