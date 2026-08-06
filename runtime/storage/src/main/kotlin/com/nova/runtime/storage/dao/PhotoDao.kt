package com.nova.runtime.storage.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.nova.runtime.storage.entities.PhotoEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID

@Dao
interface PhotoDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(photo: PhotoEntity)

    @Update
    suspend fun update(photo: PhotoEntity)

    @Delete
    suspend fun delete(photo: PhotoEntity)

    @Query("SELECT * FROM photos WHERE id = :id")
    suspend fun getById(id: UUID): PhotoEntity?

    @Query("SELECT * FROM photos WHERE id = :id")
    fun observeById(id: UUID): Flow<PhotoEntity?>

    @Query("SELECT * FROM photos WHERE uri = :uri LIMIT 1")
    suspend fun getByUri(uri: String): PhotoEntity?

    @Query("SELECT * FROM photos WHERE ocrText LIKE '%' || :query || '%' ORDER BY takenAt DESC")
    suspend fun searchByOcr(query: String): List<PhotoEntity>
}
