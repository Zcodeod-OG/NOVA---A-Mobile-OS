package com.nova.runtime.app.ui.models

import java.util.UUID

/** Command log entry item for the COMMAND screen log feed. */
data class CommandLogEntry(
    val id: String = UUID.randomUUID().toString(),
    val indexLabel: String,
    val timestamp: String,
    val latencyMs: Long,
    val status: String, // "RESOLVED", "ERROR", "PROCESSING"
    val commandText: String,
    val responseText: String,
    val tags: List<String> = emptyList(),
)

/** 2D Vector Point projection for the RAG INDEX Vector Space Map. */
data class VectorPoint2D(
    val x: Float, // 0.0f..1.0f
    val y: Float, // 0.0f..1.0f
    val label: String? = null,
    val isPrimary: Boolean = false,
)

/** Automation Task item for the ACTION screen WorkManager queue. */
data class AutomationTaskItem(
    val id: String,
    val indexLabel: String,
    val title: String,
    val status: String, // "RUNNING", "PENDING", "DONE", "FAILED"
    val progressPercent: Int, // 0..100
)

/** Runtime Model info spec for the CONFIG screen. */
data class RuntimeModelSpec(
    val indexLabel: String,
    val name: String,
    val version: String,
    val status: String, // "LOADED", "AVAILABLE", "DOWNLOADING", "ERROR"
    val iconName: String,
)
