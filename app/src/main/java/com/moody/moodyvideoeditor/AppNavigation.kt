package com.moody.moodyvideoeditor

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.moody.moodyvideoeditor.ui.screens.DashboardScreen
import com.moody.moodyvideoeditor.ui.screens.EditorScreen

object AppRoutes {
    const val DASHBOARD = "dashboard"
    const val EDITOR = "editor"
    const val CODE_MODE = "code_mode"
}

@Composable

fun AppNavigation(navController: NavHostController = rememberNavController()) {
    NavHost(
        navController = navController,
        startDestination = AppRoutes.DASHBOARD
    ) {
        composable(AppRoutes.DASHBOARD) {
            DashboardScreen(
                onNewProject = { navController.navigate(AppRoutes.EDITOR) },
                onCodeEditor = { navController.navigate(AppRoutes.CODE_MODE) }
            )
        }
        composable(AppRoutes.EDITOR) {
            EditorScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable(AppRoutes.CODE_MODE) {
            EditorScreen(
                onBack = { navController.popBackStack() },
                startInCodeMode = true   // 🆕 naya parameter
            )
        }
    }
}