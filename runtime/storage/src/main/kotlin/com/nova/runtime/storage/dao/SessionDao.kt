package com.nova.runtime.storage.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.nova.runtime.storage.entities.SessionEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID

@Dao
interface SessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: SessionEntity)

    @Update
    suspend fun update(session: SessionEntity)

    @Delete
    suspend fun delete(session: SessionEntity)

    @Query("SELECT * FROM sessions WHERE sessionId = :sessionId")
    suspend fun getById(sessionId: UUID): SessionEntity?

    @Query("SELECT * FROM sessions WHERE sessionId = :sessionId")
    fun observeById(sessionId: UUID): Flow<SessionEntity?>

    @Query("SELECT * FROM sessions WHERE traceId = :traceId ORDER BY startedAt DESC")
    suspend fun getByTraceId(traceId: UUID): List<SessionEntity>
}
