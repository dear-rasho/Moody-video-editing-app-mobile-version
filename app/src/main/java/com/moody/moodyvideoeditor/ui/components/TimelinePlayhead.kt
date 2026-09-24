package com.moody.moodyvideoeditor.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Timeline Playhead — red vertical line + knob.
 *
 * Features:
 * - Shows current position
 * - Knob is draggable (scrub)
 * - Controlled by parent (currentTimeMs + onSeek callback)
 */
@Composable
fun TimelinePlayhead(
    currentTimeMs: Long,
    totalMs: Long,
    leftOffset: Dp,        // usually TRACK_LABEL_WIDTH
    trackAreaWidthPx: Int,
    onSeek: (Long) -> Unit
) {
    if (trackAreaWidthPx <= 0 || totalMs <= 0) return

    val density = LocalDensity.current
    val pxPerMs = trackAreaWidthPx.toFloat() / totalMs.toFloat()
    val playPx = (currentTimeMs * pxPerMs).toFloat()
    val playDp = with(density) { playPx.toDp() }

    // ═══ Vertical Line ═══
    Box(
        modifier = Modifier
            .offset(x = leftOffset + playDp - 1.dp)
            .width(2.dp)
            .fillMaxHeight()
            .background(Color(0xFFFF3B3B))
    )

    // ═══ Draggable Knob ═══
    Box(
        modifier = Modifier
            .offset(x = leftOffset + playDp - 10.dp)
            .width(20.dp)
            .height(20.dp)
            .clip(CircleShape)
            .background(Color(0xFFFF3B3B))
            .pointerInput(totalMs, trackAreaWidthPx) {
                detectDragGestures(
                    onDrag = { change, drag ->
                        change.consume()
                        val deltaMs = (drag.x / pxPerMs).toLong()
                        val newTime = (currentTimeMs + deltaMs).coerceIn(0L, totalMs)
                        onSeek(newTime)
                    }
                )
            }
    )
}