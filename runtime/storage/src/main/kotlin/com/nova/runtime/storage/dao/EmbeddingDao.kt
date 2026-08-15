package com.nova.runtime.storage.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.nova.runtime.storage.entities.EmbeddingEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID

@Dao
interface EmbeddingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(embedding: EmbeddingEntity)

    @Update
    suspend fun update(embedding: EmbeddingEntity)

    @Delete
    suspend fun delete(embedding: EmbeddingEntity)

    @Query("SELECT * FROM embeddings WHERE embeddingId = :embeddingId")
    suspend fun getById(embeddingId: UUID): EmbeddingEntity?

    @Query("SELECT * FROM embeddings WHERE embeddingId = :embeddingId")
    fun observeById(embeddingId: UUID): Flow<EmbeddingEntity?>

    @Query("SELECT * FROM embeddings WHERE objectId = :objectId")
    suspend fun getByObjectId(objectId: UUID): List<EmbeddingEntity>

    @Query("SELECT * FROM embeddings WHERE vectorBlob IS NOT NULL")
    suspend fun listWithPersistedVectors(): List<EmbeddingEntity>

    @Query("SELECT COUNT(*) FROM embeddings")
    fun observeCount(): Flow<Int>

    @Query("SELECT * FROM embeddings")
    fun observeAll(): Flow<List<EmbeddingEntity>>
}
