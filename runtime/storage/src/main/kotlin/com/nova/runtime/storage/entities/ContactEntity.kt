package com.nova.runtime.storage.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/** DSS §4.3 — Contact metadata. */
@Entity(
    tableName = "contacts",
    indices = [
        Index("displayName"),
        Index("phone"),
        Index(value = ["displayName", "lastInteraction"]),
    ],
)
data class ContactEntity(
    @PrimaryKey val id: UUID,
    val displayName: String,
    val phone: String?,
    val email: String?,
    val lastInteraction: Long?,
    val importance: Int,
)
