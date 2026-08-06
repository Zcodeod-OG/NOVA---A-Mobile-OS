package com.nova.runtime.android.calendarAdapter

import com.nova.runtime.models.contracts.CapabilityResult

/** AIS §4 — CalendarAdapter interface stub */
interface CalendarAdapter {
    suspend fun execute(operation: String, parameters: Map<String, String>): CapabilityResult
}
