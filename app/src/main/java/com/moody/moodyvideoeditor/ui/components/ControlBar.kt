package com.moody.moodyvideoeditor.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.moody.moodyvideoeditor.utils.VideoUtils

@Composable
fun ControlBar(
    onMediaClick: () -> Unit,
    currentRatio: String = "16:9",
    onRatioClick: () -> Unit = {},
    isPlaying: Boolean = false,
    canUndo: Boolean = false,
    canRedo: Boolean = false,
    seekStepSeconds: Int = 3,
    onSeekBy: (Long) -> Unit = {},
    onPlayPause: () -> Unit = {},
    onUndo: () -> Unit = {},
    onRedo: () -> Unit = {},
    isMuted: Boolean = false,
    hasKeyframeAtPlayhead: Boolean = false,
    onMuteToggle: () -> Unit = {},
    onKeyframe: () -> Unit = {},
    onDelete: () -> Unit = {},
    onMagnet: () -> Unit = {},
    onDuplicate: () -> Unit = {},
    hasVideo: Boolean = true,
    currentPosMs: Long = 0L,
    totalDurationMs: Long = 0L
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(Color(0xFF0A0A0A))
    ) {
        Row(
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            PillButton("＋Media", onMediaClick, horizontalPadding = 6.dp)
            PillButton(currentRatio, onRatioClick, horizontalPadding = 6.dp)
            IconButton(onClick = onMagnet, enabled = hasVideo, modifier = Modifier.size(26.dp)) {
                Text("🧲", fontSize = 14.sp)
            }
            IconButton(onClick = onDuplicate, enabled = hasVideo, modifier = Modifier.size(26.dp)) {
                Icon(
                    Icons.Filled.ContentCopy,
                    "Duplicate",
                    tint = if (hasVideo) Color.White else Color(0xFF444444),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        Row(
            modifier = Modifier.align(Alignment.Center),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            IconButton(onClick = onUndo, enabled = canUndo, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Filled.Undo, "Undo", tint = if (canUndo) Color.White else Color(0xFF444444))
            }
            PillButton(
                "-${seekStepSeconds}s",
                onClick = { onSeekBy(-seekStepSeconds.coerceAtLeast(1) * 1000L) },
                enabled = hasVideo,
                horizontalPadding = 4.dp
            )
            IconButton(onClick = onPlayPause, enabled = hasVideo, modifier = Modifier.size(32.dp)) {
                Icon(
                    if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    "Play or pause",
                    tint = if (hasVideo) Color(0xFF7C3AED) else Color(0xFF444444)
                )
            }
            PillButton(
                "+${seekStepSeconds}s",
                onClick = { onSeekBy(seekStepSeconds.coerceAtLeast(1) * 1000L) },
                enabled = hasVideo,
                horizontalPadding = 4.dp
            )
            IconButton(onClick = onRedo, enabled = canRedo, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Filled.Redo, "Redo", tint = if (canRedo) Color.White else Color(0xFF444444))
            }
        }

        Row(
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            Text(
                "${VideoUtils.formatDuration(currentPosMs)}/${VideoUtils.formatDuration(totalDurationMs)}",
                color = Color(0xFF888888),
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            IconButton(onClick = onMuteToggle, enabled = hasVideo, modifier = Modifier.size(26.dp)) {
                Icon(
                    if (isMuted) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
                    if (isMuted) "Unmute" else "Mute",
                    tint = if (hasVideo) Color.White else Color(0xFF444444),
                    modifier = Modifier.size(17.dp)
                )
            }
            IconButton(onClick = onKeyframe, enabled = hasVideo, modifier = Modifier.size(26.dp)) {
                Text(
                    "◆",
                    color = when {
                        hasKeyframeAtPlayhead -> Color(0xFF4F9DFF)
                        hasVideo -> Color.White
                        else -> Color(0xFF444444)
                    },
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            IconButton(onClick = onDelete, enabled = hasVideo, modifier = Modifier.size(26.dp)) {
                Icon(
                    Icons.Filled.Delete,
                    "Delete",
                    tint = if (hasVideo) Color(0xFFFF6B6B) else Color(0xFF444444),
                    modifier = Modifier.size(17.dp)
                )
            }
        }
    }
}

@Composable
private fun PillButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    highlight: Color = Color.White,
    horizontalPadding: androidx.compose.ui.unit.Dp = 10.dp
) {
    Box(
        modifier = Modifier
            .height(32.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF1A1A1A))
            .pointerInput(text, enabled) {
                if (enabled) detectTapGestures { onClick() }
            }
            .padding(horizontal = horizontalPadding),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = if (enabled) highlight else Color(0xFF555555),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}