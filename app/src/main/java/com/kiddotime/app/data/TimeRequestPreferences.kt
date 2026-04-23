package com.kiddotime.app.data

import android.content.Context

class TimeRequestPreferences(context: Context) {

    private val prefs = context.getSharedPreferences("kiddotime_time_request_prefs", Context.MODE_PRIVATE)

    /** Whether children are allowed to submit time-extension requests. Defaults to true. */
    var enabled: Boolean
        get() = prefs.getBoolean("requests_enabled", true)
        set(v) { prefs.edit().putBoolean("requests_enabled", v).apply() }
}