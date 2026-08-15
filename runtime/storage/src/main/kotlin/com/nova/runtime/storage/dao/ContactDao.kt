package com.nova.runtime.storage.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.nova.runtime.storage.entities.ContactEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID

@Dao
interface ContactDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(contact: ContactEntity)

    @Update
    suspend fun update(contact: ContactEntity)

    @Delete
    suspend fun delete(contact: ContactEntity)

    @Query("SELECT * FROM contacts WHERE id = :id")
    suspend fun getById(id: UUID): ContactEntity?

    @Query("SELECT * FROM contacts WHERE id = :id")
    fun observeById(id: UUID): Flow<ContactEntity?>

    @Query(
        """
        SELECT * FROM contacts
        WHERE displayName LIKE '%' || :query || '%'
           OR phone LIKE '%' || :query || '%'
           OR email LIKE '%' || :query || '%'
        ORDER BY importance DESC, displayName ASC
        """,
    )
    suspend fun search(query: String): List<ContactEntity>
}
