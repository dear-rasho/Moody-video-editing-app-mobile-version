package com.moody.moodyvideoeditor

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.moody.moodyvideoeditor.ui.screens.DashboardScreen
import com.moody.moodyvideoeditor.ui.screens.EditorScreen
import com.moody.moodyvideoeditor.ui.screens.HelpGuideScreen

object AppRoutes {
    const val DASHBOARD = "dashboard"
    const val HELP = "help"

    fun editorWithProject(projectId: String): String = "editor/$projectId"
    fun codeModeWithProject(projectId: String): String = "code_mode/$projectId"
}

@Composable
fun AppNavigation(navController: NavHostController = rememberNavController()) {
    NavHost(
        navController = navController,
        startDestination = AppRoutes.DASHBOARD
    ) {
        composable(AppRoutes.DASHBOARD) {
            DashboardScreen(
                onNewProject = { projectId ->
                    navController.navigate(AppRoutes.editorWithProject(projectId))
                },
                onOpenProject = { projectId ->
                    navController.navigate(AppRoutes.editorWithProject(projectId))
                },
                onCodeEditor = { projectId ->
                    navController.navigate(AppRoutes.codeModeWithProject(projectId))
                },
                onOpenHelp = {
                    navController.navigate(AppRoutes.HELP)
                }
            )
        }

        composable(
            route = "editor/{projectId}",
            arguments = listOf(navArgument("projectId") { type = NavType.StringType })
        ) { backStackEntry ->
            val projectId = backStackEntry.arguments?.getString("projectId").orEmpty()
            EditorScreen(
                projectId = projectId,
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = "code_mode/{projectId}",
            arguments = listOf(navArgument("projectId") { type = NavType.StringType })
        ) { backStackEntry ->
            val projectId = backStackEntry.arguments?.getString("projectId").orEmpty()
            EditorScreen(
                projectId = projectId,
                onBack = { navController.popBackStack() },
                startInCodeMode = true
            )
        }

        composable(AppRoutes.HELP) {
            HelpGuideScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}