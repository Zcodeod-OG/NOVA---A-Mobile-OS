package com.nova.runtime.error

/**
 * Internal exception wrapper carrying a structured [NovaError].
 * Used within kernel boundaries; translated to [NovaError] at module edges.
 */
class NovaException(
    val error: NovaError,
    cause: Throwable? = null,
) : Exception(error.userVisibleMessage, cause)

object NovaErrors {
    fun serviceNotFound(serviceType: String): NovaError = NovaError(
        code = "REGISTRY_SERVICE_NOT_FOUND",
        category = ErrorCategory.REGISTRY,
        severity = ErrorSeverity.ERROR,
        recoverability = Recoverability.FATAL,
        userVisibleMessage = "Required service is not registered: $serviceType",
        diagnostics = "serviceType=$serviceType",
    )

    fun duplicateService(serviceType: String): NovaError = NovaError(
        code = "REGISTRY_DUPLICATE_SERVICE",
        category = ErrorCategory.REGISTRY,
        severity = ErrorSeverity.ERROR,
        recoverability = Recoverability.FATAL,
        userVisibleMessage = "Service already registered: $serviceType",
        diagnostics = "serviceType=$serviceType",
    )

    fun invalidLifecycleTransition(from: String, to: String): NovaError = NovaError(
        code = "LIFECYCLE_INVALID_TRANSITION",
        category = ErrorCategory.LIFECYCLE,
        severity = ErrorSeverity.ERROR,
        recoverability = Recoverability.FATAL,
        userVisibleMessage = "Invalid lifecycle transition from $from to $to",
    )

    fun configurationMissing(key: String): NovaError = NovaError(
        code = "CONFIG_MISSING_KEY",
        category = ErrorCategory.CONFIGURATION,
        severity = ErrorSeverity.ERROR,
        recoverability = Recoverability.RECOVERABLE,
        userVisibleMessage = "Required configuration key is missing: $key",
        diagnostics = "key=$key",
    )

    fun eventHandlerFailed(eventType: String, subscriber: String): NovaError = NovaError(
        code = "EVENT_HANDLER_FAILED",
        category = ErrorCategory.EVENT_BUS,
        severity = ErrorSeverity.WARNING,
        recoverability = Recoverability.RETRYABLE,
        userVisibleMessage = "Event handler failed for $eventType",
        diagnostics = "eventType=$eventType, subscriber=$subscriber",
    )

    fun internal(message: String, diagnostics: String? = null): NovaError = NovaError(
        code = "INTERNAL_ERROR",
        category = ErrorCategory.INTERNAL,
        severity = ErrorSeverity.CRITICAL,
        recoverability = Recoverability.FATAL,
        userVisibleMessage = message,
        diagnostics = diagnostics,
    )
}
