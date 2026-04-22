package com.kiddotime.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "screen_time_requests")
data class ScreenTimeRequest(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val appName: String,
    val requestedAt: Long,
    val status: String = "PENDING",
    val extraMs: Long = 30 * 60 * 1000L
)