package com.nova.runtime.storage.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** DSS §9 — forward migration from v1 (documents only) to v2 (full MVP schema). */
val Migration_1_2 =
    object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS embeddings (
                    embeddingId TEXT NOT NULL PRIMARY KEY,
                    objectType TEXT NOT NULL,
                    objectId TEXT NOT NULL,
                    modelVersion TEXT NOT NULL,
                    dimension INTEGER NOT NULL,
                    createdAt INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_embeddings_objectId ON embeddings(objectId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_embeddings_objectType ON embeddings(objectType)")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS projects (
                    id TEXT NOT NULL PRIMARY KEY,
                    title TEXT NOT NULL,
                    description TEXT,
                    createdAt INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL,
                    status TEXT NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_projects_updatedAt ON projects(updatedAt)")

            rebuildDocumentsTable(db)

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS photos (
                    id TEXT NOT NULL PRIMARY KEY,
                    uri TEXT NOT NULL,
                    takenAt INTEGER NOT NULL,
                    width INTEGER,
                    height INTEGER,
                    latitude REAL,
                    longitude REAL,
                    ocrText TEXT,
                    embeddingId TEXT,
                    favorite INTEGER NOT NULL,
                    FOREIGN KEY(embeddingId) REFERENCES embeddings(embeddingId) ON DELETE SET NULL
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_photos_takenAt ON photos(takenAt)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_photos_uri ON photos(uri)")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS contacts (
                    id TEXT NOT NULL PRIMARY KEY,
                    displayName TEXT NOT NULL,
                    phone TEXT,
                    email TEXT,
                    lastInteraction INTEGER,
                    importance INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_contacts_displayName ON contacts(displayName)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_contacts_phone ON contacts(phone)")
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_contacts_displayName_lastInteraction " +
                    "ON contacts(displayName, lastInteraction)",
            )

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS sessions (
                    sessionId TEXT NOT NULL PRIMARY KEY,
                    traceId TEXT NOT NULL,
                    startedAt INTEGER NOT NULL,
                    endedAt INTEGER,
                    state TEXT NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_sessions_traceId ON sessions(traceId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_sessions_startedAt ON sessions(startedAt)")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS preferences (
                    `key` TEXT NOT NULL PRIMARY KEY,
                    value TEXT NOT NULL,
                    confidence REAL NOT NULL,
                    updatedAt INTEGER NOT NULL
                )
                """.trimIndent(),
            )

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS execution_history (
                    id TEXT NOT NULL PRIMARY KEY,
                    graphId TEXT NOT NULL,
                    traceId TEXT NOT NULL,
                    status TEXT NOT NULL,
                    duration INTEGER NOT NULL,
                    retryCount INTEGER NOT NULL,
                    completedNodes INTEGER NOT NULL,
                    failedNodes INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_execution_history_graphId ON execution_history(graphId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_execution_history_traceId ON execution_history(traceId)")
        }

        private fun rebuildDocumentsTable(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS documents_new (
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
                    importance INTEGER NOT NULL,
                    FOREIGN KEY(projectId) REFERENCES projects(id) ON DELETE SET NULL,
                    FOREIGN KEY(embeddingId) REFERENCES embeddings(embeddingId) ON DELETE SET NULL
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO documents_new (
                    id, path, name, extension, mimeType, size, checksum,
                    createdAt, modifiedAt, indexedAt, projectId, embeddingId, importance
                )
                SELECT
                    id, path, name, extension, mimeType, size, checksum,
                    createdAt, modifiedAt, indexedAt, projectId, embeddingId, importance
                FROM documents
                """.trimIndent(),
            )
            db.execSQL("DROP TABLE documents")
            db.execSQL("ALTER TABLE documents_new RENAME TO documents")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_documents_path ON documents(path)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_documents_modifiedAt ON documents(modifiedAt)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_documents_checksum ON documents(checksum)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_documents_projectId ON documents(projectId)")
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_documents_projectId_modifiedAt " +
                    "ON documents(projectId, modifiedAt)",
            )
        }
    }
