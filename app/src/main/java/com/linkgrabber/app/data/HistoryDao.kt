package com.linkgrabber.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    @Insert
    suspend fun insert(entry: HistoryEntry)

    @Query("SELECT * FROM history ORDER BY visitedAt DESC")
    fun observeAll(): Flow<List<HistoryEntry>>

    @Query("DELETE FROM history")
    suspend fun clear()

    @Query("DELETE FROM history WHERE id = :id")
    suspend fun delete(id: Long)
}
