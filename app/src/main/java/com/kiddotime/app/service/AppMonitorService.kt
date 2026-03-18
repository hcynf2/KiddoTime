package com.kiddotime.app.service

import android.app.Service
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.kiddotime.app.data.AppDatabase
import com.kiddotime.app.data.AppLimitRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

class AppMonitorService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var repository: AppLimitRepository

    // Track which apps have already triggered the limit this session
    // so we don't spam the overlay repeatedly
    private val triggeredApps = mutableSetOf<String>()

    companion object {
        const val ACTION_LIMIT_REACHED = "com.kiddotime.app.LIMIT_REACHED"
        const val EXTRA_PACKAGE_NAME = "package_name"
        const val EXTRA_APP_NAME = "app_name"
        private const val POLL_INTERVAL_MS = 3000L // Check every 3 seconds

        fun start(context: Context) {
            val intent = Intent(context, AppMonitorService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, AppMonitorService::class.java)
            context.stopService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        repository = AppLimitRepository(
            AppDatabase.getDatabase(applicationContext).appLimitDao()
        )
        NotificationHelper.createNotificationChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Start as foreground service with persistent notification
        startForeground(
            NotificationHelper.NOTIFICATION_ID,
            NotificationHelper.buildNotification(this)
        )
        startMonitoring()
        return START_STICKY // Restart automatically if killed by system
    }

    private fun startMonitoring() {
        serviceScope.launch {
            while (isActive) {
                checkCurrentApp()
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    private suspend fun checkCurrentApp() {

        val currentPackage = getForegroundApp()
        Log.d("KiddoTime", "Foreground app: $currentPackage")

        if (currentPackage == null) {
            Log.d("KiddoTime", "Could not detect foreground app")
            return
        }

        if (currentPackage == packageName) return

        val limits = repository.allLimits.first()
        Log.d("KiddoTime", "Total limits set: ${limits.size}")
        limits.forEach {
            Log.d("KiddoTime", "Stored limit: ${it.packageName} = ${it.dailyLimitMs}ms")
        }

        val limit = limits.find { it.packageName == currentPackage }
        Log.d("KiddoTime", "Limit for $currentPackage: ${limit?.dailyLimitMs}ms")

        if (limit == null) return

        val usageMs = getAppUsageToday(currentPackage)
        Log.d("KiddoTime", "Usage for $currentPackage: ${usageMs}ms / limit: ${limit.dailyLimitMs}ms")

       // if (usageMs >= limit.dailyLimitMs && currentPackage !in triggeredApps) {
        if (usageMs >= limit.dailyLimitMs){
            Log.d("KiddoTime", "LIMIT REACHED - broadcasting for $currentPackage")
             triggeredApps.add(currentPackage)
            broadcastLimitReached(currentPackage, limit.appName)
        } else {
            Log.d("KiddoTime", "Not triggering: usageMs=$usageMs, limit=${limit.dailyLimitMs}, alreadyTriggered=${currentPackage in triggeredApps}")
        }

        if (usageMs < limit.dailyLimitMs) {
           triggeredApps.remove(currentPackage)
        }
    }


    private fun getForegroundApp(): String? {
        val usageStatsManager =
            getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()

        // Query a longer window - last 60 seconds instead of 5
        val stats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_BEST,
            now - 60000,
            now
        )

        if (stats.isNullOrEmpty()) {
            Log.d("KiddoTime", "Usage stats empty - permission may not be granted")
            return null
        }

        val foreground = stats
            .filter { it.totalTimeInForeground > 0 }
            .maxByOrNull { it.lastTimeUsed }

        Log.d("KiddoTime", "Most recent app: ${foreground?.packageName}, lastUsed: ${foreground?.lastTimeUsed}")
        return foreground?.packageName
    }

    private fun getAppUsageToday(packageName: String): Long {
        val usageStatsManager =
            getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

        val startOfDay = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis

        val now = System.currentTimeMillis()

        // Use queryEvents instead of queryUsageStats for Samsung compatibility
        val events = usageStatsManager.queryEvents(startOfDay, now)
        val event = android.app.usage.UsageEvents.Event()

        var totalTime = 0L
        var lastResumeTime = -1L

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.packageName != packageName) continue

            when (event.eventType) {
                android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED -> {
                    lastResumeTime = event.timeStamp
                }
                android.app.usage.UsageEvents.Event.ACTIVITY_PAUSED -> {
                    if (lastResumeTime != -1L) {
                        totalTime += event.timeStamp - lastResumeTime
                        lastResumeTime = -1L
                    }
                }
            }
        }

        // If app is currently open, add time since last resume
        if (lastResumeTime != -1L) {
            totalTime += now - lastResumeTime
        }

        Log.d("KiddoTime", "Event-based usage for $packageName: ${totalTime}ms")
        return totalTime
    }

    private fun broadcastLimitReached(packageName: String, appName: String) {
        // Send a broadcast that MainActivity will listen for
        Log.d("KiddoTime", "Sending broadcast for $packageName")
        val intent = Intent(ACTION_LIMIT_REACHED).apply {
            putExtra(EXTRA_PACKAGE_NAME, packageName)
            putExtra(EXTRA_APP_NAME, appName)
            setPackage(this@AppMonitorService.packageName)
        }
        sendBroadcast(intent)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}