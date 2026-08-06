package com.nova.runtime.storage.converters

import androidx.room.TypeConverter
import java.util.UUID

/** DSS §6 — Room type converters stub. */
class UuidConverter {
    @TypeConverter
    fun fromUuid(value: UUID?): String? = value?.toString()

    @TypeConverter
    fun toUuid(value: String?): UUID? = value?.let(UUID::fromString)
}
