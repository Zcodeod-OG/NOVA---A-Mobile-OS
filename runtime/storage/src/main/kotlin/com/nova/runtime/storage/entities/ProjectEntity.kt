package com.nova.runtime.storage.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/** DSS §4.4 — Logical project grouping. */
@Entity(
    tableName = "projects",
    indices = [Index("updatedAt")],
)
data class ProjectEntity(
    @PrimaryKey val id: UUID,
    val title: String,
    val description: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val status: String,
)
