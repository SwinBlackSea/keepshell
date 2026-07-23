package com.keepshell.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface HostDao {
    @Query(
        """
        SELECT * FROM hosts
        ORDER BY favorite DESC, sortOrder ASC,
        CASE WHEN lastConnectedAt IS NULL THEN 1 ELSE 0 END,
        lastConnectedAt DESC, name COLLATE NOCASE ASC
        """
    )
    fun observeAll(): Flow<List<HostEntity>>

    @Query("SELECT * FROM hosts WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): HostEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(host: HostEntity): Long

    @Update
    suspend fun update(host: HostEntity)

    @Delete
    suspend fun delete(host: HostEntity)

    @Query("UPDATE hosts SET lastConnectedAt = :timestamp WHERE id = :id")
    suspend fun markConnected(id: Long, timestamp: Long)
}
