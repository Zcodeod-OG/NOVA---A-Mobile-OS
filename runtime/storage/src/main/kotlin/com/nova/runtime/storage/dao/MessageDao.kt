package com.nova.runtime.storage.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.nova.runtime.storage.entities.MessageEntity
import java.util.UUID

@Dao
interface MessageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: MessageEntity): Long

    @Update
    suspend fun update(message: MessageEntity)

    @Query("SELECT * FROM messages WHERE id = :id")
    suspend fun getById(id: UUID): MessageEntity?

    @Query("SELECT * FROM messages WHERE externalId = :externalId LIMIT 1")
    suspend fun getByExternalId(externalId: String): MessageEntity?

    @Query("SELECT * FROM messages ORDER BY receivedAt DESC LIMIT :limit")
    suspend fun getRecent(limit: Int): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE channel = :channel ORDER BY receivedAt DESC LIMIT :limit")
    suspend fun listByChannel(channel: String, limit: Int): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE indexedAt IS NULL ORDER BY receivedAt DESC LIMIT :limit")
    suspend fun listUnindexed(limit: Int): List<MessageEntity>

    @Query(
        """
        SELECT * FROM messages
        WHERE channel = :channel
          AND (body LIKE '%' || :query || '%' OR sender LIKE '%' || :query || '%')
        ORDER BY receivedAt DESC
        LIMIT :limit
        """,
    )
    suspend fun search(channel: String, query: String, limit: Int): List<MessageEntity>

    @Query("SELECT COUNT(*) FROM messages WHERE channel = :channel")
    suspend fun countByChannel(channel: String): Int

    @Query(
        """
        SELECT * FROM messages
        WHERE importanceScore IS NOT NULL
        ORDER BY importanceScore DESC, receivedAt DESC
        LIMIT :limit
        """,
    )
    suspend fun getHighImportance(limit: Int): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE receivedAt >= :since ORDER BY receivedAt DESC")
    suspend fun getSince(since: Long): List<MessageEntity>
}
