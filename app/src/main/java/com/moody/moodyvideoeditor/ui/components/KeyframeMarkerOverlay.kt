package com.moody.moodyvideoeditor.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.moody.moodyvideoeditor.data.EditorClip
import com.moody.moodyvideoeditor.data.KeyframeLibrary
import kotlin.math.abs

/**
 * Keyframe markers drawn in TRACK coordinate space (not clipped by clip Box).
 *
 * Shows:
 *   - ♦ badge on top-right of clip (indicates clip has kf)
 *   - ♦ dots at each keyframe time
 *   - ♦ highlighted at playhead if kf exists there
 */
@Composable
fun KeyframeMarkerOverlay(
    clip: EditorClip,
    clipStartPx: Float,      // absolute X of clip start in track content
    clipWidthPx: Float,
    totalMs: Long,
    currentPosMs: Long = -1L,
    onSeekToKeyframe: (Float) -> Unit
) {
    if (totalMs <= 0L || clipWidthPx <= 0f) return
    if (clip.keyframes.isEmpty()) return

    val clipDurMs = clip.durationMs
    if (clipDurMs <= 0L) return

    val density = LocalDensity.current

    // Collect unique keyframe times (any prop)
    val times = mutableSetOf<Float>()
    KeyframeLibrary.ANIMATABLE_PROPS.forEach { prop ->
        clip.keyframes[prop]?.forEach { kf -> times.add(kf.time) }
    }
    if (times.isEmpty()) return

    // Force recompose on keyframe change
    key(clip.keyframes.hashCode(), clip.id) {

        // ─── Top-right ♦ badge ─────────────────────
        Box(
            modifier = Modifier
                .offset(
                    x = with(density) { (clipStartPx + clipWidthPx - 14f).toDp() },
                    y = 2.dp
                )
                .size(11.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color(0xFF4F9DFF))
                .zIndex(20f),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(Color.White)
            )
        }

        // ─── ♦ dots at each keyframe time ─────────
        times.sorted().forEach { tSec ->
            val tMs = (tSec * 1000f).toLong()
            if (tMs < 0L || tMs > clipDurMs) return@forEach
            val frac = tMs.toFloat() / clipDurMs.toFloat()
            val xPx = clipStartPx + frac * clipWidthPx

            val absTimeMs = clip.timelineStartMs + tMs
            val atPlayhead = abs(absTimeMs - currentPosMs) < 50L

            Box(
                modifier = Modifier
                    .offset(
                        x = with(density) { (xPx - 6f).toDp() },
                        y = 0.dp
                    )
                    .size(12.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        if (atPlayhead) Color(0xFFFFD166) else Color(0xFF4F9DFF)
                    )
                    .zIndex(21f)
                    .pointerInput(tSec) {
                        detectTapGestures { onSeekToKeyframe(tSec) }
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .clip(RoundedCornerShape(1.dp))
                        .background(Color.White)
                )
            }
        }
    }
}