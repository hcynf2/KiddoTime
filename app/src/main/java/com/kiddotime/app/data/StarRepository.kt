package com.kiddotime.app.data

import android.content.Context

class StarRepository(context: Context) {

    private val prefs = context.getSharedPreferences("kiddotime_rewards", Context.MODE_PRIVATE)

    var balance: Int
        get() = prefs.getInt("star_balance", 0)
        set(value) { prefs.edit().putInt("star_balance", value).apply() }

    var pendingCelebration: Boolean
        get() = prefs.getBoolean("pending_celebration", false)
        set(value) { prefs.edit().putBoolean("pending_celebration", value).apply() }

    fun addStar() {
        balance += 1
        pendingCelebration = true
    }

    fun clearAll() {
        prefs.edit().clear().apply()
    }
}