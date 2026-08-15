package com.nova.runtime.android.calendarAdapter

import com.nova.runtime.models.contracts.CapabilityResult
import java.util.UUID

/** AIS §4.5 — calendar event read/write abstraction. */
interface CalendarAdapter {
    suspend fun execute(
        operation: String,
        parameters: Map<String, String>,
        traceId: UUID,
    ): CapabilityResult

    fun supportedOperations(): Set<String>

    fun requiredPermissions(): Set<String>
}

object CalendarOperations {
    const val READ_EVENTS = "readEvents"
    const val CREATE_EVENT = "createEvent"
    const val MODIFY_EVENT = "modifyEvent"
    const val DELETE_EVENT = "deleteEvent"
}
