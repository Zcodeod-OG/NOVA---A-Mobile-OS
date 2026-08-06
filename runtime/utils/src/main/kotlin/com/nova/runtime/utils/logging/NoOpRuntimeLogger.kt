package com.nova.runtime.utils.logging

import com.nova.runtime.models.RuntimeError
import java.util.UUID

/** No-op logger for tests and disabled logging. */
class NoOpRuntimeLogger : NovaLogger {
    override fun trace(module: String, message: String, traceId: UUID?, metadata: Map<String, String>) = Unit
    override fun debug(module: String, message: String, traceId: UUID?, metadata: Map<String, String>) = Unit
    override fun info(module: String, message: String, traceId: UUID?, durationMs: Long?, metadata: Map<String, String>) = Unit
    override fun warn(module: String, message: String, traceId: UUID?, throwable: Throwable?, metadata: Map<String, String>) = Unit
    override fun error(module: String, message: String, traceId: UUID?, throwable: Throwable?, metadata: Map<String, String>) = Unit
    override fun entries(): List<LogEntry> = emptyList()

    /** Legacy MSP §16 operation logging hooks. */
    fun start(operation: String, traceId: UUID) = Unit
    fun finish(operation: String, traceId: UUID, durationMs: Long) = Unit
    fun error(operation: String, traceId: UUID, error: RuntimeError) = Unit
}
