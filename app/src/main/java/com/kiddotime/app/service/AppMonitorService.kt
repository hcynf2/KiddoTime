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
import com.kiddotime.app.data.BedtimeRepository
import com.kiddotime.app.overlay.OverlayService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.*

class AppMonitorService : Service() {

    private val isOverlayShowing = java.util.concurrent.atomic.AtomicBoolean(false)

    private val unlockedApps = mutableSetOf<String>()
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var repository: AppLimitRepository
    private lateinit var bedtimeRepo: BedtimeRepository
    private lateinit var limitEventRepo: com.kiddotime.app.data.LimitEventRepository
    private lateinit var starRepo: com.kiddotime.app.data.StarRepository

    // Tracks when each package's limit fired this session (packageName → timestamp).
    // Used to determine on-time eligibility when the overlay is dismissed.
    private val limitFiredAt = mutableMapOf<String, Long>()
    private lateinit var windowManager: WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private var warningOverlayView: FrameLayout? = null

    // Per-app time-limit tracking
    private val triggeredApps = mutableSetOf<String>()
    private val halfTimeWarningShown = mutableSetOf<String>()
    private val eighthTimeWarningShown = mutableSetOf<String>()

    // Bedtime tracking (in-memory; persisted via BedtimeRepository for restarts)
    private var bedtimeLockActive = false
    private var bedtimeWarning30Shown = false
    private var bedtimeWarning10Shown = false
    private var bedtimeWarningFlagDate = "" // reset warnings each new day

    companion object {
        const val ACTION_LIMIT_REACHED = "com.kiddotime.app.LIMIT_REACHED"
        const val EXTRA_PACKAGE_NAME = "package_name"
        const val EXTRA_APP_NAME = "app_name"
        private const val POLL_INTERVAL_MS = 1000L

        fun start(context: Context) {
            context.startForegroundService(Intent(context, AppMonitorService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, AppMonitorService::class.java))
        }
    }

    override fun onCreate() {
        super.onCreate()
        val db = AppDatabase.getDatabase(applicationContext)
        repository = AppLimitRepository(db.appLimitDao())
        limitEventRepo = com.kiddotime.app.data.LimitEventRepository(db.limitEventDao())
        starRepo = com.kiddotime.app.data.StarRepository(applicationContext)
        bedtimeRepo = BedtimeRepository(applicationContext)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        NotificationHelper.createNotificationChannel(this)

        // Restore bedtime lock state from SharedPreferences (across service restarts)
        bedtimeLockActive = bedtimeRepo.isLockActive

        val filter = android.content.IntentFilter().apply {
            addAction("com.kiddotime.app.OVERLAY_DISMISSED")
            addAction("com.kiddotime.app.APP_UNLOCKED")
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(overlayDismissedReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(overlayDismissedReceiver, filter)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NotificationHelper.NOTIFICATION_ID, NotificationHelper.buildNotification(this))
        startMonitoring()
        return START_STICKY
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

    // ── Main polling loop ─────────────────────────────────────────────────────

    private suspend fun checkCurrentApp() {
        val currentPackage = getForegroundApp() ?: return
        if (currentPackage == packageName) return

        // 1. Update bedtime state (warnings + lock trigger)
        checkBedtimeStatus(currentPackage)

        // 2. If bedtime lock is active, lock any selected app not yet unlocked
        if (bedtimeLockActive && !isOverlayShowing.get() && currentPackage !in unlockedApps) {
            val bedtimeApps = bedtimeRepo.getApps()
            if (currentPackage in bedtimeApps) {
                Log.d("KiddoTime", "Bedtime lock — showing lock screen for $currentPackage")
                isOverlayShowing.set(true)
                val appName = lockedApps_names[currentPackage] ?: getAppNameFromPackage(currentPackage)
                OverlayService.startLockScreen(this, appName, currentPackage)
                return
            }
        }

        // 3. If app is locked (from a reached time-limit), show lock screen
        if (currentPackage in lockedApps && !isOverlayShowing.get()) {
            Log.d("KiddoTime", "Locked app opened — showing lock screen: $currentPackage")
            isOverlayShowing.set(true)
            val appName = lockedApps_names[currentPackage] ?: currentPackage
            OverlayService.startLockScreen(this, appName, currentPackage)
            return
        }

        if (isOverlayShowing.get()) return
        if (currentPackage in unlockedApps) return
        if (currentPackage in lockedApps) return

        // 4. Per-app time-limit check
        val limits = repository.allLimits.first()
        val limit = limits.find { it.packageName == currentPackage } ?: return
        val usageMs = getAppUsageToday(currentPackage)
        Log.d("KiddoTime", "Usage for $currentPackage: ${usageMs}ms / limit: ${limit.dailyLimitMs}ms")

        if (usageMs >= limit.dailyLimitMs && currentPackage !in triggeredApps) {
            Log.d("KiddoTime", "LIMIT REACHED — launching game for $currentPackage")
            dismissWarning()
            isOverlayShowing.set(true)
            triggeredApps.add(currentPackage)
            lockedApps.add(currentPackage)
            lockedApps_names[currentPackage] = limit.appName
            val limitReachedAt = System.currentTimeMillis()
            limitFiredAt[currentPackage] = limitReachedAt
            serviceScope.launch {
                limitEventRepo.recordLimitReached(currentPackage, limit.appName, limitReachedAt)
            }
            broadcastLimitReached(currentPackage, limit.appName)
        } else if (usageMs < limit.dailyLimitMs) {
            val remainingMs = limit.dailyLimitMs - usageMs
            val eighthThreshold = limit.dailyLimitMs * 7 / 8
            val halfThreshold = limit.dailyLimitMs / 2

            if (usageMs >= eighthThreshold && currentPackage !in eighthTimeWarningShown) {
                Log.d("KiddoTime", "One-eighth time warning for $currentPackage")
                eighthTimeWarningShown.add(currentPackage)
                halfTimeWarningShown.add(currentPackage)
                showTimeWarning(limit.appName, remainingMs)
            } else if (usageMs >= halfThreshold && currentPackage !in halfTimeWarningShown) {
                Log.d("KiddoTime", "Half-time warning for $currentPackage")
                halfTimeWarningShown.add(currentPackage)
                showTimeWarning(limit.appName, remainingMs)
            }

            triggeredApps.remove(currentPackage)
            unlockedApps.remove(currentPackage)
        }
    }

    // ── Bedtime ───────────────────────────────────────────────────────────────

    private fun checkBedtimeStatus(currentPackage: String) {
        // If bedtime is disabled, release any active lock
        if (!bedtimeRepo.isEnabled) {
            if (bedtimeLockActive) {
                bedtimeLockActive = false
                bedtimeRepo.isLockActive = false
                Log.d("KiddoTime", "Bedtime disabled — lock released")
            }
            return
        }

        val today = todayDateString()

        // Reset warning flags on new calendar day
        if (today != bedtimeWarningFlagDate) {
            bedtimeWarningFlagDate = today
            bedtimeWarning30Shown = false
            bedtimeWarning10Shown = false
        }

        // Auto-release lock the morning after it was set (6 AM, new day)
        if (bedtimeLockActive && bedtimeRepo.lockDate != today) {
            val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            if (currentHour >= 6) {
                bedtimeLockActive = false
                bedtimeRepo.isLockActive = false
                Log.d("KiddoTime", "Bedtime lock auto-released at morning")
                return
            }
        }

        val minutesUntil = bedtimeRepo.minutesUntilBedtimeToday()

        // Trigger bedtime lock when time arrives
        if (minutesUntil <= 0 && !bedtimeLockActive) {
            Log.d("KiddoTime", "Bedtime reached — activating lock")
            bedtimeLockActive = true
            bedtimeRepo.isLockActive = true
            bedtimeRepo.lockDate = today
            // Show game overlay if the current app is in the bedtime list
            val bedtimeApps = bedtimeRepo.getApps()
            if (currentPackage in bedtimeApps && !isOverlayShowing.get()) {
                isOverlayShowing.set(true)
                val appName = getAppNameFromPackage(currentPackage)
                lockedApps.add(currentPackage)
                lockedApps_names[currentPackage] = appName
                OverlayService.start(this, appName, currentPackage)
            }
            return
        }

        // Pre-bedtime warnings (only when lock is not yet active)
        if (!bedtimeLockActive && minutesUntil > 0) {
            when {
                minutesUntil <= 10 && !bedtimeWarning10Shown -> {
                    bedtimeWarning10Shown = true
                    showBedtimeWarning(minutesUntil)
                }
                minutesUntil <= 30 && !bedtimeWarning30Shown -> {
                    bedtimeWarning30Shown = true
                    showBedtimeWarning(minutesUntil)
                }
            }
        }
    }

    private fun todayDateString(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

    private fun getAppNameFromPackage(pkg: String): String =
        try {
            packageManager.getApplicationLabel(
                packageManager.getApplicationInfo(pkg, 0)
            ).toString()
        } catch (e: Exception) { pkg }

    // ── Locked-apps registry ──────────────────────────────────────────────────

    private val lockedApps_names = mutableMapOf<String, String>()
    private val lockedApps = mutableSetOf<String>()

    private val overlayDismissedReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context, intent: android.content.Intent) {
            when (intent.action) {
                "com.kiddotime.app.OVERLAY_DISMISSED" -> {
                    Log.d("KiddoTime", "Overlay dismissed — resetting")
                    val pkg = intent.getStringExtra("package_name")
                    if (pkg != null) {
                        val closedAt = System.currentTimeMillis()
                        serviceScope.launch { limitEventRepo.recordAppClosed(pkg, closedAt) }
                        val firedAt = limitFiredAt.remove(pkg)
                        if (firedAt != null &&
                            closedAt - firedAt <= com.kiddotime.app.data.LimitEventRepository.ON_TIME_THRESHOLD_MS
                        ) {
                            starRepo.addStar()
                            Log.d("KiddoTime", "⭐ Star awarded for on-time stop: $pkg (${closedAt - firedAt}ms)")
                        }
                    }
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

    // ── Warning overlays ──────────────────────────────────────────────────────

    private fun showTimeWarning(appName: String, remainingMs: Long) {
        showWarningOverlay(
            icon    = "⏰",
            title   = "Time Warning",
            message = "You have ${formatTimeRemaining(remainingMs)} left\nfor $appName"
        )
    }

    private fun showBedtimeWarning(minutesUntil: Long) {
        val m = minutesUntil.coerceAtLeast(1L)
        showWarningOverlay(
            icon    = "🌙",
            title   = "Bedtime Soon",
            message = "Bedtime in $m minute${if (m == 1L) "" else "s"}\nSelected apps will be locked"
        )
    }

    private fun showWarningOverlay(icon: String, title: String, message: String) {
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
                text = icon
                textSize = 52f
                gravity = Gravity.CENTER
            })

            card.addView(TextView(ctx).apply {
                text = title
                textSize = 22f
                setTextColor(Color.WHITE)
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                gravity = Gravity.CENTER
                setPadding(0, 20, 0, 12)
            })

            card.addView(TextView(ctx).apply {
                text = message
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
                x = 0; y = 0
            }

            try {
                windowManager.addView(container, params)
                Log.d("KiddoTime", "Warning overlay shown: $title")
            } catch (e: Exception) {
                Log.e("KiddoTime", "Failed to show warning overlay: ${e.message}")
                warningOverlayView = null
            }
        }
    }

    private fun dismissWarning() {
        mainHandler.post {
            warningOverlayView?.let {
                try { windowManager.removeView(it) }
                catch (e: Exception) { Log.e("KiddoTime", "Failed to dismiss warning: ${e.message}") }
                warningOverlayView = null
            }
        }
    }

    // ── Usage helpers ─────────────────────────────────────────────────────────

    private fun formatTimeRemaining(ms: Long): String {
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return when {
            minutes >= 60 -> "${minutes / 60}h ${minutes % 60}m"
            minutes > 0   -> "$minutes minute${if (minutes == 1L) "" else "s"}"
            else          -> "$seconds second${if (seconds == 1L) "" else "s"}"
        }
    }

    private fun getForegroundApp(): String? {
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val events = usm.queryEvents(now - 10000, now)
        val event = android.app.usage.UsageEvents.Event()
        var lastPackage: String? = null
        var lastTime = 0L
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED &&
                event.timeStamp > lastTime) {
                lastTime = event.timeStamp
                lastPackage = event.packageName
            }
        }
        if (lastPackage == null) {
            lastPackage = usm.queryUsageStats(UsageStatsManager.INTERVAL_BEST, now - 10000, now)
                ?.filter { it.lastTimeUsed > 0 }
                ?.maxByOrNull { it.lastTimeUsed }
                ?.packageName
        }
        Log.d("KiddoTime", "Foreground app: $lastPackage")
        return lastPackage
    }

    private fun getAppUsageToday(packageName: String): Long {
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val startOfDay = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val now = System.currentTimeMillis()
        val events = usm.queryEvents(startOfDay, now)
        val event = android.app.usage.UsageEvents.Event()
        var totalTime = 0L
        var lastResumeTime = -1L
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.packageName != packageName) continue
            when (event.eventType) {
                android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED ->
                    lastResumeTime = event.timeStamp
                android.app.usage.UsageEvents.Event.ACTIVITY_PAUSED -> {
                    if (lastResumeTime != -1L) {
                        totalTime += event.timeStamp - lastResumeTime
                        lastResumeTime = -1L
                    }
                }
            }
        }
        if (lastResumeTime != -1L) totalTime += now - lastResumeTime
        Log.d("KiddoTime", "Event-based usage for $packageName: ${totalTime}ms")
        return totalTime
    }

    private fun broadcastLimitReached(packageName: String, appName: String) {
        Log.d("KiddoTime", "Calling OverlayService.start appName=$appName package=$packageName")
        OverlayService.start(this, appName, packageName)
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        dismissWarning()
        try { unregisterReceiver(overlayDismissedReceiver) } catch (_: Exception) {}
    }
}
