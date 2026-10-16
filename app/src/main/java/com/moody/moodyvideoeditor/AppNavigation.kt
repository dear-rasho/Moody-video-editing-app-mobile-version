package com.moody.moodyvideoeditor

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.moody.moodyvideoeditor.data.EditorState
import com.moody.moodyvideoeditor.data.ProjectMeta
import com.moody.moodyvideoeditor.data.ProjectRepository
import com.moody.moodyvideoeditor.ui.screens.DashboardScreen
import com.moody.moodyvideoeditor.ui.screens.EditorScreen
import com.moody.moodyvideoeditor.ui.screens.HelpGuideScreen
import com.moody.moodyvideoeditor.ui.screens.SettingsScreen
import java.util.UUID

object AppRoutes {
    const val DASHBOARD = "dashboard"
    const val HELP = "help"
    const val SETTINGS = "settings"

    fun editorWithProject(projectId: String): String = "editor/$projectId"

    fun editorWithSavedTemplate(projectId: String, savedTemplateId: String): String =
        "editor/$projectId?savedTemplateId=$savedTemplateId"

    fun codeModeWithProject(projectId: String): String = "code_mode/$projectId"
}

@Composable
fun AppNavigation(navController: NavHostController = rememberNavController()) {
    val context = LocalContext.current

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
                onOpenFromSavedTemplate = { templateId ->
                    val projectId = UUID.randomUUID().toString()
                    val now = System.currentTimeMillis()
                    val meta = ProjectMeta(
                        id = projectId,
                        name = "From Template",
                        createdAt = now,
                        updatedAt = now,
                        thumbnailPath = null,
                        clipCount = 0,
                        durationMs = 0L
                    )
                    ProjectRepository.saveProject(context, meta, EditorState())
                    navController.navigate(
                        AppRoutes.editorWithSavedTemplate(projectId, templateId)
                    )
                }
            )
        }

        composable(
            route = "editor/{projectId}?savedTemplateId={savedTemplateId}",
            arguments = listOf(
                navArgument("projectId") { type = NavType.StringType },
                navArgument("savedTemplateId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val projectId = backStackEntry.arguments?.getString("projectId").orEmpty()
            val savedTemplateId = backStackEntry.arguments?.getString("savedTemplateId")
            EditorScreen(
                projectId = projectId,
                onBack = { navController.popBackStack() },
                initialSavedTemplateId = savedTemplateId
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

        composable(AppRoutes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}