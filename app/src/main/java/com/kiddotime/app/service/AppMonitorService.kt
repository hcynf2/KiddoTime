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
import com.kiddotime.app.overlay.OverlayService

class AppMonitorService : Service() {

private val isOverlayShowing = java.util.concurrent.atomic.AtomicBoolean(false)

    private val unlockedApps = mutableSetOf<String>()
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var repository: AppLimitRepository

    // Track which apps have already triggered the limit this session
    // so we don't spam the overlay repeatedly
    private val triggeredApps = mutableSetOf<String>()

    companion object {
        const val ACTION_LIMIT_REACHED = "com.kiddotime.app.LIMIT_REACHED"
        const val EXTRA_PACKAGE_NAME = "package_name"
        const val EXTRA_APP_NAME = "app_name"
        private const val POLL_INTERVAL_MS = 1000L // Check every 1 second

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
        val filter = android.content.IntentFilter().apply {
            addAction("com.kiddotime.app.OVERLAY_DISMISSED")
            addAction("com.kiddotime.app.APP_UNLOCKED")
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(overlayDismissedReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(overlayDismissedReceiver, filter)
        }
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
        serviceScope.launch(Dispatchers.IO) {
            while (isActive) {
                try {
                    checkCurrentApp()
                } catch (e: Exception) {
                    Log.e("KiddoTime", "Monitor error: ${e.message}")
                }
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    private suspend fun checkCurrentApp() {
        val currentPackage = getForegroundApp() ?: return
        if (currentPackage == packageName) return

        // If app is locked and child tries to open it, show lock screen immediately
        if (currentPackage in lockedApps && !isOverlayShowing.get()) {
            Log.d("KiddoTime", "Locked app opened - showing lock screen: $currentPackage")
            isOverlayShowing.set(true)
            val appName = lockedApps_names[currentPackage] ?: currentPackage
            OverlayService.startLockScreen(this, appName, currentPackage)
            return
        }

        // Skip monitoring if already showing overlay or app is unlocked
        if (isOverlayShowing.get()) return
        if (currentPackage in unlockedApps) return
        if (currentPackage in lockedApps) return

        // Get limit for current app
        val limits = repository.allLimits.first()
        val limit = limits.find { it.packageName == currentPackage } ?: return

        // Get usage
        val usageMs = getAppUsageToday(currentPackage)
        Log.d("KiddoTime", "Usage for $currentPackage: ${usageMs}ms / limit: ${limit.dailyLimitMs}ms")

        if (usageMs >= limit.dailyLimitMs && currentPackage !in triggeredApps) {
            Log.d("KiddoTime", "LIMIT REACHED - launching game for $currentPackage")
            isOverlayShowing.set(true)
            triggeredApps.add(currentPackage)
            lockedApps.add(currentPackage)
            lockedApps_names[currentPackage] = limit.appName
            broadcastLimitReached(currentPackage, limit.appName)
        } else if (usageMs < limit.dailyLimitMs) {
            triggeredApps.remove(currentPackage)
            unlockedApps.remove(currentPackage)
        }
    }

    // Store app names for locked apps
    private val lockedApps_names = mutableMapOf<String, String>()

    private val overlayDismissedReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context, intent: android.content.Intent) {
            when (intent.action) {
                "com.kiddotime.app.OVERLAY_DISMISSED" -> {
                    Log.d("KiddoTime", "Overlay dismissed - resetting")
                    triggeredApps.clear()
                    isOverlayShowing.set(false)
                }
                "com.kiddotime.app.APP_UNLOCKED" -> {
                    val pkg = intent.getStringExtra("package_name") ?: return
                    Log.d("KiddoTime", "App unlocked: $pkg")
                    lockedApps.remove(pkg)
                    lockedApps_names.remove(pkg)
                    triggeredApps.remove(pkg)
                    unlockedApps.add(pkg)
                    isOverlayShowing.set(false)
                }
            }
        }
    }

    private val lockedApps = mutableSetOf<String>()

    private fun getForegroundApp(): String? {
        val usageStatsManager =
            getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()

        // Use queryEvents for most accurate foreground detection on Samsung
        val events = usageStatsManager.queryEvents(now - 10000, now)
        val event = android.app.usage.UsageEvents.Event()

        var lastPackage: String? = null
        var lastTime = 0L

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED) {
                if (event.timeStamp > lastTime) {
                    lastTime = event.timeStamp
                    lastPackage = event.packageName
                }
            }
        }

        // Fallback to queryUsageStats if events return nothing
        if (lastPackage == null) {
            val stats = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_BEST,
                now - 10000,
                now
            )
            lastPackage = stats
                ?.filter { it.lastTimeUsed > 0 }
                ?.maxByOrNull { it.lastTimeUsed }
                ?.packageName
        }

        Log.d("KiddoTime", "Foreground app detected: $lastPackage")
        return lastPackage
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
        Log.d("KiddoTime", "Calling OverlayService.start with appName=$appName, package=$packageName")
        OverlayService.start(this, appName, packageName)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}