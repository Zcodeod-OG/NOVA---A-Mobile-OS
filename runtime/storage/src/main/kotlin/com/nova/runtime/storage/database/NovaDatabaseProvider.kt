package com.nova.runtime.storage.database

import android.content.Context
import androidx.room.Room
import com.nova.runtime.storage.migrations.StorageMigrations

/** Factory for production and test database instances. */
object NovaDatabaseProvider {
    private const val DATABASE_NAME = "nova_storage.db"

    fun create(context: Context): NovaDatabase =
        Room.databaseBuilder(
            context.applicationContext,
            NovaDatabase::class.java,
            DATABASE_NAME,
        )
            .addMigrations(*StorageMigrations.ALL)
            .build()

    fun createInMemory(context: Context): NovaDatabase =
        Room.inMemoryDatabaseBuilder(
            context.applicationContext,
            NovaDatabase::class.java,
        )
            .addMigrations(*StorageMigrations.ALL)
            .build()
}
