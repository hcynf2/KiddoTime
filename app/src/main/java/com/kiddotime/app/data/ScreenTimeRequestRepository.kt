package com.kiddotime.app.data

import kotlinx.coroutines.flow.Flow
import java.util.Calendar

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

    suspend fun approve(requestId: Long) =
        dao.updateStatus(requestId, "APPROVED", System.currentTimeMillis())

    suspend fun deny(requestId: Long) =
        dao.updateStatus(requestId, "DENIED", System.currentTimeMillis())

    /** Returns how many requests have been submitted today (midnight boundary). */
    suspend fun countTodayRequests(): Int {
        val startOfDay = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        return dao.countTodayRequests(startOfDay)
    }

    suspend fun buildStats(): TimeRequestStats {
        val total    = dao.getTotalRequests()
        val approved = dao.getTotalApproved()
        val denied   = dao.getTotalDenied()
        val avgMs    = dao.getAvgResponseMs()?.toLong()
        val topApp   = dao.getMostRequestedApp() ?: ""
        return TimeRequestStats(
            totalRequests    = total,
            totalApproved    = approved,
            totalDenied      = denied,
            avgResponseMs    = avgMs,
            mostRequestedApp = topApp
        )
    }

    suspend fun clearAll() = dao.clearAll()
}