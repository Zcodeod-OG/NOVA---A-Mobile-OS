package com.nova.runtime.android.intentAdapter

import com.nova.runtime.models.contracts.CapabilityResult

/** AIS §4 — IntentAdapter interface stub */
interface IntentAdapter {
    suspend fun execute(operation: String, parameters: Map<String, String>): CapabilityResult
}
