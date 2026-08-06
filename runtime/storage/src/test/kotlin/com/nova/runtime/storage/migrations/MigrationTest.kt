package com.nova.runtime.storage.migrations

import android.content.Context
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.nova.runtime.storage.database.NovaDatabase
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class MigrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @get:Rule
    val helper: MigrationTestHelper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            NovaDatabase::class.java,
            emptyList(),
            FrameworkSQLiteOpenHelperFactory(),
        )

    @Test
    fun migrate1To2_addsMvpTables() {
        helper.createDatabase(TEST_DB, StorageMigrations.VERSION_1).apply {
            execSQL(
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
            close()
        }

        helper.runMigrationsAndValidate(
            TEST_DB,
            StorageMigrations.VERSION_2,
            true,
            Migration_1_2,
        )

        val db = helper.openDatabase(TEST_DB)
        assertTrue(db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='photos'").moveToFirst())
        assertTrue(db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='embeddings'").moveToFirst())
        db.close()
    }

    private companion object {
        const val TEST_DB = "migration-test"
    }
}
