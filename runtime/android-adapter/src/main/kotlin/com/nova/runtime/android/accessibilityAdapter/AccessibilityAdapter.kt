package com.nova.runtime.android.accessibilityAdapter

import com.nova.runtime.models.contracts.CapabilityResult
import java.util.UUID

/** AIS §4.1 — accessibility interaction abstraction. */
interface AccessibilityAdapter {
    suspend fun execute(
        operation: String,
        parameters: Map<String, String>,
        traceId: UUID,
    ): CapabilityResult

    fun supportedOperations(): Set<String>

    fun requiredPermissions(): Set<String> = emptySet()
}

object AccessibilityOperations {
    const val CLICK = "click"
    const val INPUT_TEXT = "inputText"
    const val SCROLL = "scroll"
    const val TRAVERSE = "traverse"
    const val GET_ACTIVE_WINDOW = "getActiveWindow"
}
