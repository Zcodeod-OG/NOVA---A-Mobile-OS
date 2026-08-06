package com.nova.runtime.error

/**
 * Structured error model per IAS §9. Raw exceptions must not cross module boundaries.
 */
data class NovaError(
    val code: String,
    val category: ErrorCategory,
    val severity: ErrorSeverity,
    val recoverability: Recoverability,
    val userVisibleMessage: String,
    val diagnostics: String? = null,
    val cause: NovaError? = null,
) {
    init {
        require(code.isNotBlank()) { "Error code must not be blank" }
        require(userVisibleMessage.isNotBlank()) { "User-visible message must not be blank" }
    }
}

enum class ErrorCategory {
    CONFIGURATION,
    LIFECYCLE,
    REGISTRY,
    EVENT_BUS,
    VALIDATION,
    INTERNAL,
}

enum class ErrorSeverity {
    INFO,
    WARNING,
    ERROR,
    CRITICAL,
}

enum class Recoverability {
    RECOVERABLE,
    RETRYABLE,
    FATAL,
}
