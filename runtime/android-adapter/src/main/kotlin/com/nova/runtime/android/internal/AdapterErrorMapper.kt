package com.nova.runtime.android.internal

import com.nova.runtime.models.ErrorCategory
import com.nova.runtime.models.ErrorSeverity
import com.nova.runtime.models.RuntimeError

/** AIS §11 — translate platform failures into structured runtime errors. */
internal object AdapterErrorMapper {
    fun permissionDenied(
        permission: String,
        diagnostics: Map<String, String> = emptyMap(),
    ): RuntimeError =
        RuntimeError(
            code = "ANDROID_PERMISSION_DENIED",
            category = ErrorCategory.PERMISSION,
            severity = ErrorSeverity.MEDIUM,
            recoverable = true,
            userVisibleMessage = "Permission required: $permission",
            diagnostics = diagnostics + mapOf("permission" to permission),
        )

    fun appUnavailable(
        target: String,
        cause: Throwable? = null,
    ): RuntimeError =
        RuntimeError(
            code = "ANDROID_APP_UNAVAILABLE",
            category = ErrorCategory.EXECUTION,
            severity = ErrorSeverity.MEDIUM,
            recoverable = true,
            userVisibleMessage = "Target is unavailable: $target",
            diagnostics = diagnosticsFrom(cause) + mapOf("target" to target),
        )

    fun intentFailed(
        operation: String,
        cause: Throwable? = null,
    ): RuntimeError =
        RuntimeError(
            code = "ANDROID_INTENT_FAILED",
            category = ErrorCategory.EXECUTION,
            severity = ErrorSeverity.MEDIUM,
            recoverable = true,
            userVisibleMessage = "Intent operation failed: $operation",
            diagnostics = diagnosticsFrom(cause) + mapOf("operation" to operation),
        )

    fun accessibilityTimeout(operation: String): RuntimeError =
        RuntimeError(
            code = "ANDROID_ACCESSIBILITY_TIMEOUT",
            category = ErrorCategory.EXECUTION,
            severity = ErrorSeverity.HIGH,
            recoverable = true,
            userVisibleMessage = "Accessibility operation timed out: $operation",
            diagnostics = mapOf("operation" to operation),
        )

    fun accessibilityUnavailable(): RuntimeError =
        RuntimeError(
            code = "ANDROID_ACCESSIBILITY_UNAVAILABLE",
            category = ErrorCategory.PERMISSION,
            severity = ErrorSeverity.HIGH,
            recoverable = true,
            userVisibleMessage = "Accessibility service is not enabled",
            diagnostics = emptyMap(),
        )

    fun contentProviderUnavailable(
        authority: String,
        cause: Throwable? = null,
    ): RuntimeError =
        RuntimeError(
            code = "ANDROID_CONTENT_PROVIDER_UNAVAILABLE",
            category = ErrorCategory.INFRASTRUCTURE,
            severity = ErrorSeverity.HIGH,
            recoverable = true,
            userVisibleMessage = "Content provider unavailable: $authority",
            diagnostics = diagnosticsFrom(cause) + mapOf("authority" to authority),
        )

    fun invalidOperation(
        adapter: String,
        operation: String,
        supported: Set<String>,
    ): RuntimeError =
        RuntimeError(
            code = "ANDROID_INVALID_OPERATION",
            category = ErrorCategory.VALIDATION,
            severity = ErrorSeverity.LOW,
            recoverable = false,
            userVisibleMessage = "Unsupported operation: $operation",
            diagnostics = mapOf(
                "adapter" to adapter,
                "operation" to operation,
                "supported" to supported.joinToString(","),
            ),
        )

    fun invalidParameters(
        adapter: String,
        operation: String,
        detail: String,
    ): RuntimeError =
        RuntimeError(
            code = "ANDROID_INVALID_PARAMETERS",
            category = ErrorCategory.VALIDATION,
            severity = ErrorSeverity.LOW,
            recoverable = false,
            userVisibleMessage = "Invalid parameters for $operation",
            diagnostics = mapOf(
                "adapter" to adapter,
                "operation" to operation,
                "detail" to detail,
            ),
        )

    fun platformFailure(
        adapter: String,
        operation: String,
        cause: Throwable?,
    ): RuntimeError =
        RuntimeError(
            code = "ANDROID_PLATFORM_FAILURE",
            category = ErrorCategory.INFRASTRUCTURE,
            severity = ErrorSeverity.HIGH,
            recoverable = false,
            userVisibleMessage = "Android platform failure during $operation",
            diagnostics = diagnosticsFrom(cause) + mapOf(
                "adapter" to adapter,
                "operation" to operation,
            ),
        )

    private fun diagnosticsFrom(cause: Throwable?): Map<String, String> {
        if (cause == null) return emptyMap()
        return mapOf(
            "exception" to cause.javaClass.simpleName,
            "exceptionMessage" to (cause.message ?: ""),
        )
    }
}
