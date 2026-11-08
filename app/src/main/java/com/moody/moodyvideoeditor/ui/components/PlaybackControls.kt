package com.moody.moodyvideoeditor.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
    hasVideo: Boolean,
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
    onCopy: () -> Unit = {},
    onCut: () -> Unit = {},
    onPaste: () -> Unit = {},
    canCopy: Boolean = false,
    canPaste: Boolean = false,
    onAddVisualLayer: () -> Unit = {},
    onAddAudioLayer: () -> Unit = {},
    // Zoom
    zoomSlider: Float = 0f,
    totalSec: Float = 0f,
    viewportContentWidthDp: Float = 320f,
    onZoomChange: (Float) -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(38.dp)
            .background(Color(0xFF0A0A0A))
            .padding(horizontal = 2.dp, vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            if (canPaste) {
                SmallBtn("Paste", highlight = true) { onPaste() }
            } else {
                SmallBtn("Copy", enabled = hasVideo && canCopy) { onCopy() }
                SmallBtn("Cut", enabled = hasVideo && canCopy, highlight = canCopy) { onCut() }
            }
            SmallBtn("][ Split", enabled = canSplit, highlight = canSplit) { onSplit() }
            SmallBtn("[ Trim L", enabled = canTrimLeft, highlight = canTrimLeft) { onTrimLeft() }
            SmallBtn("] Trim R", enabled = canTrimRight, highlight = canTrimRight) { onTrimRight() }
            SmallBtn("⏪ Back") { onSelectBackward() }
            SmallBtn("⏩ Fwd") { onSelectForward() }
            SmallBtn("☑ All") { onSelectAll() }
            SmallBtn("▤ Track") { onSelectAllTrack() }
            SmallBtn("☐ None") { onDeselectAll() }
            if (isLinked) {
                SmallBtn("Unlink", highlight = true) { onUnlink() }
            } else if (canLink) {
                SmallBtn("Link") { onLink() }
            }
            Text("🔍", fontSize = 9.sp, color = Color(0xFF888888))
            Slider(
                value = zoomSlider,
                onValueChange = onZoomChange,
                valueRange = TimelineZoom.SLIDER_MIN..TimelineZoom.SLIDER_MAX,
                modifier = Modifier.width(84.dp).height(18.dp),
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFF4F9DFF),
                    activeTrackColor = Color(0xFF4F9DFF),
                    inactiveTrackColor = Color(0xFF303030)
                )
            )
            Text(
                TimelineZoom.formatLabel(zoomSlider, totalSec, viewportContentWidthDp),
                color = Color(0xFFCCCCCC),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold
            )
            SmallBtn("＋V") { onAddVisualLayer() }
            SmallBtn("＋A") { onAddAudioLayer() }
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
            .wrapContentWidth()
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