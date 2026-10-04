package com.moody.moodyvideoeditor.ui.features

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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

data class ExportConfig(
    val mode: String,
    val fileName: String,
    val startMs: Long,
    val endMs: Long,
    val useCustomRange: Boolean,
    val videoFormat: String,
    val resolution: String,
    val fps: Int,
    val bitrateKbps: Int,
    val audioFormat: String,
    val audioBitrateKbps: Int,
    val imageFormat: String,
    val jpegQuality: Int,
    val customFolderUri: String?,
    val aspectRatio: String
)

@Composable
fun ExportDialog(
    isExporting: Boolean,
    exportProgress: Float,
    exportMessage: String,
    timelineDurationMs: Long,
    aspectRatio: String,
    startMs: Long,
    endMs: Long,
    useCustomRange: Boolean,
    onUseCustomRangeChange: (Boolean) -> Unit,
    onStartChange: (Long) -> Unit,
    onEndChange: (Long) -> Unit,
    currentFolderUri: String?,
    onChooseFolder: () -> Unit,
    onResetFolder: () -> Unit,
    onStartExport: (ExportConfig) -> Unit,
    onCancelExport: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    var exportMode by remember { mutableStateOf("video") }
    var audioFormat by remember { mutableStateOf("mp3") }
    var audioBitrateKbps by remember { mutableStateOf(192) }
    var imageFormat by remember { mutableStateOf("png") }
    var jpegQuality by remember { mutableStateOf(90) }

    var fileName by remember {
        mutableStateOf("MoodyExport_${System.currentTimeMillis()}")
    }

    var videoFormat by remember { mutableStateOf("mp4") }
    var resolution by remember { mutableStateOf("720p") }
    var fps by remember { mutableStateOf(30) }
    var bitrateKbps by remember { mutableStateOf(8000) }

    var messageCopied by remember { mutableStateOf(false) }

    LaunchedEffect(useCustomRange, timelineDurationMs) {
        if (useCustomRange && endMs <= 0L) {
            onStartChange(0L)
            onEndChange(timelineDurationMs)
        }
    }

    fun doExport() {
        val config = ExportConfig(
            mode = exportMode,
            fileName = fileName.ifBlank {
                "MoodyExport_${System.currentTimeMillis()}"
            },
            startMs = startMs,
            endMs = endMs,
            useCustomRange = useCustomRange,
            videoFormat = videoFormat,
            resolution = resolution,
            fps = fps,
            bitrateKbps = bitrateKbps,
            audioFormat = audioFormat,
            audioBitrateKbps = audioBitrateKbps,
            imageFormat = imageFormat,
            jpegQuality = jpegQuality,
            customFolderUri = currentFolderUri,
            aspectRatio = aspectRatio
        )
        onStartExport(config)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xE6000000))
            .pointerInput(Unit) {}
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

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .background(Color(0xFF1A1A1A))
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "💾 Export",
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
                                    detectTapGestures { doExport() }
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

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {

                    SectionLabel("What to Export")

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ModeCard(
                            icon = "🎬",
                            label = "Video",
                            desc = "MP4 / MOV",
                            active = exportMode == "video",
                            enabled = !isExporting,
                            modifier = Modifier.weight(1f)
                        ) { exportMode = "video" }

                        ModeCard(
                            icon = "🎵",
                            label = "Audio",
                            desc = "MP3 / M4A",
                            active = exportMode == "audio",
                            enabled = !isExporting,
                            modifier = Modifier.weight(1f)
                        ) { exportMode = "audio" }

                        ModeCard(
                            icon = "🖼️",
                            label = "Images",
                            desc = "PNG / JPEG",
                            active = exportMode == "image",
                            enabled = !isExporting,
                            modifier = Modifier.weight(1f)
                        ) { exportMode = "image" }
                    }

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
                                BasicTextField(
                                    value = fileName,
                                    onValueChange = { fileName = it },
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
                                ".${currentExt(exportMode, videoFormat, audioFormat, imageFormat)}",
                                color = Color(0xFF666666),
                                fontSize = 10.sp
                            )
                        }
                    }

                    SectionLabel("Export Range")

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        RangeButton(
                            title = "▶  Full Duration",
                            subtitle = formatDuration(timelineDurationMs),
                            active = !useCustomRange,
                            enabled = !isExporting,
                            modifier = Modifier.weight(1f)
                        ) {
                            if (useCustomRange) onUseCustomRangeChange(false)
                        }

                        RangeButton(
                            title = "✂  Custom Duration",
                            subtitle = if (useCustomRange)
                                "${formatDuration((endMs - startMs).coerceAtLeast(0L))} selected"
                            else "Set specific range",
                            active = useCustomRange,
                            enabled = !isExporting,
                            modifier = Modifier.weight(1f)
                        ) {
                            if (!useCustomRange) onUseCustomRangeChange(true)
                        }
                    }

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

                        val exportDur = (endMs - startMs).coerceAtLeast(0L)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF0F1A2A))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "⏱️ Duration: ${formatDuration(exportDur)}",
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

                    when (exportMode) {

                        "video" -> {
                            SectionLabel("Video Format")
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Chip(
                                    label = "MP4",
                                    isActive = videoFormat == "mp4",
                                    enabled = !isExporting
                                ) { videoFormat = "mp4" }
                                Chip(
                                    label = "MOV",
                                    isActive = videoFormat == "mov",
                                    enabled = !isExporting
                                ) { videoFormat = "mov" }
                            }

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
                                        isActive = resolution == preset.key,
                                        enabled = !isExporting
                                    ) {
                                        resolution = preset.key
                                        bitrateKbps = ExportSettings.autoBitrate(
                                            resolution, fps
                                        )
                                    }
                                }
                            }

                            SectionLabel("Frame Rate")
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                ExportSettings.FPS_OPTIONS.forEach { f ->
                                    Chip(
                                        label = "$f fps",
                                        isActive = fps == f,
                                        enabled = !isExporting
                                    ) {
                                        fps = f
                                        bitrateKbps = ExportSettings.autoBitrate(
                                            resolution, fps
                                        )
                                    }
                                }
                            }

                            SectionLabel("Bitrate")
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Slider(
                                    value = bitrateKbps.toFloat()
                                        .coerceIn(1000f, 50000f),
                                    onValueChange = { bitrateKbps = it.toInt() },
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
                                    "${bitrateKbps / 1000} Mbps",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(start = 4.dp)
                                )
                            }

                            val autoBit = ExportSettings.autoBitrate(resolution, fps)
                            Box(
                                modifier = Modifier
                                    .height(26.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFF1A1A1A))
                                    .pointerInput(resolution, fps, isExporting) {
                                        if (!isExporting) {
                                            detectTapGestures {
                                                bitrateKbps = autoBit
                                            }
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
                        }

                        "audio" -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF0F1A2A))
                                    .padding(10.dp)
                            ) {
                                Text(
                                    "🎵 All timeline audio will be mixed into one file. Gaps stay silent.",
                                    color = Color(0xFF60EFFF),
                                    fontSize = 10.sp,
                                    lineHeight = 14.sp
                                )
                            }

                            SectionLabel("Audio Format")
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Chip(
                                    label = "MP3",
                                    isActive = audioFormat == "mp3",
                                    enabled = !isExporting
                                ) { audioFormat = "mp3" }
                                Chip(
                                    label = "M4A (AAC)",
                                    isActive = audioFormat == "m4a",
                                    enabled = !isExporting
                                ) { audioFormat = "m4a" }
                            }

                            SectionLabel("Audio Bitrate")
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Chip(
                                    label = "128 kbps",
                                    isActive = audioBitrateKbps == 128,
                                    enabled = !isExporting
                                ) { audioBitrateKbps = 128 }
                                Chip(
                                    label = "192 kbps",
                                    isActive = audioBitrateKbps == 192,
                                    enabled = !isExporting
                                ) { audioBitrateKbps = 192 }
                                Chip(
                                    label = "256 kbps",
                                    isActive = audioBitrateKbps == 256,
                                    enabled = !isExporting
                                ) { audioBitrateKbps = 256 }
                                Chip(
                                    label = "320 kbps",
                                    isActive = audioBitrateKbps == 320,
                                    enabled = !isExporting
                                ) { audioBitrateKbps = 320 }
                            }
                        }

                        "image" -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF0F1A2A))
                                    .padding(10.dp)
                            ) {
                                Text(
                                    "🖼️ Each frame is saved as a separate image: Pictures/MoodyEditor/<name>/",
                                    color = Color(0xFF60EFFF),
                                    fontSize = 10.sp,
                                    lineHeight = 14.sp
                                )
                            }

                            SectionLabel("Image Format")
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Chip(
                                    label = "PNG",
                                    isActive = imageFormat == "png",
                                    enabled = !isExporting
                                ) { imageFormat = "png" }
                                Chip(
                                    label = "JPEG",
                                    isActive = imageFormat == "jpeg",
                                    enabled = !isExporting
                                ) { imageFormat = "jpeg" }
                            }

                            if (imageFormat == "jpeg") {
                                SectionLabel("JPEG Quality")
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Chip(
                                        label = "85% (Fast)",
                                        isActive = jpegQuality == 85,
                                        enabled = !isExporting
                                    ) { jpegQuality = 85 }
                                    Chip(
                                        label = "90% (Balanced)",
                                        isActive = jpegQuality == 90,
                                        enabled = !isExporting
                                    ) { jpegQuality = 90 }
                                    Chip(
                                        label = "95% (High)",
                                        isActive = jpegQuality == 95,
                                        enabled = !isExporting
                                    ) { jpegQuality = 95 }
                                    Chip(
                                        label = "100% (Best)",
                                        isActive = jpegQuality == 100,
                                        enabled = !isExporting
                                    ) { jpegQuality = 100 }
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0xFF1A1A1A))
                                        .padding(8.dp)
                                ) {
                                    Text(
                                        "ℹ️ PNG is lossless — no quality setting needed.",
                                        color = Color(0xFFAAAAAA),
                                        fontSize = 10.sp
                                    )
                                }
                            }

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
                                        isActive = resolution == preset.key,
                                        enabled = !isExporting
                                    ) { resolution = preset.key }
                                }
                            }

                            SectionLabel("Frame Rate (images/sec)")
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                ExportSettings.FPS_OPTIONS.forEach { f ->
                                    Chip(
                                        label = "$f fps",
                                        isActive = fps == f,
                                        enabled = !isExporting
                                    ) { fps = f }
                                }
                            }

                            val durMs = if (useCustomRange)
                                (endMs - startMs).coerceAtLeast(0L)
                            else timelineDurationMs
                            val frameCount =
                                ((durMs / 1000.0) * fps).toInt().coerceAtLeast(0)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFF1A1A1A))
                                    .padding(8.dp)
                            ) {
                                Text(
                                    "📊 Estimate: ~$frameCount images (${formatDuration(durMs)} × $fps fps)",
                                    color = Color(0xFFFFD166),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

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
                                text = when {
                                    currentFolderUri != null -> "Custom folder"
                                    exportMode == "audio" -> "Music/MoodyEditor"
                                    exportMode == "image" -> "Pictures/MoodyEditor"
                                    else -> "Movies/MoodyEditor"
                                },
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (currentFolderUri == null)
                                    "Default location"
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
                                    "🎬 Exporting...",
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
                                        if (messageCopied) "✅ Copied"
                                        else "📋 Copy",
                                        color = if (messageCopied) Color.White
                                        else msgColor,
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
                                    detectTapGestures { doExport() }
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
private fun ModeCard(
    icon: String,
    label: String,
    desc: String,
    active: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(84.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(
                when {
                    !enabled -> Color(0xFF151515)
                    active -> Color(0xFF7C3AED)
                    else -> Color(0xFF1A1A1A)
                }
            )
            .then(
                if (active && enabled) Modifier.border(
                    1.5.dp,
                    Color(0xFFA78BFA),
                    RoundedCornerShape(10.dp)
                ) else Modifier
            )
            .pointerInput(label, enabled) {
                if (enabled) {
                    detectTapGestures { onClick() }
                }
            }
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(icon, fontSize = 22.sp)
            Text(
                label,
                color = if (enabled) Color.White else Color(0xFF555555),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                desc,
                color = if (active && enabled)
                    Color.White.copy(alpha = 0.85f)
                else Color(0xFF888888),
                fontSize = 8.sp,
                textAlign = TextAlign.Center,
                lineHeight = 10.sp
            )
        }
    }
}

@Composable
private fun RangeButton(
    title: String,
    subtitle: String,
    active: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(56.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(
                when {
                    !enabled -> Color(0xFF151515)
                    active -> Color(0xFF7C3AED)
                    else -> Color(0xFF1A1A1A)
                }
            )
            .pointerInput(title, enabled) {
                if (enabled) {
                    detectTapGestures { onClick() }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                title,
                color = if (enabled) Color.White else Color(0xFF555555),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                subtitle,
                color = if (active && enabled)
                    Color.White.copy(alpha = 0.9f)
                else Color(0xFF888888),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
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
                    val filtered = newText.filter {
                        it.isDigit() || it == ':' || it == '.'
                    }
                    text = filtered
                    parseTimeStr(filtered)?.let { parsed ->
                        onChange(parsed.coerceIn(0L, maxMs))
                    }
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

private fun currentExt(
    exportMode: String,
    videoFormat: String,
    audioFormat: String,
    imageFormat: String
): String {
    return when (exportMode) {
        "audio" -> if (audioFormat == "m4a") "m4a" else "mp3"
        "image" -> if (imageFormat == "jpeg") "jpg" else "png"
        else -> if (videoFormat == "mov") "mov" else "mp4"
    }
}