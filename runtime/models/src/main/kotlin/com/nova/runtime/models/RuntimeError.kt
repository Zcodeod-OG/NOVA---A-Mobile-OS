package com.nova.runtime.models

enum class ErrorCategory {
    VALIDATION,
    PERMISSION,
    EXECUTION,
    INFRASTRUCTURE,
    UNKNOWN,
}

enum class ErrorSeverity {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL,
}

data class RuntimeError(
    val code: String,
    val category: ErrorCategory,
    val severity: ErrorSeverity,
    val recoverable: Boolean,
    val userVisibleMessage: String,
    val diagnostics: Map<String, String> = emptyMap(),
)
