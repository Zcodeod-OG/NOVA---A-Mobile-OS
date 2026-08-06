package com.nova.runtime.android.alarmAdapter

import com.nova.runtime.models.contracts.CapabilityResult
import java.util.UUID

/** AIS §4.6 — AlarmManager scheduling abstraction. */
interface AlarmAdapter {
    suspend fun execute(
        operation: String,
        parameters: Map<String, String>,
        traceId: UUID,
    ): CapabilityResult

    fun supportedOperations(): Set<String>

    fun requiredPermissions(): Set<String> = emptySet()
}

object AlarmOperations {
    const val CREATE = "createAlarm"
    const val CANCEL = "cancelAlarm"
    const val UPDATE = "updateAlarm"
}
