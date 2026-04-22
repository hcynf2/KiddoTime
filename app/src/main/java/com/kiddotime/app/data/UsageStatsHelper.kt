package com.kiddotime.app.data

import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.util.Log
import android.content.pm.PackageManager
import java.util.Calendar

data class AppUsageInfo(
    val packageName: String,
    val appName: String,
    val totalTimeMs: Long,
    val appIcon: android.graphics.drawable.Drawable?
)

class UsageStatsHelper(private val context: Context) {

    // Check if we have permission to read usage stats
    fun hasUsagePermission(): Boolean {
        return try {
            val usageStatsManager =
                context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val now = System.currentTimeMillis()
            val stats = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                now - 1000 * 60 * 60 * 24, // last 24 hours
                now
            )
            Log.d("KiddoTime", "Permission check - stats size: ${stats?.size}")
            stats != null && stats.isNotEmpty()
        } catch (e: Exception) {
            Log.e("KiddoTime", "Permission check failed: ${e.message}")
            false
        }
    }

    // Get today's usage stats for all apps
    fun getTodayUsageStats(): List<AppUsageInfo> {
        val usageStatsManager =
            context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val packageManager = context.packageManager

        // Get start of today
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        // Get usage stats for today
        val usageStats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            calendar.timeInMillis,
            System.currentTimeMillis()
        ) ?: emptyList()

        // Map usage stats by package name for quick lookup
        val usageMap = usageStats.associateBy { it.packageName }

        // Get ALL installed apps on the device
        val installedApps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
        Log.d("KiddoTime", "Total installed apps: ${installedApps.size}")

        val launchableApps = installedApps.filter { appInfo ->
            packageManager.getLaunchIntentForPackage(appInfo.packageName) != null
        }
        Log.d("KiddoTime", "Launchable apps: ${launchableApps.size}")

        val usageApps = usageStats.size
        Log.d("KiddoTime", "Apps with usage stats: $usageApps")

        return installedApps
            .filter { appInfo ->
                // Filter out system apps that aren't launchable
                packageManager.getLaunchIntentForPackage(appInfo.packageName) != null
            }
            .mapNotNull { appInfo ->
                try {
                    val appName = packageManager.getApplicationLabel(appInfo).toString()
                    val appIcon = packageManager.getApplicationIcon(appInfo)
                    val usageMs = usageMap[appInfo.packageName]?.totalTimeInForeground ?: 0L
                    AppUsageInfo(
                        packageName = appInfo.packageName,
                        appName = appName,
                        totalTimeMs = usageMs,
                        appIcon = appIcon
                    )
                } catch (e: PackageManager.NameNotFoundException) {
                    null
                }
            }
            .sortedWith(
                // Sort: apps with usage first (by time), then unused apps alphabetically
                compareByDescending<AppUsageInfo> { it.totalTimeMs }
                    .thenBy { it.appName }
            )
    }

    // Convert milliseconds to a readable format e.g. "1h 23m"
    fun formatDuration(ms: Long): String {
        val totalSeconds = ms / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60

        return when {
            hours > 0 -> "${hours}h ${minutes}m"
            minutes > 0 -> "${minutes}m"
            else -> "< 1m"
        }
    }

    /** Total screen time for yesterday (midnight-to-midnight). */
    fun getYesterdayTotal(): Long {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val startOfToday = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val startOfYesterday = startOfToday - 24 * 60 * 60 * 1000L
        return usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY, startOfYesterday, startOfToday
        )?.sumOf { it.totalTimeInForeground } ?: 0L
    }

    /**
     * Average total daily screen time over the last 7 complete days.
     * Groups INTERVAL_DAILY entries by their day bucket and averages across days
     * that had any activity.
     */
    fun get7DayDailyAverage(): Long {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val startOfToday = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val sevenDaysAgo = startOfToday - 7 * 24 * 60 * 60 * 1000L
        val stats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY, sevenDaysAgo, startOfToday
        ) ?: return 0L
        val msPerDay = 24L * 60 * 60 * 1000
        val dailyTotals = stats
            .groupBy { it.firstTimeStamp / msPerDay }
            .mapValues { (_, list) -> list.sumOf { it.totalTimeInForeground } }
        return if (dailyTotals.isNotEmpty()) dailyTotals.values.sum() / dailyTotals.size else 0L
    }

    /**
     * Returns (packageName, durationMs) of the single longest unbroken app session today,
     * derived from ACTIVITY_RESUMED / ACTIVITY_PAUSED events.
     */
    fun getLongestSessionToday(): Pair<String, Long>? {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val startOfDay = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val now = System.currentTimeMillis()
        val events = usageStatsManager.queryEvents(startOfDay, now)
        val event = android.app.usage.UsageEvents.Event()
        val resumeMap = mutableMapOf<String, Long>()
        val longestMap = mutableMapOf<String, Long>()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            when (event.eventType) {
                android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED ->
                    resumeMap[event.packageName] = event.timeStamp
                android.app.usage.UsageEvents.Event.ACTIVITY_PAUSED -> {
                    val start = resumeMap.remove(event.packageName) ?: continue
                    val dur = event.timeStamp - start
                    if (dur > (longestMap[event.packageName] ?: 0L))
                        longestMap[event.packageName] = dur
                }
            }
        }
        // Include any currently open app
        resumeMap.forEach { (pkg, start) ->
            val dur = now - start
            if (dur > (longestMap[pkg] ?: 0L)) longestMap[pkg] = dur
        }
        val pm = context.packageManager
        return longestMap
            .filter { pm.getLaunchIntentForPackage(it.key) != null }
            .maxByOrNull { it.value }
            ?.toPair()
    }

    /**
     * Returns (packageName, totalMs) of the most-used launchable app over the last 7 days
     * (including today).
     */
    fun getWeeklyTopApp(): Pair<String, Long>? {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val startOfToday = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val sevenDaysAgo = startOfToday - 7 * 24 * 60 * 60 * 1000L
        val stats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY, sevenDaysAgo, System.currentTimeMillis()
        ) ?: return null
        val pm = context.packageManager
        return stats
            .filter { pm.getLaunchIntentForPackage(it.packageName) != null && it.totalTimeInForeground > 0 }
            .groupBy { it.packageName }
            .mapValues { (_, list) -> list.sumOf { it.totalTimeInForeground } }
            .maxByOrNull { it.value }
            ?.toPair()
    }
}