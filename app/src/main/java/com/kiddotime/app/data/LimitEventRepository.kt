package com.kiddotime.app.data

import kotlinx.coroutines.flow.Flow
import java.util.Calendar

data class TodayStopStats(
    val onTime: Int,  // stops closed within ON_TIME_THRESHOLD_MS
    val total: Int    // all completed stops today
)

class LimitEventRepository(private val dao: LimitEventDao) {

    companion object {
        /** Child closed the app within this window → counts as "on time". */
        const val ON_TIME_THRESHOLD_MS = 60_000L  // 60 seconds
    }

    suspend fun recordLimitReached(packageName: String, appName: String, timestamp: Long) {
        dao.insert(LimitEvent(packageName = packageName, appName = appName, limitReachedAt = timestamp))
    }

    suspend fun recordAppClosed(packageName: String, timestamp: Long) {
        dao.recordAppClosed(packageName, timestamp)
    }

    /** Average ms from limit firing to overlay dismissed. Null when no data. */
    suspend fun getAvgTransitionLatencyMs(): Long? =
        dao.getAvgTransitionLatencyMs()?.toLong()

    /** App with the most limit hits in the last [withinDays] days. */
    suspend fun getHardestApp(withinDays: Int = 7): AppHitCount? {
        val since = System.currentTimeMillis() - withinDays * 24 * 60 * 60 * 1000L
        return dao.getHardestApp(since)
    }

    /** Today's on-time and total completed stop counts. */
    suspend fun getTodayStats(): TodayStopStats {
        val startOfDay = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        return TodayStopStats(
            onTime = dao.getTodayOnTimeCount(startOfDay, ON_TIME_THRESHOLD_MS),
            total  = dao.getTodayClosedCount(startOfDay)
        )
    }

    /**
     * Emits whenever the limit_events table changes — used by ChildViewModel
     * to reactively refresh without polling.
     */
    fun observeChanges(): Flow<Int> = dao.observeEventCount()

    /** Total on-time stops across all time. */
    suspend fun getTotalOnTimeCount(): Int =
        dao.getTotalOnTimeCount(ON_TIME_THRESHOLD_MS)

    /** All events as a Flow, newest first, max 100. */
    fun getAllEvents(): Flow<List<LimitEvent>> = dao.getAllEvents()

    /** One-shot snapshot of all events for export. */
    suspend fun getAllEventsOnce(): List<LimitEvent> = dao.getAllEventsOnce()

    /** Delete all limit events. */
    suspend fun clearAll() = dao.clearAll()

    /**
     * Longest consecutive run of days (ending at the most recent day that had
     * any events) where ≥ [thresholdPct] of stops were on time.
     */
    suspend fun computeCurrentSmoothStopStreak(thresholdPct: Double = 0.70): Int {
        val dailyStats = dao.getDailyStopStats(since = 0L, thresholdMs = ON_TIME_THRESHOLD_MS)
        if (dailyStats.isEmpty()) return 0

        // Walk from most-recent day backwards; stop as soon as a non-smooth day is hit.
        var streak = 0
        for (day in dailyStats.sortedByDescending { it.dayBucket }) {
            if (day.total == 0) continue
            val pct = day.onTime.toDouble() / day.total
            if (pct >= thresholdPct) streak++ else break
        }
        return streak
    }
}