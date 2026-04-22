package com.kiddotime.app.data

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