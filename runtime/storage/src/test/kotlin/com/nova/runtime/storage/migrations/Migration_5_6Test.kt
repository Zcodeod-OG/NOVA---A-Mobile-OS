package com.nova.runtime.storage.migrations

import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.sqlite.db.SupportSQLiteOpenHelper
import com.nova.runtime.storage.StorageRobolectricTest
import org.junit.Assert.assertTrue
import org.junit.Test

class Migration_5_6Test : StorageRobolectricTest() {

    @Test
    fun migrate5To6_createsMessagesTable() {
        val helper =
            FrameworkSQLiteOpenHelperFactory().create(
                SupportSQLiteOpenHelper.Configuration.builder(context)
                    .name("migration_test_5_6.db")
                    .callback(V5SchemaCallback())
                    .build(),
            )
        val db = helper.writableDatabase
        try {
            Migration_5_6.migrate(db)
            db.query("PRAGMA table_info(messages)").use { cursor ->
                assertTrue(cursor.count > 0)
            }
        } finally {
            db.close()
            helper.close()
        }
    }

    private class V5SchemaCallback : SupportSQLiteOpenHelper.Callback(StorageMigrations.VERSION_5) {
        override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS preferences (
                    key TEXT NOT NULL PRIMARY KEY,
                    value TEXT NOT NULL,
                    confidence REAL NOT NULL,
                    updatedAt INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS embeddings (
                    embeddingId TEXT NOT NULL PRIMARY KEY,
                    objectId TEXT NOT NULL,
                    objectType TEXT NOT NULL,
                    modelVersion TEXT NOT NULL,
                    dimension INTEGER NOT NULL,
                    createdAt INTEGER NOT NULL
                )
                """.trimIndent(),
            )
        }

        override fun onUpgrade(
            db: androidx.sqlite.db.SupportSQLiteDatabase,
            oldVersion: Int,
            newVersion: Int,
        ) = Unit
    }
}
