package com.moody.moodyvideoeditor.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moody.moodyvideoeditor.data.Keyframe
import com.moody.moodyvideoeditor.data.KeyframeLibrary
import com.moody.moodyvideoeditor.utils.KeyframeStore
import kotlin.math.abs

/**
 * Keyframe Graph — fullscreen, horizontal-scrollable time axis.
 * Each 1 second = 80 dp.
 */
@Composable
fun KeyframeGraphDialog(
    clipName: String,
    keyframeMap: Map<String, List<Keyframe>>,
    clipDurationSec: Float,
    onUpdateKeyframe: (prop: String, oldTime: Float, newTime: Float, newValue: Float) -> Unit,
    onDeleteKeyframe: (prop: String, time: Float) -> Unit,
    onClose: () -> Unit
) {
    val activeProps = remember(keyframeMap) {
        KeyframeLibrary.ANIMATABLE_PROPS.filter {
            (keyframeMap[it]?.size ?: 0) > 0
        }
    }

    val propColors = mapOf(
        "x" to Color(0xFF4F9DFF),
        "y" to Color(0xFF22C55E),
        "scale" to Color(0xFFF59E0B),
        "rotation" to Color(0xFFEF4444),
        "anchorX" to Color(0xFF06B6D4),
        "anchorY" to Color(0xFF14B8A6),
        "cropL" to Color(0xFFF472B6),
        "cropR" to Color(0xFFFB923C),
        "cropT" to Color(0xFFFACC15),
        "cropB" to Color(0xFF84CC16)
    )

    val propRanges = remember(keyframeMap, activeProps) {
        activeProps.associateWith { prop ->
            val kfs = keyframeMap[prop] ?: emptyList()
            if (kfs.isEmpty()) 0f to 1f
            else {
                val minV = kfs.minOf { it.value }
                val maxV = kfs.maxOf { it.value }
                if (abs(maxV - minV) < 0.01f) (minV - 1f) to (maxV + 1f)
                else minV to maxV
            }
        }
    }

    var dragProp by remember { mutableStateOf<String?>(null) }
    var dragOriginalTime by remember { mutableStateOf(0f) }

    val hScroll = rememberScrollState()

    // 80 dp per second → canvas width
    val canvasWidthDp = (clipDurationSec * 80f).coerceAtLeast(600f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0A))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // ─── HEADER ─────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF141414))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "📊 Keyframe Graph",
                        color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold
                    )
                    Text(
                        "${clipName.take(20)}  •  ${
                            String.format(
                                "%.2fs",
                                clipDurationSec
                            )
                        }  •  swipe to scroll",
                        color = Color(0xFF888888), fontSize = 10.sp
                    )
                }
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF222222))
                        .pointerInput(Unit) { detectTapGestures { onClose() } },
                    contentAlignment = Alignment.Center
                ) {
                    Text("✕", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }

            // ─── LEGEND ─────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF141414))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (activeProps.isEmpty()) {
                    Text(
                        "No keyframes yet",
                        color = Color(0xFF555555), fontSize = 10.sp, fontWeight = FontWeight.Bold
                    )
                } else {
                    activeProps.forEach { prop ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(propColors[prop] ?: Color.White)
                            )
                            Spacer(Modifier.width(3.dp))
                            Text(
                                prop, color = Color(0xFFCCCCCC), fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // ─── SCROLLABLE CANVAS ──────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF101010))
            ) {
                if (activeProps.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("◆", color = Color(0xFF444444), fontSize = 42.sp)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "No keyframes yet",
                                color = Color(0xFF888888), fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Tap ◆ in Transform panel to add keyframes",
                                color = Color(0xFF555555), fontSize = 10.sp
                            )
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .horizontalScroll(hScroll)
                    ) {
                        Canvas(
                            modifier = Modifier
                                .width(canvasWidthDp.dp)
                                .fillMaxSize()
                                .pointerInput(activeProps, propRanges, clipDurationSec) {
                                    detectDragGestures(
                                        onDragStart = { offset ->
                                            val padL = 44f
                                            val padT = 20f
                                            val padR = 20f
                                            val padB = 34f
                                            val plotW = size.width - padL - padR
                                            val plotH = size.height - padT - padB

                                            var bestProp: String? = null
                                            var bestTime = 0f
                                            var bestDistSq = Float.MAX_VALUE

                                            activeProps.forEach { prop ->
                                                val range = propRanges[prop] ?: return@forEach
                                                val kfs = keyframeMap[prop] ?: return@forEach
                                                kfs.forEach { kf ->
                                                    val px =
                                                        padL + (kf.time / clipDurationSec) * plotW
                                                    val py = padT + plotH * (1f -
                                                            (kf.value - range.first) / (range.second - range.first))
                                                    val dx = offset.x - px
                                                    val dy = offset.y - py
                                                    val d2 = dx * dx + dy * dy
                                                    if (d2 < bestDistSq && d2 < 26f * 26f) {
                                                        bestDistSq = d2
                                                        bestProp = prop
                                                        bestTime = kf.time
                                                    }
                                                }
                                            }

                                            if (bestProp != null) {
                                                dragProp = bestProp
                                                dragOriginalTime = bestTime
                                            }
                                        },
                                        onDrag = { change, _ ->
                                            val prop = dragProp ?: return@detectDragGestures
                                            change.consume()
                                            val padL = 44f
                                            val padT = 20f
                                            val padR = 20f
                                            val padB = 34f
                                            val plotW = size.width - padL - padR
                                            val plotH = size.height - padT - padB
                                            val range =
                                                propRanges[prop] ?: return@detectDragGestures

                                            val newTime = ((change.position.x - padL) / plotW *
                                                    clipDurationSec).coerceIn(0f, clipDurationSec)
                                            val normY = (1f - (change.position.y - padT) / plotH)
                                                .coerceIn(0f, 1f)
                                            val newValue =
                                                range.first + normY * (range.second - range.first)

                                            onUpdateKeyframe(
                                                prop,
                                                dragOriginalTime,
                                                newTime,
                                                newValue
                                            )
                                            dragOriginalTime = newTime
                                        },
                                        onDragEnd = { dragProp = null },
                                        onDragCancel = { dragProp = null }
                                    )
                                }
                        ) {
                            val padL = 44f
                            val padT = 20f
                            val padR = 20f
                            val padB = 34f
                            val plotW = size.width - padL - padR
                            val plotH = size.height - padT - padB

                            // ─── HORIZONTAL GRID (per second) ───
                            val totalSeconds = clipDurationSec.toInt() + 1
                            for (s in 0..totalSeconds) {
                                val sec = s.toFloat()
                                if (sec > clipDurationSec) break
                                val x = padL + (sec / clipDurationSec) * plotW
                                drawLine(
                                    color = if (s % 5 == 0) Color(0xFF333333) else Color(0xFF1A1A1A),
                                    start = Offset(x, padT),
                                    end = Offset(x, padT + plotH),
                                    strokeWidth = if (s % 5 == 0) 1.5f else 1f
                                )
                            }

                            // ─── HORIZONTAL GRID (values) ───
                            for (i in 0..4) {
                                val y = padT + plotH * (i / 4f)
                                drawLine(
                                    color = Color(0xFF1A1A1A),
                                    start = Offset(padL, y),
                                    end = Offset(padL + plotW, y),
                                    strokeWidth = 1f
                                )
                            }

                            // ─── BORDER ───
                            drawRect(
                                color = Color(0xFF262626),
                                topLeft = Offset(padL, padT),
                                size = Size(plotW, plotH),
                                style = Stroke(width = 1.5f)
                            )

                            // ─── CURVES + DOTS ───
                            activeProps.forEach { prop ->
                                val kfs = (keyframeMap[prop] ?: emptyList()).sortedBy { it.time }
                                if (kfs.isEmpty()) return@forEach
                                val color = propColors[prop] ?: Color.White
                                val range = propRanges[prop] ?: return@forEach
                                val span = (range.second - range.first)
                                    .takeIf { abs(it) > 0.001f } ?: 1f

                                val path = Path()
                                val samples = 200
                                for (i in 0..samples) {
                                    val t = clipDurationSec * (i / samples.toFloat())
                                    val v = KeyframeStore.sample(keyframeMap, prop, t, kfs[0].value)
                                    val x = padL + (t / clipDurationSec) * plotW
                                    val y = padT + plotH * (1f - (v - range.first) / span)
                                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                                }
                                drawPath(path, color, style = Stroke(width = 2.5f))

                                kfs.forEach { kf ->
                                    val x = padL + (kf.time / clipDurationSec) * plotW
                                    val y = padT + plotH * (1f - (kf.value - range.first) / span)
                                    val isActive = dragProp == prop &&
                                            abs(kf.time - dragOriginalTime) < 0.05f

                                    drawCircle(
                                        color = if (isActive) Color.White else color,
                                        radius = if (isActive) 9f else 7f,
                                        center = Offset(x, y)
                                    )
                                    drawCircle(
                                        color = Color.White,
                                        radius = if (isActive) 9f else 7f,
                                        center = Offset(x, y),
                                        style = Stroke(width = if (isActive) 2.5f else 1.5f)
                                    )
                                }
                            }

                            // ─── TIME LABELS ───
                            // (Canvas can't draw text; skip)
                        }
                    }
                }
            }

            // ─── HINT ───────────────────────────────────
            Text(
                "Drag dots to move (X = time, Y = value)  •  swipe canvas horizontally",
                color = Color(0xFF666666),
                fontSize = 10.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF141414))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }
    }
}