package com.nova.runtime.app.ui.models

data class LiveTelemetryState(
    val memoryUsagePercent: Int = 14,
    val npuTeraOpsCurrent: Float = 1.82f,
    val npuTeraOpsPeak: Float = 1.90f,
    val npuTempCelsius: Int = 41,
    val indexedVectorCount: Int = 12400,
    val totalDocumentsIndexed: Int = 42,
    val ragLatencyMs: Int = 14,
    val ragThroughputDocsPerSec: Int = 420,
    val nodeLatencyMs: Float = 1.4f,
    val sessionUptimeSeconds: Long = 0L,
    val totalTasksExecuted: Int = 12,
    val activeQueueProgress: Float = 0.92f,
) {
    val formattedUptime: String
        get() {
            val hours = sessionUptimeSeconds / 3600
            val minutes = (sessionUptimeSeconds % 3600) / 60
            val seconds = sessionUptimeSeconds % 60
            return String.format("%02d:%02d:%02d", hours, minutes, seconds)
        }
}
