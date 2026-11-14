package com.moody.moodyvideoeditor.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt

object TimelinePlayheadController {

    const val USER_SCROLL_GRACE_MS = 1800L
    const val PLAYHEAD_ANCHOR_FRACTION = 0.5f

    fun playheadContentPx(
        currentPosMs: Long, totalMs: Long, contentWidthPx: Float
    ): Float {
        if (totalMs <= 0L || contentWidthPx <= 0f) return 0f
        val frac = (currentPosMs.toFloat() / totalMs.toFloat()).coerceIn(0f, 1f)
        return frac * contentWidthPx
    }

    fun contentXForTime(timeMs: Long, totalMs: Long, contentWidthPx: Float): Float =
        playheadContentPx(timeMs, totalMs, contentWidthPx)

    fun clipLocalXForTime(
        clipStartMs: Long,
        clipLocalTimeMs: Long,
        totalMs: Long,
        contentWidthPx: Float
    ): Float {
        val clipStartX = contentXForTime(clipStartMs, totalMs, contentWidthPx)
        val keyframeX = contentXForTime(
            clipStartMs + clipLocalTimeMs,
            totalMs,
            contentWidthPx
        )
        return keyframeX - clipStartX
    }

    fun fixedPlayheadScreenX(viewportWidthPx: Float): Float =
        viewportWidthPx * PLAYHEAD_ANCHOR_FRACTION

    fun scrollForTime(
        currentPosMs: Long,
        totalMs: Long,
        contentWidthPx: Float
    ): Int = playheadContentPx(currentPosMs, totalMs, contentWidthPx)
        .roundToInt()
        .coerceIn(0, contentWidthPx.roundToInt().coerceAtLeast(0))

    fun timeForScroll(
        scrollValue: Int,
        totalMs: Long,
        contentWidthPx: Float
    ): Long = seekTimeFromClick(scrollValue.toFloat(), contentWidthPx, totalMs)

    fun seekTimeFromClick(clickX: Float, contentWidthPx: Float, totalMs: Long): Long {
        if (totalMs <= 0L || contentWidthPx <= 0f) return 0L
        val clampedX = clickX.coerceAtLeast(0f)
        val frac = (clampedX / contentWidthPx).coerceIn(0f, 1f)
        return (frac * totalMs).toLong().coerceIn(0L, totalMs)
    }

    fun timeForClipTap(
        clipStartMs: Long,
        clipDurationMs: Long,
        localX: Float,
        pixelsPerMs: Float,
        totalMs: Long
    ): Long {
        if (totalMs <= 0L || pixelsPerMs <= 0f) return 0L
        val localTimeMs = (localX.coerceAtLeast(0f) / pixelsPerMs).toLong()
            .coerceIn(0L, clipDurationMs.coerceAtLeast(0L))
        return (clipStartMs + localTimeMs).coerceIn(0L, totalMs)
    }

    fun formatTimeDisplay(seconds: Float): String {
        val s = seconds.coerceAtLeast(0f)
        val m = (s / 60f).toInt()
        val sec = (s - m * 60f).toInt()
        return "%02d:%02d".format(m, sec)
    }
}

// Timeline playhead — thin red line with a centered circular head.
@Composable
fun TimelinePlayhead(
    viewportWidthPx: Float
) {
    val density = LocalDensity.current
    if (viewportWidthPx <= 0f) return
    val screenX = TimelinePlayheadController.fixedPlayheadScreenX(viewportWidthPx)
    val headHalfWidthPx = with(density) { 6.dp.toPx() }

    // Thin RED vertical line (1dp)
    Box(
        modifier = Modifier
            .offset(x = with(density) { (screenX - 0.5f).toDp() })
            .width(1.dp)
            .fillMaxHeight()
            .background(Color(0xFFFF3B3B))
            .zIndex(100f)
    )

    // Keep the circular head centered on the playhead line.
    Box(
        modifier = Modifier
            .offset(
                x = with(density) { (screenX - headHalfWidthPx).toDp() },
                y = 0.dp
            )
            .size(12.dp)
            .clip(CircleShape)
            .background(Color(0xFFFF3B3B))
            .zIndex(101f)
    )
}