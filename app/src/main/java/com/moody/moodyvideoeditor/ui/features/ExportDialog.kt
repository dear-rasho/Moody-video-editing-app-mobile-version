package com.moody.moodyvideoeditor.ui.features

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moody.moodyvideoeditor.utils.ExportSettings

/**
 * Full-screen modal export dialog with:
 *  - File name
 *  - 🆕 Custom range toggle (OFF = full timeline)
 *  - Custom start/end time (only when ON)
 *  - Format, resolution, fps, bitrate
 *  - Save location
 *  - Cancel button during export
 */
@Composable
fun ExportDialog(
    isExporting: Boolean,
    exportProgress: Float,
    exportMessage: String,
    currentResolution: String,
    currentFps: Int,
    currentBitrate: Int,
    currentFormat: String,
    aspectRatio: String,
    timelineDurationMs: Long,
    currentFolderUri: String?,
    fileName: String,
    startMs: Long,
    endMs: Long,
    // 🆕 Custom range toggle
    useCustomRange: Boolean,
    onUseCustomRangeChange: (Boolean) -> Unit,
    onFileNameChange: (String) -> Unit,
    onStartChange: (Long) -> Unit,
    onEndChange: (Long) -> Unit,
    onResolutionChange: (String) -> Unit,
    onFpsChange: (Int) -> Unit,
    onBitrateChange: (Int) -> Unit,
    onFormatChange: (String) -> Unit,
    onChooseFolder: () -> Unit,
    onResetFolder: () -> Unit,
    onStartExport: () -> Unit,
    onCancelExport: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var messageCopied by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xE6000000))
            .pointerInput(Unit) {
                // Block touches behind
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0F0F0F))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        top = WindowInsets.statusBars
                            .asPaddingValues()
                            .calculateTopPadding()
                    )
            ) {

                // ═══ HEADER ═══
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .background(Color(0xFF1A1A1A))
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "💾 Export Video",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )

                    if (!isExporting) {
                        Box(
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .height(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF7C3AED))
                                .pointerInput(Unit) {
                                    detectTapGestures { onStartExport() }
                                }
                                .padding(horizontal = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "▶ Export",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF222222))
                            .pointerInput(isExporting) {
                                detectTapGestures {
                                    if (!isExporting) onDismiss()
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "✕",
                            color = if (isExporting) Color(0xFF555555) else Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // ═══ CONTENT ═══
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {

                    // ═══ FILE NAME ═══
                    SectionLabel("File Name")
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF1A1A1A))
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("📄", fontSize = 14.sp)
                            Box(modifier = Modifier.weight(1f)) {
                                if (fileName.isBlank()) {
                                    Text(
                                        "MoodyExport_${System.currentTimeMillis()}",
                                        color = Color(0xFF555555),
                                        fontSize = 11.sp
                                    )
                                }
                                BasicTextField(
                                    value = fileName,
                                    onValueChange = onFileNameChange,
                                    singleLine = true,
                                    enabled = !isExporting,
                                    textStyle = TextStyle(
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    cursorBrush = SolidColor(Color(0xFF7C3AED)),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            Text(
                                ".${if (currentFormat == "mov") "mov" else "mp4"}",
                                color = Color(0xFF666666),
                                fontSize = 10.sp
                            )
                        }
                    }

                    // ═══════════════════════════════════════════════
                    //  🆕 EXPORT RANGE TOGGLE
                    // ═══════════════════════════════════════════════
                    SectionLabel("Export Range")

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF1A1A1A))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                if (useCustomRange) "Custom range"
                                else "Full timeline",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                if (useCustomRange) "Set specific start & end time"
                                else "Export complete project (${formatDuration(timelineDurationMs)})",
                                color = Color(0xFF888888),
                                fontSize = 10.sp
                            )
                        }

                        // Toggle button
                        Box(
                            modifier = Modifier
                                .height(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (useCustomRange) Color(0xFF7C3AED)
                                    else Color(0xFF2A2A2A)
                                )
                                .pointerInput(useCustomRange, isExporting) {
                                    if (!isExporting) {
                                        detectTapGestures {
                                            onUseCustomRangeChange(!useCustomRange)
                                        }
                                    }
                                }
                                .padding(horizontal = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                if (useCustomRange) "ON" else "OFF",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // 🆕 Custom range fields — only when toggle is ON
                    if (useCustomRange) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TimeInputField(
                                label = "Start",
                                valueMs = startMs,
                                maxMs = timelineDurationMs,
                                enabled = !isExporting,
                                onChange = onStartChange,
                                modifier = Modifier.weight(1f)
                            )
                            TimeInputField(
                                label = "End",
                                valueMs = endMs,
                                maxMs = timelineDurationMs,
                                enabled = !isExporting,
                                onChange = onEndChange,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        // Duration display
                        val exportDuration = (endMs - startMs).coerceAtLeast(0L)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF0F1A2A))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "⏱️ Export Duration: ${formatDuration(exportDuration)}",
                                color = Color(0xFF60EFFF),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            Box(
                                modifier = Modifier
                                    .height(24.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFF7C3AED).copy(alpha = 0.25f))
                                    .pointerInput(timelineDurationMs) {
                                        detectTapGestures {
                                            onStartChange(0L)
                                            onEndChange(timelineDurationMs)
                                        }
                                    }
                                    .padding(horizontal = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "Full",
                                    color = Color(0xFF7C3AED),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // ═══ FORMAT ═══
                    SectionLabel("Format")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Chip(
                            label = "MP4",
                            isActive = currentFormat == "mp4",
                            enabled = !isExporting,
                            onClick = { onFormatChange("mp4") }
                        )
                        Chip(
                            label = "MOV",
                            isActive = currentFormat == "mov",
                            enabled = !isExporting,
                            onClick = { onFormatChange("mov") }
                        )
                    }

                    // ═══ RESOLUTION ═══
                    SectionLabel("Resolution")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ExportSettings.RESOLUTIONS.forEach { preset ->
                            Chip(
                                label = preset.label,
                                isActive = currentResolution == preset.key,
                                enabled = !isExporting,
                                onClick = { onResolutionChange(preset.key) }
                            )
                        }
                    }

                    // ═══ FPS ═══
                    SectionLabel("Frame Rate")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ExportSettings.FPS_OPTIONS.forEach { fps ->
                            Chip(
                                label = "$fps fps",
                                isActive = currentFps == fps,
                                enabled = !isExporting,
                                onClick = { onFpsChange(fps) }
                            )
                        }
                    }

                    // ═══ BITRATE ═══
                    SectionLabel("Bitrate")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Slider(
                            value = currentBitrate.toFloat().coerceIn(1000f, 50000f),
                            onValueChange = { onBitrateChange(it.toInt()) },
                            valueRange = 1000f..50000f,
                            enabled = !isExporting,
                            modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFF7C3AED),
                                activeTrackColor = Color(0xFF7C3AED),
                                inactiveTrackColor = Color(0xFF303030)
                            )
                        )
                        Text(
                            "${currentBitrate / 1000} Mbps",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(60.dp)
                        )
                    }

                    val autoBit = ExportSettings.autoBitrate(currentResolution, currentFps)
                    Box(
                        modifier = Modifier
                            .height(26.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF1A1A1A))
                            .pointerInput(currentResolution, currentFps, isExporting) {
                                if (!isExporting) {
                                    detectTapGestures { onBitrateChange(autoBit) }
                                }
                            }
                            .padding(horizontal = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "⚡ Auto: ${autoBit / 1000} Mbps",
                            color = Color(0xFF4F9DFF),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // ═══ SAVE LOCATION ═══
                    SectionLabel("Save Location")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF1A1A1A))
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("📁", fontSize = 16.sp)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (currentFolderUri == null) "Movies/MoodyEditor"
                                else "Custom folder",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (currentFolderUri == null) "Default location"
                                else currentFolderUri.takeLast(36),
                                color = Color(0xFF666666),
                                fontSize = 8.sp,
                                maxLines = 1
                            )
                        }
                        Box(
                            modifier = Modifier
                                .height(28.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF4F9DFF).copy(alpha = 0.2f))
                                .pointerInput(isExporting) {
                                    if (!isExporting) {
                                        detectTapGestures { onChooseFolder() }
                                    }
                                }
                                .padding(horizontal = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "Change",
                                color = Color(0xFF4F9DFF),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    if (currentFolderUri != null && !isExporting) {
                        Box(
                            modifier = Modifier
                                .height(26.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFFFF6B6B).copy(alpha = 0.15f))
                                .pointerInput(Unit) {
                                    detectTapGestures { onResetFolder() }
                                }
                                .padding(horizontal = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "↺ Use default folder",
                                color = Color(0xFFFF6B6B),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // ═══ EXPORTING PROGRESS ═══
                    if (isExporting) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF1A1A1A))
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    "🎬 Exporting…",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    "${(exportProgress * 100).toInt()}%",
                                    color = Color(0xFF7C3AED),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(Color(0xFF0A0A0A))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(
                                            exportProgress.coerceIn(0f, 1f)
                                        )
                                        .height(6.dp)
                                        .background(Color(0xFF7C3AED))
                                )
                            }
                            if (exportMessage.isNotBlank()) {
                                Text(
                                    exportMessage,
                                    color = Color(0xFF888888),
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }

                    // ═══ ERROR / MESSAGE ═══
                    if (!isExporting && exportMessage.isNotBlank()) {
                        val msgColor = when {
                            exportMessage.startsWith("✅") -> Color(0xFF22C55E)
                            exportMessage.startsWith("❌") -> Color(0xFFFF6B6B)
                            else -> Color(0xFF888888)
                        }
                        val isError = exportMessage.startsWith("❌")

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isError) Color(0xFF2A0F0F)
                                    else Color(0xFF1A1A1A)
                                )
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    if (isError) "❌ Error" else "ℹ️ Status",
                                    color = msgColor,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f)
                                )
                                Box(
                                    modifier = Modifier
                                        .height(24.dp)
                                        .clip(RoundedCornerShape(5.dp))
                                        .background(
                                            if (messageCopied) Color(0xFF22C55E)
                                            else msgColor.copy(alpha = 0.15f)
                                        )
                                        .pointerInput(exportMessage, messageCopied) {
                                            detectTapGestures {
                                                try {
                                                    val clipboard = context
                                                        .getSystemService(
                                                            Context.CLIPBOARD_SERVICE
                                                        ) as ClipboardManager
                                                    clipboard.setPrimaryClip(
                                                        ClipData.newPlainText(
                                                            "Export Message",
                                                            exportMessage
                                                        )
                                                    )
                                                    messageCopied = true
                                                } catch (_: Exception) {
                                                }
                                            }
                                        }
                                        .padding(horizontal = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        if (messageCopied) "✅ Copied" else "📋 Copy",
                                        color = if (messageCopied) Color.White else msgColor,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 200.dp)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                Text(
                                    exportMessage,
                                    color = msgColor,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    lineHeight = 14.sp
                                )
                            }
                        }
                    }
                }

                // ═══ BOTTOM: Start / Cancel Button ═══
                if (isExporting) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF1A1A1A))
                            .padding(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFFFF3B3B))
                                .pointerInput(Unit) {
                                    detectTapGestures { onCancelExport() }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "✕  Cancel Export",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF1A1A1A))
                            .padding(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF7C3AED))
                                .pointerInput(Unit) {
                                    detectTapGestures { onStartExport() }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "▶  Start Export",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  HELPERS
// ═══════════════════════════════════════════════════════════════
@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        color = Color(0xFF888888),
        fontSize = 9.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 2.dp, bottom = 4.dp)
    )
}

@Composable
private fun Chip(
    label: String,
    isActive: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .height(32.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                when {
                    !enabled -> Color(0xFF151515)
                    isActive -> Color(0xFF7C3AED)
                    else -> Color(0xFF1A1A1A)
                }
            )
            .pointerInput(label, enabled) {
                if (enabled) detectTapGestures { onClick() }
            }
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (!enabled) Color(0xFF555555) else Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * Time input field with MM:SS format.
 */
@Composable
private fun TimeInputField(
    label: String,
    valueMs: Long,
    maxMs: Long,
    enabled: Boolean,
    onChange: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var text by remember(valueMs) { mutableStateOf(msToTimeStr(valueMs)) }
    var isFocused by remember { mutableStateOf(false) }

    if (!isFocused && text != msToTimeStr(valueMs)) {
        text = msToTimeStr(valueMs)
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            label,
            color = Color(0xFF888888),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF1A1A1A))
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            BasicTextField(
                value = text,
                onValueChange = { newText ->
                    text = newText.filter { it.isDigit() || it == ':' || it == '.' }
                },
                singleLine = true,
                enabled = enabled,
                textStyle = TextStyle(
                    color = if (enabled) Color.White else Color(0xFF555555),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                ),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number
                ),
                cursorBrush = SolidColor(Color(0xFF7C3AED)),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusEvent { focusState ->
                        isFocused = focusState.isFocused
                        if (!focusState.isFocused) {
                            val parsed = parseTimeStr(text)
                            if (parsed != null) {
                                val clamped = parsed.coerceIn(0L, maxMs)
                                onChange(clamped)
                                text = msToTimeStr(clamped)
                            } else {
                                text = msToTimeStr(valueMs)
                            }
                        }
                    }
            )
        }
    }
}

private fun msToTimeStr(ms: Long): String {
    val totalSec = ms / 1000
    val m = totalSec / 60
    val s = totalSec % 60
    return "%d:%02d".format(m, s)
}

private fun parseTimeStr(str: String): Long? {
    val s = str.trim()
    if (s.isBlank()) return null
    return try {
        val parts = s.split(":")
        when (parts.size) {
            1 -> {
                val sec = parts[0].toDouble()
                (sec * 1000).toLong()
            }

            2 -> {
                val m = parts[0].toInt()
                val sec = parts[1].toDouble()
                ((m * 60 + sec) * 1000).toLong()
            }

            else -> null
        }
    } catch (e: Exception) {
        null
    }
}

private fun formatDuration(ms: Long): String {
    val totalSec = ms / 1000
    val m = totalSec / 60
    val s = totalSec % 60
    return "%d:%02d".format(m, s)
}