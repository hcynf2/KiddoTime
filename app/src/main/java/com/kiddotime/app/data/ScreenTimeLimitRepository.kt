package com.kiddotime.app.data

import android.content.Context

class ScreenTimeLimitRepository(context: Context) {

    private val prefs = context.getSharedPreferences("kiddotime_screen_limit", Context.MODE_PRIVATE)

    var capMs: Long
        get() = prefs.getLong("total_cap_ms", 0L)
        set(value) { prefs.edit().putLong("total_cap_ms", value).apply() }
}