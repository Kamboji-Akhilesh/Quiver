package com.example.screenshotcleaner.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    @Query("SELECT * FROM deletion_history ORDER BY deletedAt DESC")
    fun getAllHistory(): Flow<List<HistoryItem>>

    @Insert
    suspend fun insert(item: HistoryItem)

    @Delete
    suspend fun delete(item: HistoryItem)

    @Query("DELETE FROM deletion_history WHERE deletedAt < :cutoffTime")
    suspend fun deleteOlderThan(cutoffTime: Long)

    @Query("SELECT COUNT(*) FROM deletion_history")
    suspend fun getCount(): Int
}

