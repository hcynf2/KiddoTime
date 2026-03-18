package com.kiddotime.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AppLimitDao {
    //Get all limits as a live stream (updates UI automatically when data changes)
    @Query("SELECT * FROM app_limits")
    fun getAllLimits(): Flow<List<AppLimit>>

    // Get limit for a specific app
    @Query("SELECT * FROM app_limits WHERE packageName = :packageName")
    suspend fun getLimitForApp(packageName: String): AppLimit?

    //insert or update a limit
    @Upsert
    suspend fun upsertLimit(appLimit: AppLimit)

    // remove a limit
    @Delete
    suspend fun deleteLimit(appLimit: AppLimit)

}