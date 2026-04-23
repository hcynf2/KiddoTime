package com.kiddotime.app.overlay

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.Toast
import com.kiddotime.app.data.AppDatabase
import com.kiddotime.app.data.CooldownEventRepository
import com.kiddotime.app.data.GamePreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var gamePrefs: GamePreferences
    private lateinit var cooldownRepo: CooldownEventRepository
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var overlayView: FrameLayout? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var homeReceiver: android.content.BroadcastReceiver? = null
    private var currentLockedPackage: String = ""
    @Volatile private var currentCooldownEventId: Long = -1L

    companion object {
        const val EXTRA_APP_NAME = "app_name"
        const val EXTRA_PACKAGE_NAME = "package_name"

        fun start(context: Context, appName: String, packageName: String) {
            val intent = Intent(context, OverlayService::class.java).apply {
                putExtra(EXTRA_APP_NAME, appName)
                putExtra(EXTRA_PACKAGE_NAME, packageName)
            }
            context.startService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, OverlayService::class.java))
        }

        fun startLockScreen(context: Context, appName: String, packageName: String) {
            val intent = Intent(context, OverlayService::class.java).apply {
                putExtra(EXTRA_APP_NAME, appName)
                putExtra(EXTRA_PACKAGE_NAME, packageName)
                putExtra("lock_screen_only", true)
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        gamePrefs = GamePreferences(applicationContext)
        cooldownRepo = CooldownEventRepository(AppDatabase.getDatabase(applicationContext).cooldownEventDao())
        Log.d("KiddoTime", "OverlayService onCreate called")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val appName = intent?.getStringExtra(EXTRA_APP_NAME) ?: "this app"
        val lockedPackage = intent?.getStringExtra(EXTRA_PACKAGE_NAME) ?: ""
        val lockScreenOnly = intent?.getBooleanExtra("lock_screen_only", false) ?: false
        currentLockedPackage = lockedPackage
        Log.d("KiddoTime", "OverlayService onStartCommand - appName=$appName lockScreenOnly=$lockScreenOnly")
        mainHandler.post {
            if (overlayView == null) {
                if (lockScreenOnly) {
                    showLockScreen(appName, lockedPackage)
                } else {
                    showGameOverlay(appName, lockedPackage)
                }
            } else {
                Log.d("KiddoTime", "Overlay already showing - ignoring")
            }
        }
        return START_NOT_STICKY
    }

    private fun registerHomeReceiver(lockedPackage: String, appName: String) {
        homeReceiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == Intent.ACTION_CLOSE_SYSTEM_DIALOGS) {
                    val reason = intent.getStringExtra("reason")
                    if (reason == "homekey" || reason == "recentapps") {
                        Log.d("KiddoTime", "Home pressed - dismissing lock screen overlay")
                        mainHandler.post {
                            unregisterHomeReceiver()
                            removeOverlay()
                            val resetIntent = Intent("com.kiddotime.app.OVERLAY_DISMISSED").apply {
                                setPackage(packageName)
                                putExtra("package_name", currentLockedPackage)
                            }
                            sendBroadcast(resetIntent)
                        }
                    }
                }
            }
        }
        val filter = android.content.IntentFilter(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(homeReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(homeReceiver, filter)
        }
    }

    private fun unregisterHomeReceiver() {
        homeReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (e: Exception) {
                Log.e("KiddoTime", "Failed to unregister home receiver: ${e.message}")
            }
            homeReceiver = null
        }
    }


    private fun getLockScreenParams(): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_FULLSCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }
    }

    private fun showGameOverlay(appName: String, lockedPackage: String) {
        val chosenGame = gamePrefs.pickGame()
        Log.d("KiddoTime", "showGameOverlay: chosen=$chosenGame")

        // Record that a cooldown started; ID is stored for completion tracking.
        currentCooldownEventId = -1L
        serviceScope.launch {
            val id = cooldownRepo.recordStart(lockedPackage, appName, chosenGame)
            currentCooldownEventId = id
        }

        val container = FrameLayout(this)

        when (chosenGame) {
            GamePreferences.GAME_CLEANUP -> {
                val gameView = CleanUpGameView(this)
                gameView.onAllItemsPlaced = {
                    Log.d("KiddoTime", "CleanUp complete — transitioning to lock screen")
                    val eventId = currentCooldownEventId
                    gamePrefs.onGameComplete(GamePreferences.GAME_CLEANUP)
                    serviceScope.launch {
                        if (eventId > 0) cooldownRepo.recordComplete(eventId, System.currentTimeMillis())
                    }
                    mainHandler.post {
                        removeOverlay()
                        showLockScreen(appName, lockedPackage)
                    }
                }
                container.addView(gameView)
            }
            GamePreferences.GAME_WHAT_NEXT -> {
                val gameView = WhatNextGameView(this)
                gameView.onActivityChosen = { chosenLabel ->
                    Log.d("KiddoTime", "WhatNext complete ($chosenLabel) — transitioning to lock screen")
                    val eventId = currentCooldownEventId
                    gamePrefs.onGameComplete(GamePreferences.GAME_WHAT_NEXT)
                    serviceScope.launch {
                        if (eventId > 0) cooldownRepo.recordComplete(eventId, System.currentTimeMillis(), whatNextChoice = chosenLabel)
                    }
                    mainHandler.post {
                        removeOverlay()
                        showLockScreen(appName, lockedPackage)
                    }
                }
                container.addView(gameView)
            }
            else -> { // GAME_CARD (default / fallback)
                val gameView = CardMatchingGameView(this)
                gameView.onAllRoundsComplete = {
                    Log.d("KiddoTime", "CardMatching complete — transitioning to lock screen")
                    val eventId = currentCooldownEventId
                    gamePrefs.onGameComplete(GamePreferences.GAME_CARD)
                    serviceScope.launch {
                        if (eventId > 0) cooldownRepo.recordComplete(eventId, System.currentTimeMillis())
                    }
                    mainHandler.post {
                        removeOverlay()
                        showLockScreen(appName, lockedPackage)
                    }
                }
                container.addView(gameView)
            }
        }

        overlayView = container

        try {
            windowManager.addView(container, getGameOverlayParams())
            Log.d("KiddoTime", "Game overlay added to WindowManager")
        } catch (e: Exception) {
            Log.e("KiddoTime", "Failed to show game overlay: ${e.message}")
        }
    }

    private fun showLockScreen(appName: String, lockedPackage: String) {
        Log.d("KiddoTime", "showLockScreen called for $appName")
        val container = FrameLayout(this)

        val lockScreen = LockScreenView(
            context = this,
            appName = appName,
            onCorrectPin = {
                Log.d("KiddoTime", "Correct PIN - unlocking $lockedPackage")
                mainHandler.post {
                    unregisterHomeReceiver()
                    removeOverlay()
                    val unlockIntent = Intent("com.kiddotime.app.APP_UNLOCKED").apply {
                        putExtra("package_name", lockedPackage)
                        setPackage(packageName)
                    }
                    sendBroadcast(unlockIntent)
                    val dismissIntent = Intent("com.kiddotime.app.OVERLAY_DISMISSED").apply {
                        setPackage(packageName)
                        putExtra("package_name", lockedPackage)
                    }
                    sendBroadcast(dismissIntent)
                    val launchIntent = packageManager.getLaunchIntentForPackage(lockedPackage)
                    launchIntent?.let {
                        it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(it)
                    }
                    stopSelf()
                }
            },
            onWrongPin = {
                Log.d("KiddoTime", "Wrong PIN entered")
            },
            onMaxAttempts = {
                Log.d("KiddoTime", "Max PIN attempts reached - exiting")
                mainHandler.post {
                    val msg = "Maximum pin attempt reached, app will exit now"
                    Toast.makeText(applicationContext, msg, Toast.LENGTH_LONG).show()
                    val broadcastIntent = Intent("com.kiddotime.app.MAX_PIN_ATTEMPTS").apply {
                        putExtra("message", msg)
                        setPackage(packageName)
                    }
                    sendBroadcast(broadcastIntent)
                    unregisterHomeReceiver()
                    removeOverlay()
                    val dismissIntent = Intent("com.kiddotime.app.OVERLAY_DISMISSED").apply {
                        setPackage(packageName)
                        putExtra("package_name", currentLockedPackage)
                    }
                    sendBroadcast(dismissIntent)
                    val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                        addCategory(Intent.CATEGORY_HOME)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(homeIntent)
                    stopSelf()
                }
            },
            onGoHome = {
                // Child pressed back/home - dismiss overlay and go to home screen
                mainHandler.post {
                    unregisterHomeReceiver()
                    removeOverlay()
                    // Reset overlay showing so lock screen can reappear
                    // when child tries to reopen the app
                    val resetIntent = Intent("com.kiddotime.app.OVERLAY_DISMISSED").apply {
                        setPackage(packageName)
                        putExtra("package_name", currentLockedPackage)
                    }
                    sendBroadcast(resetIntent)
                    // Navigate to home screen
                    val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                        addCategory(Intent.CATEGORY_HOME)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(homeIntent)
                }
            }
        )

        container.addView(lockScreen)
        overlayView = container

        // Register home button receiver
        registerHomeReceiver(lockedPackage, appName)

        try {
            windowManager.addView(container, getLockScreenParams())
            Log.d("KiddoTime", "Lock screen added to WindowManager")
        } catch (e: Exception) {
            Log.e("KiddoTime", "Failed to show lock screen: ${e.message}")
        }
    }

    private fun removeOverlay() {
        overlayView?.let {
            try {
                windowManager.removeView(it)
                Log.d("KiddoTime", "Overlay removed successfully")
            } catch (e: Exception) {
                Log.e("KiddoTime", "Failed to remove overlay: ${e.message}")
            }
            overlayView = null
        } ?: Log.d("KiddoTime", "removeOverlay called but overlayView was null")
    }
    private fun getGameOverlayParams(): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_FULLSCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
            width= WindowManager.LayoutParams.MATCH_PARENT
            height = WindowManager.LayoutParams.MATCH_PARENT
        }
    }
    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        unregisterHomeReceiver()
        mainHandler.removeCallbacksAndMessages(null)
        removeOverlay()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}