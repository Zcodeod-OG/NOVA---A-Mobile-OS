package com.nova.runtime.storage.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/** DSS §4.5 — Persistent user preferences. */
@Entity(tableName = "preferences")
data class PreferenceEntity(
    @PrimaryKey val key: String,
    val value: String,
    val confidence: Float,
    val updatedAt: Long,
)
