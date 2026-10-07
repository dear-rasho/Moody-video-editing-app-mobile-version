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
import com.moody.moodyvideoeditor.ui.screens.SettingsScreen

object AppRoutes {
    const val DASHBOARD = "dashboard"
    const val HELP = "help"
    const val SETTINGS = "settings"

    fun editorWithProject(projectId: String): String = "editor/$projectId"
    fun editorWithTemplate(projectId: String, templateId: String): String =
        "editor/$projectId?templateId=$templateId"

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
                },
                onOpenSettings = {
                    navController.navigate(AppRoutes.SETTINGS)
                },
                onTemplateProject = { projectId, templateId ->
                    navController.navigate(
                        AppRoutes.editorWithTemplate(projectId, templateId)
                    )
                }
            )
        }

        composable(
            route = "editor/{projectId}?templateId={templateId}",
            arguments = listOf(
                navArgument("projectId") { type = NavType.StringType },
                navArgument("templateId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val projectId = backStackEntry.arguments?.getString("projectId").orEmpty()
            val templateId = backStackEntry.arguments?.getString("templateId")
            EditorScreen(
                projectId = projectId,
                onBack = { navController.popBackStack() },
                initialTemplateId = templateId
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

        // 🆕 Settings route
        composable(AppRoutes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}