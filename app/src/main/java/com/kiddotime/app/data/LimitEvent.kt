package com.kiddotime.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "limit_events")
data class LimitEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val appName: String,
    val limitReachedAt: Long,       // System.currentTimeMillis() when limit fired
    val appClosedAt: Long? = null   // null until overlay is dismissed
)
