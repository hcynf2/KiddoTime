package com.kiddotime.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cooldown_events")
data class CooldownEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val appName: String,
    val gameType: String,              // "card" | "cleanup" | "whatnext"
    val startedAt: Long,
    val completedAt: Long? = null,     // null = abandoned / still in progress
    val whatNextChoice: String? = null // only populated for GAME_WHAT_NEXT
)