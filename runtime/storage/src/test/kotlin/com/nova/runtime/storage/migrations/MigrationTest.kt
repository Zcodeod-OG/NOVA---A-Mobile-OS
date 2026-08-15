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

    @Test
    fun migrate2To3_addsContentTextColumn() {
        val helper =
            FrameworkSQLiteOpenHelperFactory().create(
                SupportSQLiteOpenHelper.Configuration.builder(context)
                    .name("migration_test_2_3.db")
                    .callback(V1SchemaCallback())
                    .build(),
            )
        val db = helper.writableDatabase
        try {
            Migration_1_2.migrate(db)
            Migration_2_3.migrate(db)

            val cursor = db.query("PRAGMA table_info(documents)")
            var hasContentText = false
            cursor.use {
                val nameIndex = it.getColumnIndexOrThrow("name")
                while (it.moveToNext()) {
                    if (it.getString(nameIndex) == "contentText") hasContentText = true
                }
            }
            assertTrue(hasContentText)

            db.execSQL(
                "INSERT INTO documents (id, path, name, extension, mimeType, size, checksum, createdAt, " +
                    "modifiedAt, importance, contentText) " +
                    "VALUES ('doc-1', 'content://d/1', 'menu.pdf', 'pdf', 'application/pdf', 1, '', 0, 0, 0, 'FRIDAY menu')",
            )
            assertTrue(
                db.query("SELECT contentText FROM documents WHERE id = 'doc-1'").use { row ->
                    row.moveToFirst() && row.getString(0) == "FRIDAY menu"
                },
            )
        } finally {
            db.close()
            helper.close()
        }
    }

    @Test
    fun migrate4To5_addsContentExtractStatusColumns() {
        val helper =
            FrameworkSQLiteOpenHelperFactory().create(
                SupportSQLiteOpenHelper.Configuration.builder(context)
                    .name("migration_test_4_5.db")
                    .callback(V1SchemaCallback())
                    .build(),
            )
        val db = helper.writableDatabase
        try {
            Migration_1_2.migrate(db)
            Migration_2_3.migrate(db)
            Migration_3_4.migrate(db)

            db.execSQL(
                "INSERT INTO documents (id, path, name, extension, mimeType, size, checksum, createdAt, " +
                    "modifiedAt, importance, contentText, summary) " +
                    "VALUES ('doc-ok', 'content://d/ok', 'menu.pdf', 'pdf', 'application/pdf', 1, '', 0, 0, 0, " +
                    "'FRIDAY dinner', 'menu.pdf: FRIDAY')," +
                    "('doc-empty', 'content://d/empty', 'blank.pdf', 'pdf', 'application/pdf', 1, '', 0, 0, 0, " +
                    "'', NULL)," +
                    "('doc-null', 'content://d/null', 'pending.pdf', 'pdf', 'application/pdf', 1, '', 0, 0, 0, " +
                    "NULL, NULL)",
            )

            Migration_4_5.migrate(db)

            val cursor = db.query("PRAGMA table_info(documents)")
            var hasStatus = false
            var hasExtractedAt = false
            cursor.use {
                val nameIndex = it.getColumnIndexOrThrow("name")
                while (it.moveToNext()) {
                    when (it.getString(nameIndex)) {
                        "contentExtractStatus" -> hasStatus = true
                        "contentExtractedAt" -> hasExtractedAt = true
                    }
                }
            }
            assertTrue(hasStatus)
            assertTrue(hasExtractedAt)

            assertTrue(
                db.query("SELECT contentExtractStatus FROM documents WHERE id = 'doc-ok'").use { row ->
                    row.moveToFirst() && row.getString(0) == "SUCCESS"
                },
            )
            assertTrue(
                db.query("SELECT contentExtractStatus FROM documents WHERE id = 'doc-empty'").use { row ->
                    row.moveToFirst() && row.getString(0) == "EMPTY"
                },
            )
            assertTrue(
                db.query("SELECT contentExtractStatus FROM documents WHERE id = 'doc-null'").use { row ->
                    row.moveToFirst() && row.getString(0) == "NOT_TRIED"
                },
            )
        } finally {
            db.close()
            helper.close()
        }
    }

    @Test
    fun migrate3To4_addsSummaryColumn() {
        val helper =
            FrameworkSQLiteOpenHelperFactory().create(
                SupportSQLiteOpenHelper.Configuration.builder(context)
                    .name("migration_test_3_4.db")
                    .callback(V1SchemaCallback())
                    .build(),
            )
        val db = helper.writableDatabase
        try {
            Migration_1_2.migrate(db)
            Migration_2_3.migrate(db)
            Migration_3_4.migrate(db)

            val cursor = db.query("PRAGMA table_info(documents)")
            var hasSummary = false
            cursor.use {
                val nameIndex = it.getColumnIndexOrThrow("name")
                while (it.moveToNext()) {
                    if (it.getString(nameIndex) == "summary") hasSummary = true
                }
            }
            assertTrue(hasSummary)

            db.execSQL(
                "INSERT INTO documents (id, path, name, extension, mimeType, size, checksum, createdAt, " +
                    "modifiedAt, importance, contentText, summary) " +
                    "VALUES ('doc-2', 'content://d/2', 'bookly.pdf', 'pdf', 'application/pdf', 1, '', 0, 0, 0, " +
                    "'long body', 'bookly.pdf: prospectus')",
            )
            assertTrue(
                db.query("SELECT summary FROM documents WHERE id = 'doc-2'").use { row ->
                    row.moveToFirst() && row.getString(0) == "bookly.pdf: prospectus"
                },
            )
        } finally {
            db.close()
            helper.close()
        }
    }

    @Test
    fun migrate4To5_addsContentExtractStatusColumn() {
        val helper =
            FrameworkSQLiteOpenHelperFactory().create(
                SupportSQLiteOpenHelper.Configuration.builder(context)
                    .name("migration_test_4_5.db")
                    .callback(V1SchemaCallback())
                    .build(),
            )
        val db = helper.writableDatabase
        try {
            Migration_1_2.migrate(db)
            Migration_2_3.migrate(db)
            Migration_3_4.migrate(db)
            Migration_4_5.migrate(db)

            val cursor = db.query("PRAGMA table_info(documents)")
            var hasStatus = false
            cursor.use {
                val nameIndex = it.getColumnIndexOrThrow("name")
                while (it.moveToNext()) {
                    if (it.getString(nameIndex) == "contentExtractStatus") hasStatus = true
                }
            }
            assertTrue(hasStatus)

            db.execSQL(
                "INSERT INTO documents (id, path, name, extension, mimeType, size, checksum, createdAt, " +
                    "modifiedAt, importance, contentText, contentExtractStatus) " +
                    "VALUES ('doc-3', 'content://d/3', 'menu.pdf', 'pdf', 'application/pdf', 1, '', 0, 0, 0, " +
                    "'FRIDAY menu', 'SUCCESS')",
            )
            assertTrue(
                db.query("SELECT contentExtractStatus FROM documents WHERE id = 'doc-3'").use { row ->
                    row.moveToFirst() && row.getString(0) == "SUCCESS"
                },
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
