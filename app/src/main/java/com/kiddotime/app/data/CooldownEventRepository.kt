package com.kiddotime.app.data

class CooldownEventRepository(private val dao: CooldownEventDao) {

    suspend fun recordStart(packageName: String, appName: String, gameType: String): Long =
        dao.insert(
            CooldownEvent(
                packageName = packageName,
                appName = appName,
                gameType = gameType,
                startedAt = System.currentTimeMillis()
            )
        )

    suspend fun recordComplete(id: Long, completedAt: Long, whatNextChoice: String? = null) {
        dao.markCompleted(id, completedAt, whatNextChoice)
    }

    suspend fun getTotalStarted(): Int = dao.getTotalStarted()
    suspend fun getTotalCompleted(): Int = dao.getTotalCompleted()

    suspend fun getStartedByType(gameType: String): Int = dao.getStartedByType(gameType)
    suspend fun getCompletedByType(gameType: String): Int = dao.getCompletedByType(gameType)

    /** Returns a map of activity label → pick count, already sorted descending by count. */
    suspend fun getWhatNextChoiceCounts(): Map<String, Int> =
        dao.getWhatNextChoiceCounts().associate { it.whatNextChoice to it.count }

    suspend fun clearAll() = dao.clearAll()
}