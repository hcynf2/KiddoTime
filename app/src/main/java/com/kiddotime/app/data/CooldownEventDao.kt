package com.kiddotime.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

data class WhatNextChoiceCount(val whatNextChoice: String, val count: Int)

@Dao
interface CooldownEventDao {

    @Insert
    suspend fun insert(event: CooldownEvent): Long

    @Query("UPDATE cooldown_events SET completedAt = :completedAt, whatNextChoice = :whatNextChoice WHERE id = :id")
    suspend fun markCompleted(id: Long, completedAt: Long, whatNextChoice: String?)

    @Query("SELECT COUNT(*) FROM cooldown_events")
    suspend fun getTotalStarted(): Int

    @Query("SELECT COUNT(*) FROM cooldown_events WHERE completedAt IS NOT NULL")
    suspend fun getTotalCompleted(): Int

    @Query("SELECT COUNT(*) FROM cooldown_events WHERE gameType = :gameType")
    suspend fun getStartedByType(gameType: String): Int

    @Query("SELECT COUNT(*) FROM cooldown_events WHERE gameType = :gameType AND completedAt IS NOT NULL")
    suspend fun getCompletedByType(gameType: String): Int

    @Query("""
        SELECT whatNextChoice, COUNT(*) as count
        FROM cooldown_events
        WHERE gameType = 'whatnext' AND whatNextChoice IS NOT NULL
        GROUP BY whatNextChoice
        ORDER BY count DESC
    """)
    suspend fun getWhatNextChoiceCounts(): List<WhatNextChoiceCount>

    @Query("DELETE FROM cooldown_events")
    suspend fun clearAll()
}