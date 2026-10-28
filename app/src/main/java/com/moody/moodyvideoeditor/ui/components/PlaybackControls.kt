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
import androidx.compose.foundation.layout.wrapContentWidth
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
    seekStepSeconds: Int = 3,
    onSeekBy: (Long) -> Unit = {},
    // Tools
    onSelectBackward: () -> Unit = {},
    onSelectForward: () -> Unit = {},
    onSelectAll: () -> Unit = {},
    onSelectAllTrack: () -> Unit = {},
    onDeselectAll: () -> Unit = {},
    onLink: () -> Unit = {},
    onUnlink: () -> Unit = {},
    isLinked: Boolean = false,
    canLink: Boolean = false,
    // Split & Trim
    onSplit: () -> Unit = {},
    canSplit: Boolean = false,
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
        // ─── ROW 1 — Centered playback and seek controls ───
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(34.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(onClick = onUndo, enabled = canUndo, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Filled.Undo, "Undo",
                        tint = if (canUndo) Color.White else Color(0xFF444444),
                        modifier = Modifier.size(16.dp)
                    )
                }
                SmallBtn(
                    text = "-${seekStepSeconds}s",
                    enabled = hasVideo
                ) { onSeekBy(-seekStepSeconds.coerceAtLeast(1) * 1000L) }
            }

            IconButton(onClick = onPlayPause, enabled = hasVideo, modifier = Modifier.size(34.dp)) {
                Icon(
                    if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    "Play",
                    tint = if (hasVideo) Color(0xFF7C3AED) else Color(0xFF444444),
                    modifier = Modifier.size(22.dp)
                )
            }

            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start
            ) {
                SmallBtn(
                    text = "+${seekStepSeconds}s",
                    enabled = hasVideo
                ) { onSeekBy(seekStepSeconds.coerceAtLeast(1) * 1000L) }
                IconButton(onClick = onRedo, enabled = canRedo, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Filled.Redo, "Redo",
                        tint = if (canRedo) Color.White else Color(0xFF444444),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // ─── ROW 2 — Selection and editing tools ───
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Select tools (with Text)
            SmallBtn("⏪ Back") { onSelectBackward() }
            SmallBtn("⏩ Fwd") { onSelectForward() }
            SmallBtn("☑ All") { onSelectAll() }
            SmallBtn("▤ Track") { onSelectAllTrack() }
            SmallBtn("☐ None") { onDeselectAll() }

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

            // Split
            SmallBtn(
                text = "][ Split",
                enabled = canSplit,
                highlight = canSplit
            ) { onSplit() }

            // Trim
            SmallBtn(
                text = "[ Trim L",
                enabled = canTrimLeft,
                highlight = canTrimLeft
            ) { onTrimLeft() }

            SmallBtn(
                text = "] Trim R",
                enabled = canTrimRight,
                highlight = canTrimRight
            ) { onTrimRight() }

            // Link / unlink
            if (isLinked) {
                SmallBtn("🔓 Unlink", highlight = true) { onUnlink() }
            } else if (canLink) {
                SmallBtn("🔗 Link") { onLink() }
            }
        }

        // ─── ROW 2 — Compact zoom slider ───
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(18.dp)
                .padding(horizontal = 8.dp),
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
            .height(26.dp)
            .wrapContentWidth() // 🆕 Text ke hisaab se width
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
            .padding(horizontal = 8.dp), // 🆕 Saaf padding
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 9.sp, // Chota text
            fontWeight = FontWeight.Medium,
            color = if (enabled) Color.White else Color(0xFF555555),
            maxLines = 1
        )
    }
}