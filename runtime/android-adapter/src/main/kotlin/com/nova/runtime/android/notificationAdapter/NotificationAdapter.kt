package com.nova.runtime.android.notificationAdapter

import com.nova.runtime.models.contracts.CapabilityResult
import java.util.UUID

/** AIS §4.7 — notification observe/create abstraction. */
interface NotificationAdapter {
    suspend fun execute(
        operation: String,
        parameters: Map<String, String>,
        traceId: UUID,
    ): CapabilityResult

    fun supportedOperations(): Set<String>

    fun requiredPermissions(): Set<String>
}

object NotificationOperations {
    const val PUBLISH = "publish"
    const val DISMISS = "dismiss"
    const val READ = "read"
}
