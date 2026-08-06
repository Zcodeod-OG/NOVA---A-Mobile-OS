package com.nova.runtime.storage.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/** DSS §4.8 — Embedding metadata; vectors live in the Vector Index. */
@Entity(
    tableName = "embeddings",
    indices = [
        Index("objectId"),
        Index("objectType"),
    ],
)
data class EmbeddingEntity(
    @PrimaryKey val embeddingId: UUID,
    val objectType: String,
    val objectId: UUID,
    val modelVersion: String,
    val dimension: Int,
    val createdAt: Long,
)
