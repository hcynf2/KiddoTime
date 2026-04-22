package com.kiddotime.app.data

import android.content.Context
import java.util.Calendar

class BedtimeRepository(context: Context) {

    private val prefs = context.getSharedPreferences("bedtime_prefs", Context.MODE_PRIVATE)

    var isEnabled: Boolean
        get() = prefs.getBoolean("enabled", false)
        set(value) { prefs.edit().putBoolean("enabled", value).apply() }

    var hour: Int
        get() = prefs.getInt("hour", 21)           // default 9 PM
        set(value) { prefs.edit().putInt("hour", value).apply() }

    var minute: Int
        get() = prefs.getInt("minute", 0)
        set(value) { prefs.edit().putInt("minute", value).apply() }

    /** Package names of apps that should be locked at bedtime. */
    fun getApps(): Set<String> =
        prefs.getStringSet("apps", emptySet()) ?: emptySet()

    fun setApps(packages: Set<String>) {
        prefs.edit().putStringSet("apps", packages).apply()
    }

    // ── Lock persistence (survives service restarts) ──────────────────────────

    var isLockActive: Boolean
        get() = prefs.getBoolean("lock_active", false)
        set(value) { prefs.edit().putBoolean("lock_active", value).apply() }

    /** "YYYY-MM-DD" of the day the bedtime lock was last triggered. */
    var lockDate: String
        get() = prefs.getString("lock_date", "") ?: ""
        set(value) { prefs.edit().putString("lock_date", value).apply() }

    /**
     * Returns minutes from now until TODAY's bedtime (negative = already passed).
     * Does not wrap to the next day.
     */
    fun minutesUntilBedtimeToday(): Long {
        val now = Calendar.getInstance()
        val bedtime = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return (bedtime.timeInMillis - now.timeInMillis) / 60_000L
    }
}