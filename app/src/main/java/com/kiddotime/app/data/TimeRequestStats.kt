package com.kiddotime.app.data

data class TimeRequestStats(
    val totalRequests: Int = 0,
    val totalApproved: Int = 0,
    val totalDenied: Int = 0,
    val avgResponseMs: Long? = null,
    val mostRequestedApp: String = ""
) {
    val approvalRate: Float
        get() {
            val resolved = totalApproved + totalDenied
            return if (resolved > 0) totalApproved.toFloat() / resolved else 0f
        }
}
