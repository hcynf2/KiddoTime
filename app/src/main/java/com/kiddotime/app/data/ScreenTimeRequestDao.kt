package com.kiddotime.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ScreenTimeRequestDao {

    @Insert
    suspend fun insert(request: ScreenTimeRequest)

    @Query("SELECT * FROM screen_time_requests WHERE status = 'PENDING' ORDER BY requestedAt DESC")
    fun getPendingRequests(): Flow<List<ScreenTimeRequest>>

    @Query("UPDATE screen_time_requests SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String)

    /** Delete all rows. */
    @Query("DELETE FROM screen_time_requests")
    suspend fun clearAll()
}