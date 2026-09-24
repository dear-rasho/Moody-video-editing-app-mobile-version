package com.moody.moodyvideoeditor.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moody.moodyvideoeditor.utils.VideoUtils

@Composable
fun PlaybackControls(
    isPlaying: Boolean,
    isMuted: Boolean,
    currentPosMs: Long,
    totalDurationMs: Long,
    hasVideo: Boolean,
    canUndo: Boolean,
    canRedo: Boolean,
    hasKeyframeAtPlayhead: Boolean = false,
    onPlayPause: () -> Unit,
    onSplit: () -> Unit,
    onDelete: () -> Unit,
    onDuplicate: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onMuteToggle: () -> Unit,
    onKeyframe: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(Color(0xFF0A0A0A))
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        IconButton(onClick = onMuteToggle, enabled = hasVideo) {
            Icon(
                if (isMuted) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
                "Mute",
                tint = if (isMuted) Color(0xFFFF6B6B) else Color(0xFF7C3AED),
                modifier = Modifier.size(22.dp)
            )
        }

        IconButton(onClick = onSplit, enabled = hasVideo) {
            Icon(
                Icons.Filled.ContentCut, "Split",
                tint = if (hasVideo) Color(0xFFFFD166) else Color(0xFF444444),
                modifier = Modifier.size(24.dp)
            )
        }

        IconButton(onClick = onDuplicate, enabled = hasVideo) {
            Icon(
                Icons.Filled.ContentCopy, "Duplicate",
                tint = if (hasVideo) Color(0xFF7C3AED) else Color(0xFF444444),
                modifier = Modifier.size(22.dp)
            )
        }

        // ◆ Keyframe button
        IconButton(onClick = onKeyframe, enabled = hasVideo) {
            Text(
                "◆",
                color = when {
                    hasKeyframeAtPlayhead -> Color(0xFF4F9DFF)
                    hasVideo -> Color.White
                    else -> Color(0xFF444444)
                },
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }

        IconButton(onClick = onPlayPause, enabled = hasVideo) {
            Icon(
                if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                "Play",
                tint = if (hasVideo) Color(0xFF7C3AED) else Color(0xFF444444),
                modifier = Modifier.size(34.dp)
            )
        }

        IconButton(onClick = onUndo, enabled = canUndo) {
            Icon(
                Icons.Filled.Undo, "Undo",
                tint = if (canUndo) Color.White else Color(0xFF444444),
                modifier = Modifier.size(22.dp)
            )
        }

        IconButton(onClick = onRedo, enabled = canRedo) {
            Icon(
                Icons.Filled.Redo, "Redo",
                tint = if (canRedo) Color.White else Color(0xFF444444),
                modifier = Modifier.size(22.dp)
            )
        }

        IconButton(onClick = onDelete, enabled = hasVideo) {
            Icon(
                Icons.Filled.Delete, "Delete",
                tint = if (hasVideo) Color(0xFFFF6B6B) else Color(0xFF444444),
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        Text(
            "${VideoUtils.formatDuration(currentPosMs)} / ${
                VideoUtils.formatDuration(
                    totalDurationMs
                )
            }",
            color = Color(0xFF888888),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}