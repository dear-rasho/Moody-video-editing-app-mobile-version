package com.moody.moodyvideoeditor.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.moody.moodyvideoeditor.data.EditorClip
import com.moody.moodyvideoeditor.data.KeyframeLibrary
import com.moody.moodyvideoeditor.utils.KeyframeStore
import kotlin.math.abs

// Keyframe markers drawn in ClipCard-local coordinate space.
// Markers are positioned in clip-local time; the playhead's marker is blue.
@Composable
fun KeyframeMarkerOverlay(
    clip: EditorClip,
    clipWidthPx: Float,
    currentPosMs: Long = -1L,
    onSeekToKeyframe: (Float) -> Unit
) {
    if (clipWidthPx <= 0f) return
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
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.CenterStart
        ) {
            // The parent ClipCard centers its children; this full-size wrapper
            // gives marker offsets a clip-local origin instead of adding half a clip.
            times.sorted().forEach { tSec ->
                val tMs = (tSec * 1000f).toLong()
                if (tMs < 0L || tMs > clipDurMs) return@forEach
                val xPx = KeyframeStore.keyframeXInClip(
                    keyframeTimeSec = tSec,
                    clipDurationMs = clipDurMs,
                    clipWidthPx = clipWidthPx
                )

                val absTimeMs = clip.timelineStartMs + tMs
                val atPlayhead = abs(absTimeMs - currentPosMs) < 50L

                Box(
                    modifier = Modifier
                        .offset(
                            x = with(density) { xPx.toDp() } - 6.dp,
                            y = 0.dp
                        )
                        .size(12.dp)
                        .graphicsLayer { rotationZ = 45f }
                        .background(if (atPlayhead) Color(0xFF4F9DFF) else Color.White)
                        .zIndex(21f)
                        .pointerInput(tSec) {
                            detectTapGestures { onSeekToKeyframe(tSec) }
                        }
                )
            }
        }
    }
}