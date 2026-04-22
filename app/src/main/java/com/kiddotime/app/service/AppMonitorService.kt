package com.kiddotime.app.service

import android.app.Service
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
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
    private lateinit var windowManager: WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private var warningOverlayView: FrameLayout? = null

    // Track which apps have already triggered the limit this session
    // so we don't spam the overlay repeatedly
    private val triggeredApps = mutableSetOf<String>()
    private val halfTimeWarningShown = mutableSetOf<String>()
    private val eighthTimeWarningShown = mutableSetOf<String>()

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
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
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
            dismissWarning()
            isOverlayShowing.set(true)
            triggeredApps.add(currentPackage)
            lockedApps.add(currentPackage)
            lockedApps_names[currentPackage] = limit.appName
            broadcastLimitReached(currentPackage, limit.appName)
        } else if (usageMs < limit.dailyLimitMs) {
            val remainingMs = limit.dailyLimitMs - usageMs
            val eighthThreshold = limit.dailyLimitMs * 7 / 8
            val halfThreshold = limit.dailyLimitMs / 2

            if (usageMs >= eighthThreshold && currentPackage !in eighthTimeWarningShown) {
                Log.d("KiddoTime", "One-eighth time warning for $currentPackage (${remainingMs}ms left)")
                eighthTimeWarningShown.add(currentPackage)
                halfTimeWarningShown.add(currentPackage) // prevent double-warn
                showTimeWarning(limit.appName, remainingMs)
            } else if (usageMs >= halfThreshold && currentPackage !in halfTimeWarningShown) {
                Log.d("KiddoTime", "Half time warning for $currentPackage (${remainingMs}ms left)")
                halfTimeWarningShown.add(currentPackage)
                showTimeWarning(limit.appName, remainingMs)
            }

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
                    halfTimeWarningShown.remove(pkg)
                    eighthTimeWarningShown.remove(pkg)
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

    private fun formatTimeRemaining(ms: Long): String {
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return when {
            minutes >= 60 -> "${minutes / 60}h ${minutes % 60}m"
            minutes > 0 -> "$minutes minute${if (minutes == 1L) "" else "s"}"
            else -> "$seconds second${if (seconds == 1L) "" else "s"}"
        }
    }

    private fun showTimeWarning(appName: String, remainingMs: Long) {
        mainHandler.post {
            if (warningOverlayView != null) dismissWarning()

            val ctx = applicationContext
            val container = FrameLayout(ctx).apply {
                setBackgroundColor(Color.argb(180, 0, 0, 0))
                isClickable = true
                isFocusable = true
            }

            val card = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setBackgroundColor(Color.rgb(25, 35, 75))
                setPadding(72, 64, 72, 64)
            }

            card.addView(TextView(ctx).apply {
                text = "⏰"
                textSize = 52f
                gravity = Gravity.CENTER
            })

            card.addView(TextView(ctx).apply {
                text = "Time Warning"
                textSize = 22f
                setTextColor(Color.WHITE)
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                gravity = Gravity.CENTER
                setPadding(0, 20, 0, 12)
            })

            card.addView(TextView(ctx).apply {
                text = "You have ${formatTimeRemaining(remainingMs)} left\nfor $appName"
                textSize = 17f
                setTextColor(Color.argb(220, 200, 220, 255))
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, 40)
            })

            card.addView(Button(ctx).apply {
                text = "OK, Got It!"
                textSize = 16f
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.rgb(60, 100, 210))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { gravity = Gravity.CENTER_HORIZONTAL }
                setOnClickListener { dismissWarning() }
            })

            container.addView(card, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            ).apply { setMargins(64, 0, 64, 0) })

            warningOverlayView = container

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = 0
                y = 0
            }

            try {
                windowManager.addView(container, params)
                Log.d("KiddoTime", "Time warning shown for $appName (${formatTimeRemaining(remainingMs)} left)")
            } catch (e: Exception) {
                Log.e("KiddoTime", "Failed to show time warning: ${e.message}")
                warningOverlayView = null
            }
        }
    }

    private fun dismissWarning() {
        mainHandler.post {
            warningOverlayView?.let {
                try {
                    windowManager.removeView(it)
                } catch (e: Exception) {
                    Log.e("KiddoTime", "Failed to dismiss warning: ${e.message}")
                }
                warningOverlayView = null
            }
        }
    }

    private fun broadcastLimitReached(packageName: String, appName: String) {
        Log.d("KiddoTime", "Calling OverlayService.start with appName=$appName, package=$packageName")
        OverlayService.start(this, appName, packageName)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        dismissWarning()
    }
}