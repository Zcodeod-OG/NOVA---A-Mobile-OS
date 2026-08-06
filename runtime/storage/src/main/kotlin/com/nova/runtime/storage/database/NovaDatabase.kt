package com.nova.runtime.storage.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.nova.runtime.storage.converters.UuidConverter
import com.nova.runtime.storage.dao.ContactDao
import com.nova.runtime.storage.dao.DocumentDao
import com.nova.runtime.storage.dao.EmbeddingDao
import com.nova.runtime.storage.dao.ExecutionHistoryDao
import com.nova.runtime.storage.dao.PhotoDao
import com.nova.runtime.storage.dao.PreferenceDao
import com.nova.runtime.storage.dao.ProjectDao
import com.nova.runtime.storage.dao.SessionDao
import com.nova.runtime.storage.entities.ContactEntity
import com.nova.runtime.storage.entities.DocumentEntity
import com.nova.runtime.storage.entities.EmbeddingEntity
import com.nova.runtime.storage.entities.ExecutionHistoryEntity
import com.nova.runtime.storage.entities.PhotoEntity
import com.nova.runtime.storage.entities.PreferenceEntity
import com.nova.runtime.storage.entities.ProjectEntity
import com.nova.runtime.storage.entities.SessionEntity
import com.nova.runtime.storage.migrations.StorageMigrations

/** DSS §4 — Room database for NOVA structured metadata. */
@Database(
    entities = [
        EmbeddingEntity::class,
        ProjectEntity::class,
        DocumentEntity::class,
        PhotoEntity::class,
        ContactEntity::class,
        SessionEntity::class,
        PreferenceEntity::class,
        ExecutionHistoryEntity::class,
    ],
    version = StorageMigrations.CURRENT_VERSION,
    exportSchema = true,
)
@TypeConverters(UuidConverter::class)
abstract class NovaDatabase : RoomDatabase() {
    abstract fun documentDao(): DocumentDao

    abstract fun photoDao(): PhotoDao

    abstract fun contactDao(): ContactDao

    abstract fun projectDao(): ProjectDao

    abstract fun sessionDao(): SessionDao

    abstract fun preferenceDao(): PreferenceDao

    abstract fun executionHistoryDao(): ExecutionHistoryDao

    abstract fun embeddingDao(): EmbeddingDao
}
