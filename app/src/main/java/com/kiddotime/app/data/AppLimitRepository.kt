package com.kiddotime.app.data

import kotlinx.coroutines.flow.Flow

class AppLimitRepository(private val dao: AppLimitDao) {

    val allLimits: Flow<List<AppLimit>> = dao.getAllLimits()

    suspend fun setLimit(packageName: String, appName: String, limitMs: Long) {
        dao.upsertLimit(AppLimit(packageName, appName, limitMs))
    }

    suspend fun removeLimit(packageName: String, appName: String) {
        dao.deleteLimit(AppLimit(packageName, appName, 0L))
    }

    suspend fun getLimitForApp(packageName: String): AppLimit? {
        return dao.getLimitForApp(packageName)
    }
}