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
import androidx.compose.runtime.DisposableEffect
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.moody.moodyvideoeditor.data.EditorState
import com.moody.moodyvideoeditor.data.ProjectMeta
import com.moody.moodyvideoeditor.data.ProjectRepository
import com.moody.moodyvideoeditor.data.SavedTemplateRepository
import com.moody.moodyvideoeditor.ui.home.CodeBaseShelf
import com.moody.moodyvideoeditor.ui.home.RecentProjects
import com.moody.moodyvideoeditor.ui.home.SavedTemplatesShelf
import java.util.UUID

@Composable
fun DashboardScreen(
    onNewProject: (String) -> Unit,
    onOpenProject: (String) -> Unit,
    onCodeEditor: (String) -> Unit,
    onOpenHelp: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenFromSavedTemplate: (String) -> Unit
) {
    val context = LocalContext.current
    var projects by remember { mutableStateOf(ProjectRepository.listProjects(context)) }
    var savedTemplates by remember {
        mutableStateOf(SavedTemplateRepository.listTemplates(context))
    }
    var pendingDeleteId by remember { mutableStateOf<String?>(null) }
    var pendingDeleteTemplateId by remember { mutableStateOf<String?>(null) }
    var selectingProjects by remember { mutableStateOf(false) }
    var selectedProjectIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var selectingTemplates by remember { mutableStateOf(false) }
    var selectedTemplateIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var confirmDeleteProjects by remember { mutableStateOf(false) }
    var confirmDeleteTemplates by remember { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(context, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                projects = ProjectRepository.listProjects(context)
                savedTemplates = SavedTemplateRepository.listTemplates(context)
                selectedProjectIds = selectedProjectIds intersect projects.map { it.id }.toSet()
                selectedTemplateIds =
                    selectedTemplateIds intersect savedTemplates.map { it.id }.toSet()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
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
        ProjectRepository.saveProject(context, meta, EditorState())
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
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF181818))
                        .pointerInput(Unit) {
                            detectTapGestures { onOpenSettings() }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text("⚙️", fontSize = 20.sp)
                }

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
        SelectionSectionHeader(
            title = "Recent Projects",
            selectionMode = selectingProjects,
            selectedCount = selectedProjectIds.size,
            itemCount = projects.size,
            onEnterSelection = {
                selectingProjects = true
                selectedProjectIds = emptySet()
            },
            onSelectAll = { selectedProjectIds = projects.map { it.id }.toSet() },
            onDelete = { confirmDeleteProjects = true },
            onCancel = {
                selectingProjects = false
                selectedProjectIds = emptySet()
            }
        )
        RecentProjects(
            projects = projects,
            onOpen = { projectId -> onOpenProject(projectId) },
            onDelete = { projectId -> pendingDeleteId = projectId },
            selectionMode = selectingProjects,
            selectedIds = selectedProjectIds,
            onToggleSelection = { projectId ->
                selectedProjectIds = if (projectId in selectedProjectIds) {
                    selectedProjectIds - projectId
                } else {
                    selectedProjectIds + projectId
                }
            },
            onStartSelection = { projectId ->
                selectingProjects = true
                selectedProjectIds = selectedProjectIds + projectId
            }
        )

        // 🆕 MY TEMPLATES
        SelectionSectionHeader(
            title = "My Templates",
            selectionMode = selectingTemplates,
            selectedCount = selectedTemplateIds.size,
            itemCount = savedTemplates.size,
            onEnterSelection = {
                selectingTemplates = true
                selectedTemplateIds = emptySet()
            },
            onSelectAll = { selectedTemplateIds = savedTemplates.map { it.id }.toSet() },
            onDelete = { confirmDeleteTemplates = true },
            onCancel = {
                selectingTemplates = false
                selectedTemplateIds = emptySet()
            }
        )
        SavedTemplatesShelf(
            templates = savedTemplates,
            onOpen = { templateId -> onOpenFromSavedTemplate(templateId) },
            onDelete = { templateId -> pendingDeleteTemplateId = templateId },
            selectionMode = selectingTemplates,
            selectedIds = selectedTemplateIds,
            onToggleSelection = { templateId ->
                selectedTemplateIds = if (templateId in selectedTemplateIds) {
                    selectedTemplateIds - templateId
                } else {
                    selectedTemplateIds + templateId
                }
            },
            onStartSelection = { templateId ->
                selectingTemplates = true
                selectedTemplateIds = selectedTemplateIds + templateId
            }
        )



        // CODE BASE EDITING
        SectionTitle("Code Base Editing")
        CodeBaseShelf(
            onOpen = { createNewProject(true) }
        )

        // HELP CARD
        SectionTitle("Help")
        HelpCard(onOpenHelp)
    }

    // Project delete dialog
    if (pendingDeleteId != null) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { pendingDeleteId = null },
            title = {
                Text("Delete Project?", color = Color.White, fontSize = 16.sp,
                    fontWeight = FontWeight.Bold)
            },
            text = {
                val projName = projects.firstOrNull { it.id == pendingDeleteId }?.name
                    ?: "this project"
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
                    Text("Delete", color = Color(0xFFFF6B6B), fontSize = 12.sp,
                        fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                Box(
                    modifier = Modifier
                        .height(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF181818))
                        .pointerInput(Unit) {
                            detectTapGestures { pendingDeleteId = null }
                        }
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Cancel", color = Color.White, fontSize = 12.sp,
                        fontWeight = FontWeight.Bold)
                }
            },
            containerColor = Color(0xFF1A1A1A),
            shape = RoundedCornerShape(16.dp)
        )
    }

    // Template delete dialog
    if (pendingDeleteTemplateId != null) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { pendingDeleteTemplateId = null },
            title = {
                Text("Delete Template?", color = Color.White, fontSize = 16.sp,
                    fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    "Are you sure you want to delete this template?",
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
                                pendingDeleteTemplateId?.let { id ->
                                    SavedTemplateRepository.delete(context, id)
                                    savedTemplates = SavedTemplateRepository.listTemplates(context)
                                }
                                pendingDeleteTemplateId = null
                            }
                        }
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Delete", color = Color(0xFFFF6B6B), fontSize = 12.sp,
                        fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                Box(
                    modifier = Modifier
                        .height(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF181818))
                        .pointerInput(Unit) {
                            detectTapGestures { pendingDeleteTemplateId = null }
                        }
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Cancel", color = Color.White, fontSize = 12.sp,
                        fontWeight = FontWeight.Bold)
                }
            },
            containerColor = Color(0xFF1A1A1A),
            shape = RoundedCornerShape(16.dp)
        )
    }

    if (confirmDeleteProjects) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmDeleteProjects = false },
            title = {
                Text(
                    "Delete ${selectedProjectIds.size} projects?",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    "These projects and their thumbnails will be permanently deleted.",
                    color = Color(0xFFAAAAAA),
                    fontSize = 12.sp
                )
            },
            confirmButton = {
                DialogAction("Delete", Color(0xFFFF6B6B)) {
                    ProjectRepository.deleteProjects(context, selectedProjectIds)
                    projects = ProjectRepository.listProjects(context)
                    selectedProjectIds = emptySet()
                    selectingProjects = false
                    confirmDeleteProjects = false
                }
            },
            dismissButton = {
                DialogAction("Cancel", Color.White) { confirmDeleteProjects = false }
            },
            containerColor = Color(0xFF1A1A1A),
            shape = RoundedCornerShape(16.dp)
        )
    }

    if (confirmDeleteTemplates) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmDeleteTemplates = false },
            title = {
                Text(
                    "Delete ${selectedTemplateIds.size} templates?",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    "These saved templates will be permanently deleted.",
                    color = Color(0xFFAAAAAA),
                    fontSize = 12.sp
                )
            },
            confirmButton = {
                DialogAction("Delete", Color(0xFFFF6B6B)) {
                    SavedTemplateRepository.deleteMany(context, selectedTemplateIds)
                    savedTemplates = SavedTemplateRepository.listTemplates(context)
                    selectedTemplateIds = emptySet()
                    selectingTemplates = false
                    confirmDeleteTemplates = false
                }
            },
            dismissButton = {
                DialogAction("Cancel", Color.White) { confirmDeleteTemplates = false }
            },
            containerColor = Color(0xFF1A1A1A),
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@Composable
private fun SelectionSectionHeader(
    title: String,
    selectionMode: Boolean,
    selectedCount: Int,
    itemCount: Int,
    onEnterSelection: () -> Unit,
    onSelectAll: () -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            title,
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (!selectionMode && itemCount > 0) {
                HeaderAction("Select", Color(0xFF60EFFF), onEnterSelection)
            } else if (selectionMode) {
                Text(
                    "$selectedCount selected",
                    color = Color(0xFFAAAAAA),
                    fontSize = 10.sp,
                    modifier = Modifier.align(Alignment.CenterVertically)
                )
                HeaderAction("All", Color(0xFF60EFFF), onSelectAll)
                if (selectedCount > 0) {
                    HeaderAction("Delete", Color(0xFFFF6B6B), onDelete)
                }
                HeaderAction("Cancel", Color.White, onCancel)
            }
        }
    }
}

@Composable
private fun HeaderAction(label: String, color: Color, onClick: () -> Unit) {
    Text(
        label,
        color = color,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF222222))
            .pointerInput(label) { detectTapGestures { onClick() } }
            .padding(horizontal = 8.dp, vertical = 6.dp)
    )
}

@Composable
private fun DialogAction(label: String, color: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .height(36.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (label == "Delete") color.copy(alpha = 0.2f) else Color(0xFF181818))
            .pointerInput(label) { detectTapGestures { onClick() } }
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
            Text("→", color = Color(0xFF7C3AED), fontSize = 18.sp,
                fontWeight = FontWeight.Bold)
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