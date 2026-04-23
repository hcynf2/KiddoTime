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

    @Query("UPDATE screen_time_requests SET status = :status, resolvedAt = :resolvedAt WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String, resolvedAt: Long)

    /** Number of requests submitted since [startOfDay] (epoch ms). Used to enforce the 1-per-day cap. */
    @Query("SELECT COUNT(*) FROM screen_time_requests WHERE requestedAt >= :startOfDay")
    suspend fun countTodayRequests(startOfDay: Long): Int

    // ── Analysis queries ──────────────────────────────────────────────────────

    @Query("SELECT COUNT(*) FROM screen_time_requests")
    suspend fun getTotalRequests(): Int

    @Query("SELECT COUNT(*) FROM screen_time_requests WHERE status = 'APPROVED'")
    suspend fun getTotalApproved(): Int

    @Query("SELECT COUNT(*) FROM screen_time_requests WHERE status = 'DENIED'")
    suspend fun getTotalDenied(): Int

    /** Average ms between requestedAt and resolvedAt, across all resolved rows. Null if none. */
    @Query("SELECT AVG(resolvedAt - requestedAt) FROM screen_time_requests WHERE resolvedAt IS NOT NULL")
    suspend fun getAvgResponseMs(): Double?

    /** App name that appears most often in requests, or null if the table is empty. */
    @Query("""
        SELECT appName FROM screen_time_requests
        GROUP BY appName
        ORDER BY COUNT(*) DESC
        LIMIT 1
    """)
    suspend fun getMostRequestedApp(): String?

    /** Delete all rows. */
    @Query("DELETE FROM screen_time_requests")
    suspend fun clearAll()
}