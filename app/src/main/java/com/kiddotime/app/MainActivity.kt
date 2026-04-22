package com.kiddotime.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.net.Uri
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.kiddotime.app.navigation.Routes
import com.kiddotime.app.screens.ChildScreen
import com.kiddotime.app.screens.DataPrivacyScreen
import com.kiddotime.app.screens.ModeSelectScreen
import com.kiddotime.app.screens.ParentScreen
import com.kiddotime.app.service.AppMonitorService
import com.kiddotime.app.ui.theme.KiddoTimeTheme

class MainActivity : ComponentActivity() {

    // Holds the package name of the app that hit its limit
    private val limitReachedState = mutableStateOf<String?>(null)

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            Log.d("KiddoTime", "Notification permission granted: $granted")
        }
    private val limitReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val packageName = intent.getStringExtra(AppMonitorService.EXTRA_PACKAGE_NAME)
            Log.d("KiddoTime", "Broadcast received in MainActivity for: $packageName")
            limitReachedState.value = packageName
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("KiddoTime", "Starting AppMonitorService...")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        AppMonitorService.start(this)
        Log.d("KiddoTime", "Service start called")


        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }

        // Listen for limit reached broadcasts
        val filter = IntentFilter(AppMonitorService.ACTION_LIMIT_REACHED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(limitReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(limitReceiver, filter)
        }

        setContent {
            KiddoTimeTheme {
                KiddoTimeApp(limitReachedState)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(limitReceiver)
    }
}

@Composable
fun KiddoTimeApp(limitReachedState: MutableState<String?>) {
    val navController = rememberNavController()
    val limitedPackage by limitReachedState

    Log.d("KiddoTime", "KiddoTimeApp composable loaded")

    NavHost(
        navController = navController,
        startDestination = Routes.MODE_SELECT
    ) {
        composable(Routes.MODE_SELECT) {
            Log.d("KiddoTime", "ModeSelectScreen loaded")
            ModeSelectScreen(
                onParentClick = {
                    Log.d("KiddoTime", "Parent button tapped")
                    navController.navigate(Routes.PARENT)
                },
                onChildClick = { navController.navigate(Routes.CHILD) }
            )
        }
        composable(Routes.PARENT) {
            Log.d("KiddoTime", "ParentScreen loaded")
            ParentScreen(
                onPrivacyClick = { navController.navigate(Routes.DATA_PRIVACY) }
            )
        }
        composable(Routes.CHILD) {
            ChildScreen()
        }
        composable(Routes.DATA_PRIVACY) {
            DataPrivacyScreen(onBack = { navController.popBackStack() })
        }
    }
}