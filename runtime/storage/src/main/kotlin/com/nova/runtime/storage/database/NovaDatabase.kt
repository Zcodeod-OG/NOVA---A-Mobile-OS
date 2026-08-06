package com.nova.runtime.storage.database

import com.nova.runtime.storage.dao.DocumentDao
import com.nova.runtime.storage.entities.DocumentEntity
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.nova.runtime.storage.converters.UuidConverter

/** DSS §4 — Room database stub (entities expanded in Sprint 1). */
@Database(
    entities = [DocumentEntity::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(UuidConverter::class)
abstract class NovaDatabase : RoomDatabase() {
    abstract fun documentDao(): DocumentDao
}
