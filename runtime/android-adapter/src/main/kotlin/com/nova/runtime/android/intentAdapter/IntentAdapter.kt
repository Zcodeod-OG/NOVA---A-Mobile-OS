package com.nova.runtime.android.intentAdapter

import com.nova.runtime.models.contracts.CapabilityResult
import java.util.UUID

/** AIS §4.2 — wraps Android Intent APIs. */
interface IntentAdapter {
    suspend fun execute(
        operation: String,
        parameters: Map<String, String>,
        traceId: UUID,
    ): CapabilityResult

    fun supportedOperations(): Set<String>

    fun requiredPermissions(): Set<String> = emptySet()
}

object IntentOperations {
    const val OPEN_APP = "openApp"
    const val SHARE = "share"
    const val VIEW_DOCUMENT = "viewDocument"
    const val DIAL = "dial"
    const val LAUNCH_SETTINGS = "launchSettings"
    const val OPEN_URL = "openUrl"
}
