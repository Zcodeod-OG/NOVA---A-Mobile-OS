package com.nova.runtime.storage.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * DSS §9 — v4 to v5: document content extraction status
 * (NOT_TRIED / FAILED / EMPTY / SUCCESS) plus last-attempt timestamp.
 */
val Migration_4_5 =
    object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE documents ADD COLUMN contentExtractStatus TEXT NOT NULL DEFAULT 'NOT_TRIED'",
            )
            db.execSQL("ALTER TABLE documents ADD COLUMN contentExtractedAt INTEGER")
            // Backfill so prior successful bodies are not re-extracted as never-tried.
            db.execSQL(
                """
                UPDATE documents
                SET contentExtractStatus = 'SUCCESS'
                WHERE contentText IS NOT NULL AND length(contentText) > 0
                """.trimIndent(),
            )
            db.execSQL(
                """
                UPDATE documents
                SET contentExtractStatus = 'EMPTY'
                WHERE contentText IS NOT NULL AND length(contentText) = 0
                """.trimIndent(),
            )
        }
    }
