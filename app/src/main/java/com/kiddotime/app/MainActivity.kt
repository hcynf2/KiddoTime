package com.kiddotime.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.kiddotime.app.navigation.Routes
import com.kiddotime.app.screens.ChildScreen
import com.kiddotime.app.screens.ModeSelectScreen
import com.kiddotime.app.screens.ParentScreen
import com.kiddotime.app.ui.theme.KiddoTimeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            KiddoTimeTheme {
                KiddoTimeApp()
            }
        }
    }
}

@Composable
fun KiddoTimeApp() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Routes.MODE_SELECT
    ) {
        composable(Routes.MODE_SELECT) {
            ModeSelectScreen(
                onParentClick = { navController.navigate(Routes.PARENT) },
                onChildClick = { navController.navigate(Routes.CHILD) }
            )
        }
        composable(Routes.PARENT) {
            ParentScreen()
        }
        composable(Routes.CHILD) {
            ChildScreen()
        }
    }
}