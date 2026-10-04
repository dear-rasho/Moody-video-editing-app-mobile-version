package com.moody.moodyvideoeditor.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moody.moodyvideoeditor.data.ProjectMeta
import com.moody.moodyvideoeditor.data.ProjectRepository
import com.moody.moodyvideoeditor.ui.home.CodeBaseShelf
import com.moody.moodyvideoeditor.ui.home.RecentProjects
import com.moody.moodyvideoeditor.ui.home.TemplatesShelf
import java.util.UUID

@Composable
fun DashboardScreen(
    onNewProject: (String) -> Unit,
    onOpenProject: (String) -> Unit,
    onCodeEditor: (String) -> Unit,
    onOpenHelp: () -> Unit = {}
) {
    val context = LocalContext.current
    var projects by remember { mutableStateOf(ProjectRepository.listProjects(context)) }
    var pendingDeleteId by remember { mutableStateOf<String?>(null) }

    // Refresh list when screen is composed
    remember(Unit) {
        projects = ProjectRepository.listProjects(context)
    }

    fun createNewProject(isCodeMode: Boolean) {
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val meta = ProjectMeta(
            id = id,
            name = if (isCodeMode) "Code Project" else "New Project",
            createdAt = now,
            updatedAt = now,
            thumbnailPath = null,
            clipCount = 0,
            durationMs = 0L
        )
        ProjectRepository.saveProject(
            context, meta,
            com.moody.moodyvideoeditor.data.EditorState()
        )
        if (isCodeMode) onCodeEditor(id) else onNewProject(id)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // HEADER
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "OFFLINE VIDEO EDITOR",
                    color = Color(0xFF888888),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "Projects",
                    color = Color.White,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // 🆕 Help button
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF181818))
                        .pointerInput(Unit) {
                            detectTapGestures { onOpenHelp() }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text("📖", fontSize = 20.sp)
                }

                // New Project button
                IconButton(
                    onClick = { createNewProject(false) },
                    modifier = Modifier
                        .size(48.dp)
                        .background(Color(0xFF7C3AED), RoundedCornerShape(12.dp))
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = "New Project",
                        tint = Color.White
                    )
                }
            }
        }

        // RECENT PROJECTS
        SectionTitle("Recent Projects")
        RecentProjects(
            projects = projects,
            onOpen = { projectId -> onOpenProject(projectId) },
            onDelete = { projectId ->
                // Show confirmation dialog
                pendingDeleteId = projectId
            }
        )

        // TEMPLATES
        SectionTitle("Templates")
        TemplatesShelf()

        // CODE BASE EDITING
        SectionTitle("Code Base Editing")
        CodeBaseShelf(
            onOpen = { title ->
                createNewProject(true)
            }
        )

        // HELP CARD
        SectionTitle("Help")
        HelpCard(onOpenHelp)
    }

    // 🆕 Delete Confirmation Dialog
    if (pendingDeleteId != null) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { pendingDeleteId = null },
            title = {
                Text(
                    "Delete Project?",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                val projName = projects
                    .firstOrNull { it.id == pendingDeleteId }
                    ?.name ?: "this project"
                Text(
                    "Are you sure you want to delete \"$projName\"? " +
                            "This cannot be undone.",
                    color = Color(0xFFAAAAAA),
                    fontSize = 12.sp
                )
            },
            confirmButton = {
                Box(
                    modifier = Modifier
                        .height(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFFF6B6B).copy(alpha = 0.2f))
                        .pointerInput(Unit) {
                            detectTapGestures {
                                pendingDeleteId?.let { id ->
                                    ProjectRepository.deleteProject(context, id)
                                    projects = ProjectRepository.listProjects(context)
                                }
                                pendingDeleteId = null
                            }
                        }
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Delete",
                        color = Color(0xFFFF6B6B),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                Box(
                    modifier = Modifier
                        .height(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF181818))
                        .pointerInput(Unit) {
                            detectTapGestures {
                                pendingDeleteId = null
                            }
                        }
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Cancel",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            containerColor = Color(0xFF1A1A1A),
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@Composable
private fun HelpCard(onOpen: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF1A1F3A))
            .pointerInput(Unit) {
                detectTapGestures { onOpen() }
            }
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF7C3AED).copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center
            ) {
                Text("📖", fontSize = 22.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Guide & AI Prompt Helper",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Learn features, copy AI prompt, browse commands",
                    color = Color(0xFFAAAAAA),
                    fontSize = 10.sp
                )
            }
            Text("→", color = Color(0xFF7C3AED), fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text = text,
        color = Color.White,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold
    )
}