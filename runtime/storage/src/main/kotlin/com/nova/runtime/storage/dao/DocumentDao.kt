package com.nova.runtime.storage.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.nova.runtime.storage.entities.DocumentEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/** DSS §7 — Document data access. */
@Dao
interface DocumentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(document: DocumentEntity)

    @Update
    suspend fun update(document: DocumentEntity)

    @Delete
    suspend fun delete(document: DocumentEntity)

    @Query("SELECT * FROM documents WHERE id = :id")
    suspend fun getById(id: UUID): DocumentEntity?

    @Query("SELECT * FROM documents WHERE id = :id")
    fun observeById(id: UUID): Flow<DocumentEntity?>

    @Query("SELECT * FROM documents WHERE path = :path LIMIT 1")
    suspend fun getByPath(path: String): DocumentEntity?

    @Query("SELECT * FROM documents WHERE name LIKE '%' || :query || '%' ORDER BY modifiedAt DESC")
    suspend fun searchByName(query: String): List<DocumentEntity>

    @Query(
        """
        SELECT * FROM documents
        WHERE name LIKE '%' || :query || '%'
           OR path LIKE '%' || :query || '%'
           OR extension LIKE '%' || :query || '%'
           OR summary LIKE '%' || :query || '%'
           OR contentText LIKE '%' || :query || '%'
        ORDER BY modifiedAt DESC
        LIMIT :limit OFFSET :offset
        """,
    )
    suspend fun searchFullText(query: String, limit: Int, offset: Int): List<DocumentEntity>

    @Query(
        """
        SELECT COUNT(*) FROM documents
        WHERE name LIKE '%' || :query || '%'
           OR path LIKE '%' || :query || '%'
           OR extension LIKE '%' || :query || '%'
           OR summary LIKE '%' || :query || '%'
           OR contentText LIKE '%' || :query || '%'
        """,
    )
    suspend fun countFullText(query: String): Int

    @Query("SELECT * FROM documents WHERE projectId = :projectId ORDER BY modifiedAt DESC")
    suspend fun getByProjectId(projectId: UUID): List<DocumentEntity>

    @Query("SELECT * FROM documents WHERE embeddingId IS NULL ORDER BY modifiedAt DESC LIMIT :limit")
    suspend fun listUnindexed(limit: Int): List<DocumentEntity>

    /**
     * Extractable documents that still need body extraction or re-extraction
     * (never tried, failed, or empty OCR). Restricted to supported mime/extension types.
     */
    @Query(
        """
        SELECT * FROM documents
        WHERE contentExtractStatus IN ('NOT_TRIED', 'FAILED', 'EMPTY')
          AND (contentText IS NULL OR length(trim(contentText)) = 0)
          AND (
            lower(mimeType) = 'application/pdf'
            OR lower(mimeType) LIKE 'text/%'
            OR lower(mimeType) LIKE 'image/%'
            OR lower(mimeType) LIKE 'application/vnd.openxmlformats%'
            OR lower(extension) IN (
                'pdf', 'txt', 'md', 'csv', 'json', 'log', 'xml', 'html', 'htm',
                'png', 'jpg', 'jpeg', 'webp', 'gif', 'bmp', 'docx'
            )
          )
        ORDER BY
          CASE contentExtractStatus
            WHEN 'NOT_TRIED' THEN 0
            WHEN 'FAILED' THEN 1
            ELSE 2
          END,
          modifiedAt DESC
        LIMIT :limit
        """,
    )
    suspend fun listMissingContentText(limit: Int): List<DocumentEntity>

    /**
     * Documents that have content (or metadata-only) but no discovery summary yet —
     * re-queued so Stage A summary embeddings can be backfilled after migration.
     */
    @Query(
        """
        SELECT * FROM documents
        WHERE summary IS NULL
        ORDER BY modifiedAt DESC
        LIMIT :limit
        """,
    )
    suspend fun listMissingSummary(limit: Int): List<DocumentEntity>

    @Query("SELECT COUNT(*) FROM documents")
    suspend fun countAll(): Int

    @Query("SELECT COUNT(*) FROM documents WHERE summary IS NOT NULL AND length(summary) > 0")
    suspend fun countWithSummary(): Int

    @Query("SELECT COUNT(*) FROM documents WHERE summary IS NULL OR length(summary) = 0")
    suspend fun countMissingSummary(): Int
}
