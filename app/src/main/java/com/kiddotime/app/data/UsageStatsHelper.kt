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
}