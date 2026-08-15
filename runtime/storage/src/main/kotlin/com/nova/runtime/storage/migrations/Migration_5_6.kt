package com.nova.runtime.storage.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v5 → v6: persist embedding vectors in Room, add message ingestion table
 * for Gmail / WhatsApp notification capture.
 */
val Migration_5_6 =
    object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE embeddings ADD COLUMN vectorBlob BLOB")
            db.execSQL("ALTER TABLE embeddings ADD COLUMN embeddingKind TEXT")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS messages (
                    id TEXT NOT NULL PRIMARY KEY,
                    channel TEXT NOT NULL,
                    sender TEXT NOT NULL,
                    body TEXT NOT NULL,
                    threadKey TEXT,
                    receivedAt INTEGER NOT NULL,
                    externalId TEXT NOT NULL,
                    subject TEXT,
                    indexedAt INTEGER,
                    importanceScore REAL
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_messages_channel ON messages(channel)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_messages_threadKey ON messages(threadKey)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_messages_receivedAt ON messages(receivedAt)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_messages_externalId ON messages(externalId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_messages_importanceScore ON messages(importanceScore)")
        }
    }
