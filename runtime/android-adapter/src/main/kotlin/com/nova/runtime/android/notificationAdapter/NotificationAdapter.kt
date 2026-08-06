package com.nova.runtime.android.notificationAdapter

import com.nova.runtime.models.contracts.CapabilityResult

/** AIS §4 — NotificationAdapter interface stub */
interface NotificationAdapter {
    suspend fun execute(operation: String, parameters: Map<String, String>): CapabilityResult
}
