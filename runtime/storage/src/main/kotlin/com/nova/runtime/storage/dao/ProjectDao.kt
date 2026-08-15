package com.nova.runtime.storage.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.nova.runtime.storage.entities.ProjectEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID

@Dao
interface ProjectDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(project: ProjectEntity)

    @Update
    suspend fun update(project: ProjectEntity)

    @Delete
    suspend fun delete(project: ProjectEntity)

    @Query("SELECT * FROM projects WHERE id = :id")
    suspend fun getById(id: UUID): ProjectEntity?

    @Query("SELECT * FROM projects WHERE id = :id")
    fun observeById(id: UUID): Flow<ProjectEntity?>

    @Query("SELECT * FROM projects WHERE title LIKE '%' || :query || '%' ORDER BY updatedAt DESC")
    suspend fun search(query: String): List<ProjectEntity>
}
