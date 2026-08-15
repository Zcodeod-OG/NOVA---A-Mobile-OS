package com.nova.runtime.orchestrator.calendar

import com.nova.runtime.models.Nir

/** App-provided calendar enrichment for message-driven scheduling intents. */
interface CalendarIntentSupport {
    suspend fun enrichScheduleNir(nir: Nir): Nir

    suspend fun buildImportantSummary(maxItems: Int = 5): String

    fun buildConfirmationPrompt(nir: Nir): String
}
