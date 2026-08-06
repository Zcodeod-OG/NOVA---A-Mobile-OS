package com.nova.runtime.storage.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.nova.runtime.storage.entities.PreferenceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PreferenceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(preference: PreferenceEntity)

    @Update
    suspend fun update(preference: PreferenceEntity)

    @Delete
    suspend fun delete(preference: PreferenceEntity)

    @Query("SELECT * FROM preferences WHERE `key` = :key")
    suspend fun getByKey(key: String): PreferenceEntity?

    @Query("SELECT * FROM preferences WHERE `key` = :key")
    fun observeByKey(key: String): Flow<PreferenceEntity?>

    @Query("SELECT * FROM preferences WHERE `key` LIKE '%' || :query || '%'")
    suspend fun search(query: String): List<PreferenceEntity>
}
