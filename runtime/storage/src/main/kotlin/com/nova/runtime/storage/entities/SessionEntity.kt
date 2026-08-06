package com.nova.runtime.storage.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/** DSS §4.6 — Conversation session metadata. */
@Entity(
    tableName = "sessions",
    indices = [Index("traceId"), Index("startedAt")],
)
data class SessionEntity(
    @PrimaryKey val sessionId: UUID,
    val traceId: UUID,
    val startedAt: Long,
    val endedAt: Long?,
    val state: String,
)
