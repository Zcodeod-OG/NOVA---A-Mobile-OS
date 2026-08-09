package com.nova.runtime.storage.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** DSS §9 — v3 to v4: adds extractive document summary for two-stage discovery retrieval. */
val Migration_3_4 =
    object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE documents ADD COLUMN summary TEXT")
        }
    }
