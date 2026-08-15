package com.nova.runtime.utils.logging

import com.nova.runtime.models.RuntimeModule
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

enum class LogLevel {
    TRACE,
    DEBUG,
    INFO,
    WARN,
    ERROR,
}

data class LogEntry(
    val timestamp: Instant,
    val level: LogLevel,
    val module: String,
    val message: String,
    val traceId: UUID? = null,
    val durationMs: Long? = null,
    val metadata: Map<String, String> = emptyMap(),
)

interface NovaLogger {
    fun trace(
        module: String,
        message: String,
        traceId: UUID? = null,
        metadata: Map<String, String> = emptyMap(),
    )

    fun debug(
        module: String,
        message: String,
        traceId: UUID? = null,
        metadata: Map<String, String> = emptyMap(),
    )

    fun info(
        module: String,
        message: String,
        traceId: UUID? = null,
        durationMs: Long? = null,
        metadata: Map<String, String> = emptyMap(),
    )

    fun warn(
        module: String,
        message: String,
        traceId: UUID? = null,
        throwable: Throwable? = null,
        metadata: Map<String, String> = emptyMap(),
    )

    fun error(
        module: String,
        message: String,
        traceId: UUID? = null,
        throwable: Throwable? = null,
        metadata: Map<String, String> = emptyMap(),
    )

    fun entries(): List<LogEntry>
}

/**
 * Structured logger per MSP §16. Consistent format across all modules.
 */
class StructuredLogger(
    private val minLevel: LogLevel = LogLevel.DEBUG,
    private val sink: (LogEntry) -> Unit = {},
) : NovaLogger {

    private val logArchive = CopyOnWriteArrayList<LogEntry>()

    override fun trace(module: String, message: String, traceId: UUID?, metadata: Map<String, String>) {
        log(LogLevel.TRACE, module, message, traceId, null, metadata)
    }

    override fun debug(module: String, message: String, traceId: UUID?, metadata: Map<String, String>) {
        log(LogLevel.DEBUG, module, message, traceId, null, metadata)
    }

    override fun info(
        module: String,
        message: String,
        traceId: UUID?,
        durationMs: Long?,
        metadata: Map<String, String>,
    ) {
        log(LogLevel.INFO, module, message, traceId, durationMs, metadata)
    }

    override fun warn(
        module: String,
        message: String,
        traceId: UUID?,
        throwable: Throwable?,
        metadata: Map<String, String>,
    ) {
        val enriched = enrichWithThrowable(metadata, throwable)
        log(LogLevel.WARN, module, message, traceId, null, enriched)
    }

    override fun error(
        module: String,
        message: String,
        traceId: UUID?,
        throwable: Throwable?,
        metadata: Map<String, String>,
    ) {
        val enriched = enrichWithThrowable(metadata, throwable)
        log(LogLevel.ERROR, module, message, traceId, null, enriched)
    }

    override fun entries(): List<LogEntry> = logArchive.toList()

    private fun log(
        level: LogLevel,
        module: String,
        message: String,
        traceId: UUID?,
        durationMs: Long?,
        metadata: Map<String, String>,
    ) {
        if (level.ordinal < minLevel.ordinal) return
        val entry = LogEntry(
            timestamp = Instant.now(),
            level = level,
            module = module,
            message = message,
            traceId = traceId,
            durationMs = durationMs,
            metadata = metadata,
        )
        logArchive.add(entry)
        sink(entry)
    }

    private fun enrichWithThrowable(metadata: Map<String, String>, throwable: Throwable?): Map<String, String> {
        if (throwable == null) return metadata
        return metadata + mapOf(
            "exception" to throwable.javaClass.simpleName,
            "exceptionMessage" to (throwable.message ?: ""),
        )
    }
}

fun NovaLogger.forModule(module: RuntimeModule): ModuleLogger = ModuleLogger(this, module.name)

class ModuleLogger(
    private val delegate: NovaLogger,
    private val module: String,
) {
    fun info(message: String, traceId: UUID? = null, durationMs: Long? = null) =
        delegate.info(module, message, traceId, durationMs)

    fun error(message: String, traceId: UUID? = null, throwable: Throwable? = null) =
        delegate.error(module, message, traceId, throwable)
}
