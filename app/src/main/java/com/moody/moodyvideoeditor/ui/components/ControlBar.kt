package com.moody.moodyvideoeditor.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
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
    onAddVisualLayer: () -> Unit = {},
    onAddAudioLayer: () -> Unit = {},
    currentRatio: String = "16:9",
    onRatioClick: () -> Unit = {},
    // 🆕 5 icons moved from PlaybackControls
    isMuted: Boolean = false,
    onMuteToggle: () -> Unit = {},
    onMagnet: () -> Unit = {},
    onDelete: () -> Unit = {},
    onDuplicate: () -> Unit = {},
    onCopy: () -> Unit = {},
    onCut: () -> Unit = {},
    onPaste: () -> Unit = {},
    canCopy: Boolean = false,
    canPaste: Boolean = false,
    hasVideo: Boolean = true,
    currentPosMs: Long = 0L,
    totalDurationMs: Long = 0L
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(Color(0xFF0A0A0A))
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        PillButton("＋ Media", onMediaClick)
        PillButton("＋V", onAddVisualLayer)
        PillButton("＋A", onAddAudioLayer)
        PillButton("🖼️ $currentRatio", onRatioClick)

        // ─── Moved from PlaybackControls ───
        IconButton(
            onClick = onMuteToggle,
            enabled = hasVideo,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                if (isMuted) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
                "Mute",
                tint = if (isMuted) Color(0xFFFF6B6B) else Color(0xFF7C3AED),
                modifier = Modifier.size(18.dp)
            )
        }

        PillButton("🧲", onMagnet)

        IconButton(
            onClick = onDuplicate,
            enabled = hasVideo,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                Icons.Filled.ContentCopy, "Duplicate",
                tint = if (hasVideo) Color(0xFF7C3AED) else Color(0xFF444444),
                modifier = Modifier.size(18.dp)
            )
        }

        if (canPaste) {
            PillButton("Paste", onPaste, highlight = Color(0xFF22C55E))
        } else {
            PillButton("Copy", onCopy, enabled = hasVideo && canCopy)
            PillButton("Cut", onCut, enabled = hasVideo && canCopy, highlight = Color(0xFFFFB84D))
        }

        IconButton(
            onClick = onDelete,
            enabled = hasVideo,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                Icons.Filled.Delete, "Delete",
                tint = if (hasVideo) Color(0xFFFF6B6B) else Color(0xFF444444),
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        // Time display
        Text(
            "${VideoUtils.formatDuration(currentPosMs)} / ${
                VideoUtils.formatDuration(
                    totalDurationMs
                )
            }",
            color = Color(0xFF888888),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun PillButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    highlight: Color = Color.White
) {
    Box(
        modifier = Modifier
            .height(32.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF1A1A1A))
            .pointerInput(text, enabled) {
                if (enabled) detectTapGestures { onClick() }
            }
            .padding(horizontal = 10.dp),
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