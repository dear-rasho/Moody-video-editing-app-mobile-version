package com.moody.moodyvideoeditor.ui.features

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moody.moodyvideoeditor.ui.components.FeaturePanel
import com.moody.moodyvideoeditor.utils.ExportSettings

@Composable
fun ExportPanel(
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
    onResolutionChange: (String) -> Unit,
    onFpsChange: (Int) -> Unit,
    onBitrateChange: (Int) -> Unit,
    onFormatChange: (String) -> Unit,
    onChooseFolder: () -> Unit,
    onResetFolder: () -> Unit,
    onStartExport: () -> Unit,
    onClose: () -> Unit
) {
    FeaturePanel(title = "💾 Export Video", onClose = onClose) {

        if (isExporting) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Exporting… ${(exportProgress * 100).toInt()}%",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color(0xFF1A1A1A))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(exportProgress.coerceIn(0f, 1f))
                            .height(6.dp)
                            .background(Color(0xFF7C3AED))
                    )
                }
                Text(
                    text = exportMessage,
                    color = Color(0xFF888888),
                    fontSize = 10.sp
                )
            }
            return@FeaturePanel
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 260.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {

            // ═══ INFO BAR ═══
            val targetDims = ExportSettings.targetDimensions(
                currentResolution, aspectRatio
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF181818))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    "📐 $aspectRatio  ·  ${targetDims.first}×${targetDims.second}  ·  ${
                        formatDuration(
                            timelineDurationMs
                        )
                    }",
                    color = Color(0xFF4F9DFF),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
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
                    .height(24.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF181818))
                    .pointerInput(currentResolution, currentFps) {
                        detectTapGestures { onBitrateChange(autoBit) }
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

            // ═══ FORMAT ═══
            SectionLabel("Format")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Chip(
                    label = "MP4",
                    isActive = currentFormat == "mp4",
                    onClick = { onFormatChange("mp4") }
                )
                Chip(
                    label = "MOV",
                    isActive = currentFormat == "mov",
                    onClick = { onFormatChange("mov") }
                )
            }

            // ═══ SAVE LOCATION ═══
            SectionLabel("Save Location")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF181818))
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
                        .pointerInput(Unit) {
                            detectTapGestures { onChooseFolder() }
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

            if (currentFolderUri != null) {
                Box(
                    modifier = Modifier
                        .height(24.dp)
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

            Spacer(Modifier.height(4.dp))

            // ═══ START BUTTON ═══
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

            if (exportMessage.isNotBlank()) {
                Text(
                    text = exportMessage,
                    color = when {
                        exportMessage.startsWith("✅") -> Color(0xFF22C55E)
                        exportMessage.startsWith("❌") -> Color(0xFFFF6B6B)
                        else -> Color(0xFF888888)
                    },
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
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
        modifier = Modifier.padding(start = 2.dp)
    )
}

@Composable
private fun Chip(
    label: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .height(30.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isActive) Color(0xFF7C3AED) else Color(0xFF181818))
            .pointerInput(label) {
                detectTapGestures { onClick() }
            }
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

private fun formatDuration(ms: Long): String {
    val totalSec = ms / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return "%d:%02d".format(min, sec)
}