package com.moody.moodyvideoeditor.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.moody.moodyvideoeditor.utils.TimelineZoom

@Composable
fun PlaybackControls(
    isPlaying: Boolean,
    isMuted: Boolean = false,
    currentPosMs: Long = 0L,
    totalDurationMs: Long = 0L,
    hasVideo: Boolean,
    canUndo: Boolean,
    canRedo: Boolean,
    hasKeyframeAtPlayhead: Boolean = false,
    onSelectBackward: () -> Unit = {},
    onSelectForward: () -> Unit = {},
    onSelectAll: () -> Unit = {},
    onSelectAllTrack: () -> Unit = {},
    onDeselectAll: () -> Unit = {},
    onLink: () -> Unit = {},
    onUnlink: () -> Unit = {},
    isLinked: Boolean = false,
    canLink: Boolean = false,
    // SPLIT
    onSplit: () -> Unit = {},
    canSplit: Boolean = false,
    // Trim
    onTrimLeft: () -> Unit = {},
    onTrimRight: () -> Unit = {},
    canTrimLeft: Boolean = false,
    canTrimRight: Boolean = false,
    // Zoom
    zoomSlider: Float = 0f,
    totalSec: Float = 0f,
    viewportContentWidthDp: Float = 320f,
    onZoomChange: (Float) -> Unit = {},
    // Core
    onPlayPause: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onKeyframe: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0A0A0A))
            .padding(horizontal = 2.dp, vertical = 1.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        // ─── ROW 1 — Play + Undo/Redo + Keyframe + Tools + Split/Trim + Link ───
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(30.dp)
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            // Keyframe
            IconButton(onClick = onKeyframe, enabled = hasVideo, modifier = Modifier.size(28.dp)) {
                Text(
                    "◆",
                    color = when {
                        hasKeyframeAtPlayhead -> Color(0xFF4F9DFF)
                        hasVideo -> Color.White
                        else -> Color(0xFF444444)
                    },
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Play/Pause
            IconButton(onClick = onPlayPause, enabled = hasVideo, modifier = Modifier.size(30.dp)) {
                Icon(
                    if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    "Play",
                    tint = if (hasVideo) Color(0xFF7C3AED) else Color(0xFF444444),
                    modifier = Modifier.size(22.dp)
                )
            }

            // Undo
            IconButton(onClick = onUndo, enabled = canUndo, modifier = Modifier.size(28.dp)) {
                Icon(
                    Icons.Filled.Undo, "Undo",
                    tint = if (canUndo) Color.White else Color(0xFF444444),
                    modifier = Modifier.size(16.dp)
                )
            }

            // Redo
            IconButton(onClick = onRedo, enabled = canRedo, modifier = Modifier.size(28.dp)) {
                Icon(
                    Icons.Filled.Redo, "Redo",
                    tint = if (canRedo) Color.White else Color(0xFF444444),
                    modifier = Modifier.size(16.dp)
                )
            }

            // Select tools
            SmallBtn("⏪") { onSelectBackward() }
            SmallBtn("⏩") { onSelectForward() }
            SmallBtn("☑") { onSelectAll() }
            SmallBtn("▤") { onSelectAllTrack() }
            SmallBtn("☐") { onDeselectAll() }

            // ★ SPLIT wapas ★
            SmallBtn(
                text = "][",
                enabled = canSplit,
                highlight = canSplit
            ) { onSplit() }

            // Trim
            SmallBtn(
                text = "[",
                enabled = canTrimLeft,
                highlight = canTrimLeft
            ) { onTrimLeft() }

            SmallBtn(
                text = "]",
                enabled = canTrimRight,
                highlight = canTrimRight
            ) { onTrimRight() }

            // Link / unlink
            if (isLinked) {
                SmallBtn("🔓", highlight = true) { onUnlink() }
            } else if (canLink) {
                SmallBtn("🔗") { onLink() }
            }
        }

        // ─── ROW 2 — Compact zoom slider ───
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("🔍", fontSize = 9.sp, color = Color(0xFF888888))

            Slider(
                value = zoomSlider,
                onValueChange = onZoomChange,
                valueRange = TimelineZoom.SLIDER_MIN..TimelineZoom.SLIDER_MAX,
                modifier = Modifier
                    .weight(1f)
                    .height(14.dp),
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFF4F9DFF),
                    activeTrackColor = Color(0xFF4F9DFF),
                    inactiveTrackColor = Color(0xFF303030)
                )
            )

            Text(
                TimelineZoom.formatLabel(
                    zoomSlider,
                    totalSec,
                    viewportContentWidthDp
                ),
                color = Color(0xFFCCCCCC),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold
            )

            Text("🎚️", fontSize = 9.sp, color = Color(0xFF888888))
        }
    }
}

@Composable
private fun SmallBtn(
    text: String,
    highlight: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .height(24.dp)
            .widthIn(min = 24.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(
                when {
                    !enabled -> Color(0xFF141414)
                    highlight -> Color(0xFF7C3AED)
                    else -> Color(0xFF181818)
                }
            )
            .pointerInput(text, enabled) {
                if (enabled) detectTapGestures { onClick() }
            }
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 10.sp,
            color = if (enabled) Color.White else Color(0xFF555555)
        )
    }
}