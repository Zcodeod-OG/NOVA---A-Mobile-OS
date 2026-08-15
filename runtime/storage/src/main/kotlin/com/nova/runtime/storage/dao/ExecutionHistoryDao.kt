package com.nova.runtime.storage.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.nova.runtime.storage.entities.ExecutionHistoryEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID

@Dao
interface ExecutionHistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(execution: ExecutionHistoryEntity)

    @Update
    suspend fun update(execution: ExecutionHistoryEntity)

    @Delete
    suspend fun delete(execution: ExecutionHistoryEntity)

    @Query("SELECT * FROM execution_history WHERE id = :id")
    suspend fun getById(id: UUID): ExecutionHistoryEntity?

    @Query("SELECT * FROM execution_history WHERE id = :id")
    fun observeById(id: UUID): Flow<ExecutionHistoryEntity?>

    @Query("SELECT * FROM execution_history WHERE graphId = :graphId ORDER BY duration DESC")
    suspend fun getByGraphId(graphId: UUID): List<ExecutionHistoryEntity>
}
