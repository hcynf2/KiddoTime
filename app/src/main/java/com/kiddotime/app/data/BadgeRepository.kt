package com.kiddotime.app.data

import android.content.Context

enum class BadgeId(val title: String, val emoji: String, val description: String) {
    FIRST_STOP("First Stop!", "🌱", "Stopped an app on time for the first time"),
    STREAK_3("3-Day Streak", "🔥", "3 days in a row with on-time stops"),
    STREAK_7("Week Champion", "🏆", "7 days in a row with on-time stops"),
    PERFECT_DAY("Perfect Day", "⭐", "All stops on time in a single day"),
    STAR_10("Star Collector", "💫", "Earned 10 stars"),
    STAR_25("Star Champion", "✨", "Earned 25 stars")
}

class BadgeRepository(context: Context) {

    private val prefs = context.getSharedPreferences("kiddotime_badges", Context.MODE_PRIVATE)

    private var earnedIds: Set<String>
        get() = prefs.getStringSet("earned", emptySet()) ?: emptySet()
        set(value) { prefs.edit().putStringSet("earned", value).apply() }

    private var seenIds: Set<String>
        get() = prefs.getStringSet("seen", emptySet()) ?: emptySet()
        set(value) { prefs.edit().putStringSet("seen", value).apply() }

    fun getEarnedBadges(): List<BadgeId> =
        BadgeId.values().filter { it.name in earnedIds }

    fun getNewBadges(): List<BadgeId> {
        val earned = earnedIds
        val seen = seenIds
        return BadgeId.values().filter { it.name in earned && it.name !in seen }
    }

    fun markAllSeen() {
        seenIds = earnedIds.toSet()
    }

    fun clearAll() {
        prefs.edit().clear().apply()
    }

    suspend fun evaluate(limitRepo: LimitEventRepository, starBalance: Int) {
        val current = earnedIds.toMutableSet()

        val totalOnTime = limitRepo.getTotalOnTimeCount()
        val streak = limitRepo.computeCurrentSmoothStopStreak()
        val todayStats = limitRepo.getTodayStats()

        if (totalOnTime > 0) current.add(BadgeId.FIRST_STOP.name)
        if (streak >= 3) current.add(BadgeId.STREAK_3.name)
        if (streak >= 7) current.add(BadgeId.STREAK_7.name)
        if (todayStats.total > 0 && todayStats.onTime == todayStats.total)
            current.add(BadgeId.PERFECT_DAY.name)
        if (starBalance >= 10) current.add(BadgeId.STAR_10.name)
        if (starBalance >= 25) current.add(BadgeId.STAR_25.name)

        earnedIds = current
    }
}