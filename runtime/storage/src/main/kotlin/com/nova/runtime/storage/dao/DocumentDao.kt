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
        """,
    )
    suspend fun countFullText(query: String): Int

    @Query("SELECT * FROM documents WHERE projectId = :projectId ORDER BY modifiedAt DESC")
    suspend fun getByProjectId(projectId: UUID): List<DocumentEntity>

    @Query("SELECT * FROM documents WHERE embeddingId IS NULL ORDER BY modifiedAt DESC LIMIT :limit")
    suspend fun listUnindexed(limit: Int): List<DocumentEntity>
}
