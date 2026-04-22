package com.kiddotime.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

/** Returned by [LimitEventDao.getHardestApp]. */
data class AppHitCount(
    val packageName: String,
    val appName: String,
    val hitCount: Int
)

/** Returned by [LimitEventDao.getDailyStopStats]. */
data class DayStopStats(
    val dayBucket: Long,  // epoch-day = limitReachedAt / 86_400_000
    val total: Int,       // total limit hits that day
    val onTime: Int       // hits closed within ON_TIME_THRESHOLD_MS
)

@Dao
interface LimitEventDao {

    @Insert
    suspend fun insert(event: LimitEvent): Long

    /**
     * Stamps [closedAt] on the most-recent open event for [packageName].
     * "Open" means appClosedAt IS NULL.
     */
    @Query("""
        UPDATE limit_events
        SET appClosedAt = :closedAt
        WHERE id = (
            SELECT id FROM limit_events
            WHERE packageName = :packageName AND appClosedAt IS NULL
            ORDER BY limitReachedAt DESC
            LIMIT 1
        )
    """)
    suspend fun recordAppClosed(packageName: String, closedAt: Long)

    /** Average ms between limit firing and overlay dismissed. Null if no completed events. */
    @Query("""
        SELECT AVG(CAST(appClosedAt - limitReachedAt AS REAL))
        FROM limit_events
        WHERE appClosedAt IS NOT NULL
    """)
    suspend fun getAvgTransitionLatencyMs(): Double?

    /**
     * App with the most limit hits since [since] (epoch ms).
     * Returns null when the table is empty.
     */
    @Query("""
        SELECT packageName, appName, COUNT(*) AS hitCount
        FROM limit_events
        WHERE limitReachedAt >= :since
        GROUP BY packageName
        ORDER BY hitCount DESC
        LIMIT 1
    """)
    suspend fun getHardestApp(since: Long): AppHitCount?

    /**
     * Per-day totals since [since], with [onTime] counting events closed
     * within [thresholdMs] milliseconds of the limit firing.
     */
    @Query("""
        SELECT
            (limitReachedAt / 86400000) AS dayBucket,
            COUNT(*) AS total,
            SUM(CASE WHEN appClosedAt IS NOT NULL
                          AND (appClosedAt - limitReachedAt) <= :thresholdMs
                     THEN 1 ELSE 0 END) AS onTime
        FROM limit_events
        WHERE limitReachedAt >= :since
        GROUP BY dayBucket
        ORDER BY dayBucket ASC
    """)
    suspend fun getDailyStopStats(since: Long, thresholdMs: Long): List<DayStopStats>
}