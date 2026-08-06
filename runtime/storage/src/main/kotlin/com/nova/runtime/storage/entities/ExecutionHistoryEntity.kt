package com.nova.runtime.storage.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/** DSS §4.7 — Execution audit history. */
@Entity(
    tableName = "execution_history",
    indices = [Index("graphId"), Index("traceId")],
)
data class ExecutionHistoryEntity(
    @PrimaryKey val id: UUID,
    val graphId: UUID,
    val traceId: UUID,
    val status: String,
    val duration: Long,
    val retryCount: Int,
    val completedNodes: Int,
    val failedNodes: Int,
)
