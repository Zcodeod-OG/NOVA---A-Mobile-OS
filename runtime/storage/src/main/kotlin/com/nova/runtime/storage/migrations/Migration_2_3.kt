package com.nova.runtime.storage.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** DSS §9 — v2 to v3: adds extracted document content text for content-aware search. */
val Migration_2_3 =
    object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE documents ADD COLUMN contentText TEXT")
        }
    }
