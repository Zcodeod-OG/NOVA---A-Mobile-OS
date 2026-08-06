package com.nova.runtime.storage.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/** DSS §4.2 — Photo metadata. */
@Entity(
    tableName = "photos",
    foreignKeys = [
        ForeignKey(
            entity = EmbeddingEntity::class,
            parentColumns = ["embeddingId"],
            childColumns = ["embeddingId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index("takenAt"),
        Index("uri"),
    ],
)
data class PhotoEntity(
    @PrimaryKey val id: UUID,
    val uri: String,
    val takenAt: Long,
    val width: Int?,
    val height: Int?,
    val latitude: Double?,
    val longitude: Double?,
    val ocrText: String?,
    val embeddingId: UUID?,
    val favorite: Boolean,
)
