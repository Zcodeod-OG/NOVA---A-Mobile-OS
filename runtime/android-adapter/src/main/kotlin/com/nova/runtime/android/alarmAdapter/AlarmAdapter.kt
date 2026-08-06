package com.nova.runtime.android.alarmAdapter

import com.nova.runtime.models.contracts.CapabilityResult

/** AIS §4 — AlarmAdapter interface stub */
interface AlarmAdapter {
    suspend fun execute(operation: String, parameters: Map<String, String>): CapabilityResult
}
