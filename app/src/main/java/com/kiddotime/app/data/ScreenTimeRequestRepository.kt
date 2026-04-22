package com.kiddotime.app.data

import kotlinx.coroutines.flow.Flow

class ScreenTimeRequestRepository(private val dao: ScreenTimeRequestDao) {

    fun getPendingRequests(): Flow<List<ScreenTimeRequest>> = dao.getPendingRequests()

    suspend fun submitRequest(
        packageName: String,
        appName: String,
        extraMs: Long = 30 * 60 * 1000L
    ) {
        dao.insert(
            ScreenTimeRequest(
                packageName = packageName,
                appName = appName,
                requestedAt = System.currentTimeMillis(),
                extraMs = extraMs
            )
        )
    }

    suspend fun approve(requestId: Long) = dao.updateStatus(requestId, "APPROVED")

    suspend fun deny(requestId: Long) = dao.updateStatus(requestId, "DENIED")

    suspend fun clearAll() = dao.clearAll()
}