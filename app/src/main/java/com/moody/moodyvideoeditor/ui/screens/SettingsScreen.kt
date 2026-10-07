package com.moody.moodyvideoeditor.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.moody.moodyvideoeditor.data.settings.AppSettings
import com.moody.moodyvideoeditor.viewmodel.SettingsViewModel

// ═══════════════════════════════════════════════════════════════
//  COLOR PALETTE — matches app's existing dark theme
// ═══════════════════════════════════════════════════════════════

private val BG_DARK = Color(0xFF0A0A0A)
private val BG_CARD = Color(0xFF181818)
private val BG_CARD_ALT = Color(0xFF1A1A1A)
private val BORDER_SUBTLE = Color(0xFF2A2A2A)
private val ACCENT = Color(0xFF7C3AED)
private val ACCENT_LIGHT = Color(0xFFA78BFA)
private val TEXT_PRIMARY = Color(0xFFFFFFFF)
private val TEXT_SECONDARY = Color(0xFFAAAAAA)
private val TEXT_MUTED = Color(0xFF888888)
private val BTN_SAVE = Color(0xFF00C2FF)
private val BTN_CANCEL = Color(0xFF3A3A3A)

// ═══════════════════════════════════════════════════════════════
//  SCREEN
// ═══════════════════════════════════════════════════════════════

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel()
) {
    val draft by viewModel.draftSettings.collectAsState()
    val isDirty by viewModel.isDirty.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()
    val errorMsg by viewModel.errorMessage.collectAsState()

    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }
    var showDiscardDialog by remember { mutableStateOf(false) }

    // Load draft on first composition
    LaunchedEffect(Unit) { viewModel.initializeDraft() }

    // Folder picker for export folder
    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (_: Throwable) {
            }
            viewModel.setDefaultExportFolderUri(uri.toString())
        }
    }

    // Handle back with dirty check
    BackHandler(enabled = true) {
        if (isDirty) showDiscardDialog = true else onBack()
    }

    // Auto-dismiss error
    LaunchedEffect(errorMsg) {
        if (errorMsg != null) {
            kotlinx.coroutines.delay(3000)
            viewModel.clearError()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BG_DARK)
    ) {

        Column(modifier = Modifier.fillMaxSize()) {

            // ─── Top Bar ───
            SettingsTopBar(
                isDirty = isDirty,
                onBack = {
                    if (isDirty) showDiscardDialog = true else onBack()
                }
            )

            // ─── Tab Row ───
            SettingsTabRow(
                selectedIndex = selectedTab,
                onSelect = { selectedTab = it }
            )

            // ─── Tab Content ───
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                AnimatedContent(
                    targetState = selectedTab,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "tab_content"
                ) { tab ->
                    when (tab) {
                        0 -> ProjectTab(draft, viewModel)
                        1 -> EditTab(draft, viewModel)
                        2 -> PerformanceTab(draft, viewModel)
                        3 -> LanguageTab(draft, viewModel, folderPicker)
                    }
                }
            }

            // ─── Bottom Action Bar ───
            SettingsBottomBar(
                isDirty = isDirty,
                isSaving = isSaving,
                onSave = {
                    viewModel.save()
                },
                onCancel = {
                    if (isDirty) showDiscardDialog = true
                    else {
                        viewModel.cancel(); onBack()
                    }
                }
            )
        }

        // ─── Error Snackbar ───
        errorMsg?.let { msg ->
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 100.dp, start = 16.dp, end = 16.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF2A0F0F))
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text(msg, color = Color(0xFFFF6B6B), fontSize = 12.sp)
            }
        }
    }

    // ─── Discard Confirmation Dialog ───
    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = {
                Text(
                    "Discard changes?",
                    color = TEXT_PRIMARY,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    "You have unsaved changes. Are you sure you want to leave?",
                    color = TEXT_SECONDARY,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDiscardDialog = false
                    viewModel.cancel()
                    onBack()
                }) {
                    Text("Discard", color = Color(0xFFFF6B6B), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) {
                    Text("Keep Editing", color = ACCENT_LIGHT, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = Color(0xFF1A1A1A),
            shape = RoundedCornerShape(16.dp)
        )
    }
}

// ═══════════════════════════════════════════════════════════════
//  TOP BAR
// ═══════════════════════════════════════════════════════════════

@Composable
private fun SettingsTopBar(isDirty: Boolean, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(BG_DARK)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.Filled.ArrowBack, "Back", tint = TEXT_PRIMARY)
        }
        Text(
            "Settings",
            color = TEXT_PRIMARY,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
        if (isDirty) {
            Box(
                modifier = Modifier
                    .padding(end = 12.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(ACCENT.copy(alpha = 0.15f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    "● Unsaved",
                    color = ACCENT_LIGHT,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
    Divider(color = BORDER_SUBTLE, thickness = 1.dp)
}

// ═══════════════════════════════════════════════════════════════
//  TAB ROW
// ═══════════════════════════════════════════════════════════════

private val TABS = listOf("Project", "Edit", "Performance", "Language")

@Composable
private fun SettingsTabRow(selectedIndex: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(BG_DARK)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        TABS.forEachIndexed { index, title ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (selected) Color(0xFF2A2A2A) else Color.Transparent)
                    .clickable { onSelect(index) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    title,
                    color = if (selected) TEXT_PRIMARY else TEXT_MUTED,
                    fontSize = 12.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  TAB 1 — PROJECT
// ═══════════════════════════════════════════════════════════════

@Composable
private fun ProjectTab(draft: AppSettings, vm: SettingsViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SettingsCard {
            SwitchRow(
                label = "Free layer",
                description = "Free layer is turned on by default when a new project is created",
                checked = draft.freeLayerEnabled,
                onCheckedChange = vm::setFreeLayerEnabled
            )

            CardDivider()

            NumberSpinnerRow(
                label = "Image duration",
                value = draft.defaultImageDurationMs / 1000f,
                unit = "s",
                step = 0.5f,
                min = 0.5f,
                max = 30f,
                decimals = 1,
                onValueChange = { vm.setDefaultImageDurationMs((it * 1000).toInt()) }
            )

            CardDivider()

            DropdownRow(
                label = "Export resolution",
                current = draft.defaultExportResolution,
                options = listOf("480p", "720p", "1080p", "2K", "4K"),
                onSelect = vm::setDefaultExportResolution
            )

            CardDivider()

            DropdownRow(
                label = "Aspect ratio",
                current = draft.defaultAspectRatio,
                options = listOf("16:9", "9:16", "1:1", "4:5", "3:4", "21:9"),
                onSelect = vm::setDefaultAspectRatio
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  TAB 2 — EDIT
// ═══════════════════════════════════════════════════════════════

@Composable
private fun EditTab(draft: AppSettings, vm: SettingsViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SettingsCard {
            NumberSpinnerRow(
                label = "Step forward/back",
                value = draft.stepFrames.toFloat(),
                unit = "frames",
                step = 1f,
                min = 1f,
                max = 120f,
                decimals = 0,
                onValueChange = { vm.setStepFrames(it.toInt()) }
            )

            CardDivider()

            NumberSpinnerRow(
                label = "Adjust value step",
                value = draft.adjustStep.toFloat(),
                unit = "units",
                step = 1f,
                min = 1f,
                max = 100f,
                decimals = 0,
                onValueChange = { vm.setAdjustStep(it.toInt()) }
            )

            CardDivider()

            DropdownRow(
                label = "Frame rate",
                current = "${draft.defaultFps}.00 fps",
                options = listOf("24.00 fps", "25.00 fps", "30.00 fps", "60.00 fps"),
                onSelect = { selected ->
                    val fps = selected.substringBefore('.').toIntOrNull() ?: 30
                    vm.setDefaultFps(fps)
                }
            )

            CardDivider()

            DropdownRow(
                label = "Time code",
                current = draft.timeCodeFormat,
                options = listOf(
                    "HH:MM:SS+frame",
                    "HH:MM:SS.ms",
                    "Frames only",
                    "Seconds only"
                ),
                onSelect = vm::setTimeCodeFormat
            )

            CardDivider()

            SwitchRow(
                label = "Snap to grid",
                description = "Snap clips to grid lines while dragging",
                checked = draft.snapToGrid,
                onCheckedChange = vm::setSnapToGrid
            )

            CardDivider()

            SwitchRow(
                label = "Haptic feedback",
                description = "Vibrate on clip snap and key actions",
                checked = draft.hapticFeedback,
                onCheckedChange = vm::setHapticFeedback
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  TAB 3 — PERFORMANCE
// ═══════════════════════════════════════════════════════════════

@Composable
private fun PerformanceTab(draft: AppSettings, vm: SettingsViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SettingsCard {
            DropdownRow(
                label = "Target loudness",
                current = draft.targetLoudness,
                options = listOf(
                    "Default (-23 LUFS)",
                    "Broadcast (-24 LUFS)",
                    "Streaming (-14 LUFS)",
                    "Podcast (-16 LUFS)",
                    "Custom"
                ),
                onSelect = vm::setTargetLoudness
            )

            CardDivider()

            SwitchRow(
                label = "Reduce motion",
                description = "Minimize animations throughout the app",
                checked = draft.reduceMotion,
                onCheckedChange = vm::setReduceMotion
            )

            CardDivider()

            SwitchRow(
                label = "Auto-save",
                description = "Automatically save your project while editing",
                checked = draft.autoSaveEnabled,
                onCheckedChange = vm::setAutoSaveEnabled
            )

            CardDivider()

            NumberSpinnerRow(
                label = "Auto-save interval",
                value = draft.autoSaveIntervalSec.toFloat(),
                unit = "s",
                step = 5f,
                min = 10f,
                max = 300f,
                decimals = 0,
                onValueChange = { vm.setAutoSaveIntervalSec(it.toInt()) }
            )

            CardDivider()

            SwitchRow(
                label = "Proxy mode",
                description = "Use lower-resolution proxies for smoother editing",
                checked = draft.proxyModeEnabled,
                onCheckedChange = vm::setProxyModeEnabled
            )

            CardDivider()

            SwitchRow(
                label = "Sound for export",
                description = "Send a notification sound when export is complete",
                checked = draft.exportSoundEnabled,
                onCheckedChange = vm::setExportSoundEnabled
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  TAB 4 — LANGUAGE
// ═══════════════════════════════════════════════════════════════

@Composable
private fun LanguageTab(
    draft: AppSettings,
    vm: SettingsViewModel,
    folderPicker: androidx.activity.result.ActivityResultLauncher<Uri?>
) {
    val hasFolder = !draft.defaultExportFolderUri.isNullOrBlank()
    val folderDisplay = if (hasFolder) {
        Uri.parse(draft.defaultExportFolderUri)
            .lastPathSegment?.take(30) ?: "Custom folder"
    } else "Movies/MoodyEditor (default)"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SettingsCard {
            DropdownRow(
                label = "App language",
                current = languageLabel(draft.appLanguage),
                options = listOf("English", "اردو", "हिन्दी", "العربية"),
                onSelect = { label ->
                    val code = when (label) {
                        "English" -> "en"
                        "اردو" -> "ur"
                        "हिन्दी" -> "hi"
                        "العربية" -> "ar"
                        else -> "en"
                    }
                    vm.setAppLanguage(code)
                }
            )

            CardDivider()

            DropdownRow(
                label = "App theme",
                current = themeLabel(draft.appTheme),
                options = listOf("System", "Light", "Dark"),
                onSelect = { label ->
                    vm.setAppTheme(label.lowercase())
                }
            )

            CardDivider()

            // Folder row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Default export folder",
                        color = TEXT_PRIMARY,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        folderDisplay,
                        color = TEXT_MUTED,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.width(10.dp))
                Box(
                    modifier = Modifier
                        .height(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(ACCENT.copy(alpha = 0.15f))
                        .clickable { folderPicker.launch(null) }
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Change",
                        color = ACCENT_LIGHT,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (hasFolder) {
                    Spacer(Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .height(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFFF6B6B).copy(alpha = 0.15f))
                            .clickable { vm.setDefaultExportFolderUri(null) }
                            .padding(horizontal = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Reset",
                            color = Color(0xFFFF6B6B),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Text(
            "Language changes apply immediately after saving.",
            color = TEXT_MUTED,
            fontSize = 10.sp,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

private fun languageLabel(code: String) = when (code) {
    "en" -> "English"
    "ur" -> "اردو"
    "hi" -> "हिन्दी"
    "ar" -> "العربية"
    else -> "English"
}

private fun themeLabel(key: String) = when (key) {
    "light" -> "Light"
    "dark" -> "Dark"
    else -> "System"
}

// ═══════════════════════════════════════════════════════════════
//  BOTTOM ACTION BAR
// ═══════════════════════════════════════════════════════════════

@Composable
private fun SettingsBottomBar(
    isDirty: Boolean,
    isSaving: Boolean,
    onSave: () -> Unit,
    onCancel: () -> Unit
) {
    Divider(color = BORDER_SUBTLE, thickness = 1.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(BG_DARK)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // SAVE button (primary)
        Button(
            onClick = onSave,
            enabled = isDirty && !isSaving,
            modifier = Modifier
                .weight(1f)
                .height(46.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = BTN_SAVE,
                contentColor = Color.Black,
                disabledContainerColor = BTN_SAVE.copy(alpha = 0.25f),
                disabledContentColor = Color.White.copy(alpha = 0.4f)
            ),
            shape = RoundedCornerShape(8.dp)
        ) {
            if (isSaving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = Color.Black
                )
            } else {
                Text(
                    "Save",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // CANCEL button (secondary)
        Button(
            onClick = onCancel,
            modifier = Modifier
                .weight(1f)
                .height(46.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = BTN_CANCEL,
                contentColor = TEXT_PRIMARY
            ),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(
                "Cancel",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  REUSABLE WIDGETS
// ═══════════════════════════════════════════════════════════════

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(BG_CARD)
            .border(1.dp, BORDER_SUBTLE, RoundedCornerShape(12.dp)),
        content = content
    )
}

@Composable
private fun CardDivider() {
    Divider(
        color = BORDER_SUBTLE.copy(alpha = 0.5f),
        thickness = 1.dp,
        modifier = Modifier.padding(horizontal = 14.dp)
    )
}

// ─── Switch Row ───
@Composable
private fun SwitchRow(
    label: String,
    description: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                label,
                color = TEXT_PRIMARY,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
            if (description != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    description,
                    color = TEXT_MUTED,
                    fontSize = 10.sp,
                    lineHeight = 14.sp
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = ACCENT,
                uncheckedThumbColor = Color(0xFF888888),
                uncheckedTrackColor = Color(0xFF2A2A2A)
            )
        )
    }
}

// ─── Dropdown Row ───
@Composable
private fun DropdownRow(
    label: String,
    current: String,
    options: List<String>,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            color = TEXT_PRIMARY,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.width(12.dp))

        Box {
            Row(
                modifier = Modifier
                    .height(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(BG_CARD_ALT)
                    .clickable { expanded = true }
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    current,
                    color = TEXT_PRIMARY,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.width(6.dp))
                Text("▾", color = TEXT_SECONDARY, fontSize = 12.sp)
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier
                    .background(BG_CARD)
                    .border(1.dp, BORDER_SUBTLE, RoundedCornerShape(8.dp))
            ) {
                options.forEach { opt ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                opt,
                                color = if (opt == current) ACCENT_LIGHT else TEXT_PRIMARY,
                                fontSize = 13.sp,
                                fontWeight = if (opt == current) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        onClick = {
                            onSelect(opt)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

// ─── Number Spinner Row ───
@Composable
private fun NumberSpinnerRow(
    label: String,
    value: Float,
    unit: String,
    step: Float,
    min: Float,
    max: Float,
    decimals: Int,
    onValueChange: (Float) -> Unit
) {
    val formatted = if (decimals > 0) "%.${decimals}f".format(value)
    else value.toInt().toString()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            color = TEXT_PRIMARY,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.width(10.dp))

        Row(
            modifier = Modifier
                .height(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(BG_CARD_ALT),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Decrement
            SpinnerButton("−") {
                val nv = (value - step).coerceIn(min, max)
                onValueChange(nv)
            }

            // Value
            Box(
                modifier = Modifier
                    .width(56.dp)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    formatted,
                    color = TEXT_PRIMARY,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }

            // Increment
            SpinnerButton("+") {
                val nv = (value + step).coerceIn(min, max)
                onValueChange(nv)
            }
        }

        Spacer(Modifier.width(8.dp))
        Text(
            unit,
            color = TEXT_MUTED,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.widthIn(min = 32.dp)
        )
    }
}

@Composable
private fun SpinnerButton(symbol: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .width(32.dp)
            .fillMaxHeight()
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            symbol,
            color = ACCENT_LIGHT,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )
    }
}