package com.nova.runtime.storage.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/** Ingested Gmail / WhatsApp message metadata for importance scoring and scheduling. */
@Entity(
    tableName = "messages",
    indices = [
        Index("channel"),
        Index("threadKey"),
        Index("receivedAt"),
        Index(value = ["externalId"], unique = true),
        Index("importanceScore"),
    ],
)
data class MessageEntity(
    @PrimaryKey val id: UUID,
    val channel: String,
    val sender: String,
    val body: String,
    val threadKey: String?,
    val receivedAt: Long,
    val externalId: String,
    val subject: String? = null,
    val indexedAt: Long? = null,
    val importanceScore: Float? = null,
)
