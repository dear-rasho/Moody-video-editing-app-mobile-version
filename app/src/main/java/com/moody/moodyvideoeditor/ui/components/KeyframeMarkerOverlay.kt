package com.moody.moodyvideoeditor.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.graphics.Path
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
    if (clip.keyframes.isEmpty() && !clip.mask.hasKeyframes) return

    val clipDurMs = clip.durationMs
    if (clipDurMs <= 0L) return

    val density = LocalDensity.current

    // Collect unique keyframe times (any prop)
    val times = mutableSetOf<Float>()
// Transform keyframes
    KeyframeLibrary.ANIMATABLE_PROPS.forEach { prop ->
        clip.keyframes[prop]?.forEach { kf -> times.add(kf.time) }
    }
// 🆕 Mask property keyframes
    clip.mask.propertyKeyframes.values.forEach { list ->
        list.forEach { kf -> times.add(kf.time) }
    }
// 🆕 Legacy mask keyframes
    clip.mask.keyframes.forEach { kf ->
        times.add(kf.timeMs / 1000f)
    }
    if (times.isEmpty()) return

    // Force recompose on keyframe change
    key(clip.keyframes.hashCode(), clip.mask.propertyKeyframes.hashCode(), clip.mask.keyframes.hashCode(), clip.id) {
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

                val hasEasingGraph = KeyframeLibrary.ANIMATABLE_PROPS.any { property ->
                    clip.keyframes[property].orEmpty().any {
                        abs(it.time - tSec) < 0.02f && it.ease != "linear"
                    }
                } || clip.mask.propertyKeyframes.values.any { frames ->
                    frames.any { abs(it.time - tSec) < 0.02f && it.ease != "linear" }
                } || clip.mask.keyframes.any {
                    abs(it.timeMs / 1000f - tSec) < 0.02f && it.ease != "linear"
                }

                Canvas(
                    modifier = Modifier
                        .offset(
                            x = with(density) { xPx.toDp() } - 6.dp,
                            y = 0.dp
                        )
                        .size(12.dp)
                        .zIndex(21f)
                        .pointerInput(tSec) {
                            detectTapGestures { onSeekToKeyframe(tSec) }
                        }
                ) {
                    val markerColor = if (atPlayhead) Color(0xFF1689FF) else Color.White
                    val path = Path().apply {
                        if (hasEasingGraph) {
                            moveTo(0f, 0f)
                            lineTo(size.width, 0f)
                            cubicTo(size.width, size.height * 0.3f, size.width * 0.62f, size.height * 0.34f, size.width * 0.62f, size.height / 2f)
                            cubicTo(size.width * 0.62f, size.height * 0.66f, size.width, size.height * 0.7f, size.width, size.height)
                            lineTo(0f, size.height)
                            cubicTo(0f, size.height * 0.7f, size.width * 0.38f, size.height * 0.66f, size.width * 0.38f, size.height / 2f)
                            cubicTo(size.width * 0.38f, size.height * 0.34f, 0f, size.height * 0.3f, 0f, 0f)
                        } else {
                            moveTo(size.width / 2f, 0f)
                            lineTo(size.width, size.height / 2f)
                            lineTo(size.width / 2f, size.height)
                            lineTo(0f, size.height / 2f)
                        }
                        close()
                    }
                    drawPath(path, markerColor)
                }
            }
        }
    }
}