package com.moody.moodyvideoeditor.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

object TimelinePlayheadController {

    const val USER_SCROLL_GRACE_MS = 1800L
    private const val PLAYHEAD_TARGET_FRACTION = 0.30f

    fun playheadContentPx(
        currentPosMs: Long, totalMs: Long, contentWidthPx: Float
    ): Float {
        if (totalMs <= 0L || contentWidthPx <= 0f) return 0f
        val frac = (currentPosMs.toFloat() / totalMs.toFloat()).coerceIn(0f, 1f)
        return frac * contentWidthPx
    }

    fun playheadScreenX(
        currentPosMs: Long, totalMs: Long,
        contentWidthPx: Float, labelWidthPx: Float, hScrollValue: Int
    ): Float =
        labelWidthPx + playheadContentPx(currentPosMs, totalMs, contentWidthPx) -
                hScrollValue.toFloat()

    fun seekTimeFromClick(
        clickX: Float, contentWidthPx: Float, totalMs: Long
    ): Long {
        if (totalMs <= 0L || contentWidthPx <= 0f) return 0L
        val clampedX = clickX.coerceAtLeast(0f)
        val frac = (clampedX / contentWidthPx).coerceIn(0f, 1f)
        return (frac * totalMs).toLong().coerceIn(0L, totalMs)
    }

    fun autoScrollTarget(
        playheadContentPx: Float,
        labelWidthPx: Float,
        viewportWidthPx: Float,
        contentWidthPx: Float,
        currentScroll: Int,
        isPlaying: Boolean,
        userScrolledRecently: Boolean
    ): Int {
        if (viewportWidthPx <= labelWidthPx) return currentScroll
        if (!isPlaying && userScrolledRecently) return currentScroll

        val contentVisible = viewportWidthPx - labelWidthPx
        if (contentVisible <= 0f) return currentScroll

        val playheadScreen = labelWidthPx + playheadContentPx - currentScroll
        val safeMin = labelWidthPx + 16f
        val safeMax = viewportWidthPx - 16f
        val isVisible = playheadScreen in safeMin..safeMax

        if (!isPlaying && isVisible) return currentScroll

        val targetScroll =
            (playheadContentPx - contentVisible * PLAYHEAD_TARGET_FRACTION).toInt()
        val maxScroll = maxOf(0, (contentWidthPx - contentVisible).toInt())
        return targetScroll.coerceIn(0, maxScroll)
    }

    fun formatTimeDisplay(seconds: Float): String {
        val s = seconds.coerceAtLeast(0f)
        val m = (s / 60f).toInt()
        val sec = (s - m * 60f).toInt()
        return "%02d:%02d".format(m, sec)
    }
}

/**
 * Timeline playhead — THIN red line + small triangle head at top.
 */
@Composable
fun TimelinePlayhead(
    currentPosMs: Long,
    totalMs: Long,
    contentWidthPx: Float,
    labelWidthPx: Float,
    hScrollValue: Int,
    viewportWidthPx: Float
) {
    val density = LocalDensity.current

    val screenX = TimelinePlayheadController.playheadScreenX(
        currentPosMs = currentPosMs,
        totalMs = totalMs,
        contentWidthPx = contentWidthPx,
        labelWidthPx = labelWidthPx,
        hScrollValue = hScrollValue
    )

    if (screenX < labelWidthPx - 4f) return
    if (viewportWidthPx > 0f && screenX > viewportWidthPx + 4f) return

    // ─── Thin RED vertical line (1dp) ───
    Box(
        modifier = Modifier
            .offset(x = with(density) { (screenX - 0.5f).toDp() })
            .width(1.dp)
            .fillMaxHeight()
            .background(Color(0xFFFF3B3B))
            .zIndex(100f)
    )

    // ─── RED triangle head at very top ───
    Box(
        modifier = Modifier
            .offset(
                x = with(density) { (screenX - 6f).toDp() },
                y = 0.dp
            )
            .size(12.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(Color(0xFFFF3B3B))
            .zIndex(101f)
    )
}