package com.nova.runtime.android.internal

internal class AccessibilityOperationTimeoutException(
    val operation: String,
) : RuntimeException("Accessibility timeout: $operation")
