package com.nova.runtime.storage.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/** DSS §4.1 — Indexed document metadata. */
@Entity(
    tableName = "documents",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = EmbeddingEntity::class,
            parentColumns = ["embeddingId"],
            childColumns = ["embeddingId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index("path"),
        Index("modifiedAt"),
        Index("checksum"),
        Index("projectId"),
        Index(value = ["projectId", "modifiedAt"]),
    ],
)
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
