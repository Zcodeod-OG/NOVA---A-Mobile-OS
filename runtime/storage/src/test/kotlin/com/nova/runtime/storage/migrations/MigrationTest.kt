package com.nova.runtime.storage.migrations

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import com.nova.runtime.storage.StorageRobolectricTest
import org.junit.Assert.assertTrue
import org.junit.Test

class MigrationTest : StorageRobolectricTest() {

    @Test
    fun migrate1To2_addsMvpTables() {
        val helper =
            FrameworkSQLiteOpenHelperFactory().create(
                SupportSQLiteOpenHelper.Configuration.builder(context)
                    .name("migration_test.db")
                    .callback(V1SchemaCallback())
                    .build(),
            )
        val db = helper.writableDatabase
        try {
            Migration_1_2.migrate(db)

            assertTrue(
                db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='photos'").moveToFirst(),
            )
            assertTrue(
                db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='embeddings'").moveToFirst(),
            )
        } finally {
            db.close()
            helper.close()
        }
    }

    private class V1SchemaCallback : SupportSQLiteOpenHelper.Callback(1) {
        override fun onCreate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS documents (
                    id TEXT NOT NULL PRIMARY KEY,
                    path TEXT NOT NULL,
                    name TEXT NOT NULL,
                    extension TEXT NOT NULL,
                    mimeType TEXT NOT NULL,
                    size INTEGER NOT NULL,
                    checksum TEXT NOT NULL,
                    createdAt INTEGER NOT NULL,
                    modifiedAt INTEGER NOT NULL,
                    indexedAt INTEGER,
                    projectId TEXT,
                    embeddingId TEXT,
                    importance INTEGER NOT NULL
                )
                """.trimIndent(),
            )
        }

        override fun onUpgrade(
            db: SupportSQLiteDatabase,
            oldVersion: Int,
            newVersion: Int,
        ) = Unit
    }
}
