package com.nova.runtime.storage.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/** DSS §4.1 — Documents table stub */
@Entity(tableName = "documents")
data class DocumentEntity(
    @PrimaryKey val id: UUID,
    val path: String,
    val name: String,
    val extension: String,
    val mimeType: String,
    val size: Long,
    val checksum: String,
    val createdAt: Long,
    val modifiedAt: Long,
    val indexedAt: Long?,
    val projectId: UUID?,
    val embeddingId: UUID?,
    val importance: Int,
)
