package com.nova.runtime.orchestrator.calendar

import com.nova.runtime.models.Nir

/** No-op calendar support for JVM tests and headless pipelines. */
object NoOpCalendarIntentSupport : CalendarIntentSupport {
    override suspend fun enrichScheduleNir(nir: Nir) = nir

    override suspend fun buildImportantSummary(maxItems: Int): String =
        "No messages ingested yet today."

    override fun buildConfirmationPrompt(nir: Nir): String {
        val title = nir.constraints["title"] ?: "Calendar event"
        return "Add \"$title\" to your calendar? Reply \"confirm\" to create the event."
    }
}
