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
    /** L2-normalized embedding bytes; null for rows indexed before v6 migration. */
    val vectorBlob: ByteArray? = null,
    /** Mirrors vector-index metadata (summary / ocr / image). */
    val embeddingKind: String? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EmbeddingEntity) return false
        return embeddingId == other.embeddingId &&
            objectType == other.objectType &&
            objectId == other.objectId &&
            modelVersion == other.modelVersion &&
            dimension == other.dimension &&
            createdAt == other.createdAt &&
            vectorBlob.contentEquals(other.vectorBlob) &&
            embeddingKind == other.embeddingKind
    }

    override fun hashCode(): Int {
        var result = embeddingId.hashCode()
        result = 31 * result + objectType.hashCode()
        result = 31 * result + objectId.hashCode()
        result = 31 * result + modelVersion.hashCode()
        result = 31 * result + dimension
        result = 31 * result + createdAt.hashCode()
        result = 31 * result + (vectorBlob?.contentHashCode() ?: 0)
        result = 31 * result + (embeddingKind?.hashCode() ?: 0)
        return result
    }
}
