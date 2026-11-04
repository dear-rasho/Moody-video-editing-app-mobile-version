package com.moody.moodyvideoeditor.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.moody.moodyvideoeditor.ui.components.FeaturePanel
import com.moody.moodyvideoeditor.data.Keyframe
import com.moody.moodyvideoeditor.data.KeyframeLibrary
import com.moody.moodyvideoeditor.utils.KeyframeStore
import com.moody.moodyvideoeditor.utils.SpeedEngine
import kotlin.math.abs

private fun createSmoothGraphPath(points: List<Offset>): Path {
    val path = Path()
    if (points.isEmpty()) return path
    path.moveTo(points.first().x, points.first().y)
    if (points.size == 1) return path

    val segmentSlopes = (0 until points.lastIndex).map { index ->
        val dx = (points[index + 1].x - points[index].x).coerceAtLeast(0.001f)
        (points[index + 1].y - points[index].y) / dx
    }
    val tangents = MutableList(points.size) { 0f }
    tangents[0] = segmentSlopes.first()
    tangents[tangents.lastIndex] = segmentSlopes.last()
    for (index in 1 until points.lastIndex) {
        val previous = segmentSlopes[index - 1]
        val next = segmentSlopes[index]
        tangents[index] = if (previous * next <= 0f) {
            0f
        } else {
            2f * previous * next / (previous + next)
        }
    }

    for (index in segmentSlopes.indices) {
        val slope = segmentSlopes[index]
        if (abs(slope) < 0.0001f) {
            tangents[index] = 0f
            tangents[index + 1] = 0f
        } else {
            val alpha = tangents[index] / slope
            val beta = tangents[index + 1] / slope
            val magnitude = alpha * alpha + beta * beta
            if (magnitude > 9f) {
                val factor = 3f / kotlin.math.sqrt(magnitude)
                tangents[index] = factor * alpha * slope
                tangents[index + 1] = factor * beta * slope
            }
        }
    }

    for (index in segmentSlopes.indices) {
        val start = points[index]
        val end = points[index + 1]
        val dx = end.x - start.x
        path.cubicTo(
            start.x + dx / 3f,
            start.y + tangents[index] * dx / 3f,
            end.x - dx / 3f,
            end.y - tangents[index + 1] * dx / 3f,
            end.x,
            end.y
        )
    }
    return path
}

// Keyframe Graph — fullscreen, horizontal-scrollable time axis.
// Each 1 second = 80 dp.
@Composable
fun KeyframeGraphDialog(
    clipName: String,
    clipId: String? = null,
    keyframeMap: Map<String, List<Keyframe>>,
    clipDurationSec: Float,
    readOnly: Boolean = false,
    onUpdateKeyframe: (prop: String, oldTime: Float, newTime: Float, newValue: Float) -> Unit,
    onDeleteKeyframe: (prop: String, time: Float) -> Unit,
    selectedKeyframe: Pair<String, Float>? = null,
    selectAllKeyframes: Boolean = false,
    inlinePanel: Boolean = false,
    onSelectKeyframe: (prop: String, time: Float) -> Unit = { _, _ -> },
    onSetKeyframeEase: (prop: String, time: Float, ease: String) -> Unit = { _, _, _ -> },
    enableEaseEditing: Boolean = false,
    onKeyframeEditStart: () -> Unit = {},
    onKeyframeEditEnd: () -> Unit = {},
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
        "scaleX" to Color(0xFFFB7185),
        "scaleY" to Color(0xFFA78BFA),
        "rotation" to Color(0xFFEF4444),
        "anchorX" to Color(0xFF06B6D4),
        "anchorY" to Color(0xFF14B8A6),
        "cropL" to Color(0xFFF472B6),
        "cropR" to Color(0xFFFB923C),
        "cropT" to Color(0xFFFACC15),
        "cropB" to Color(0xFF84CC16),
        KeyframeLibrary.SPEED to Color(0xFF38BDF8),
        "spotlight.sourceX" to Color(0xFFFFC857),
        "spotlight.sourceY" to Color(0xFFFFE08A),
        "spotlight.targetX" to Color(0xFF3DD6D0),
        "spotlight.targetY" to Color(0xFF79E8E2),
        "spotlight.rotation" to Color(0xFFFF8C42),
        "spotlight.intensity" to Color(0xFFFF4FD8),
        "spotlight.radius" to Color(0xFF8A7DFF),
        "spotlight.softness" to Color(0xFF9ECA55),
        "spotlight.colorR" to Color(0xFFFF6B6B),
        "spotlight.colorG" to Color(0xFF7EE081),
        "spotlight.colorB" to Color(0xFF6B9BFF),
        "spotlight.targetColorR" to Color(0xFFFF9898),
        "spotlight.targetColorG" to Color(0xFFA3ECA8),
        "spotlight.targetColorB" to Color(0xFF9BB9FF)
    )

    val propRanges = remember(clipId, clipName, activeProps) {
        activeProps.associateWith { prop ->
            val kfs = keyframeMap[prop] ?: emptyList()
            if (prop == KeyframeLibrary.SPEED) {
                SpeedEngine.MIN_SPEED to SpeedEngine.MAX_SPEED
            } else if (kfs.isEmpty()) 0f to 1f
            else {
                val minV = kfs.minOf { it.value }
                val maxV = kfs.maxOf { it.value }
                val padding = maxOf((maxV - minV) * 0.15f, abs(minV) * 0.05f, 0.1f)
                (minV - padding) to (maxV + padding)
            }
        }
    }

    var dragProp by remember { mutableStateOf<String?>(null) }
    var dragOriginalTime by remember { mutableStateOf(0f) }
    var selectedGraphKeyframe by remember { mutableStateOf<Pair<String, Float>?>(null) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var selectedProp by remember(clipId, clipName) {
        mutableStateOf(
            selectedKeyframe?.first?.takeIf { it in activeProps } ?: activeProps.firstOrNull()
        )
    }
    val visibleSelectedProp = selectedProp?.takeIf { it in activeProps }
        ?: activeProps.firstOrNull()
    val graphDurationSec by remember(clipId, clipName) {
        mutableFloatStateOf(clipDurationSec.coerceAtLeast(0.1f))
    }
    val dragHitRadiusPx = with(LocalDensity.current) { 40.dp.toPx() }
    val activeSelection = selectedGraphKeyframe
        ?: selectedKeyframe?.takeIf { it.first == visibleSelectedProp }
    val visibleProps = activeProps
    val hScroll = rememberScrollState()

    KeyframeGraphHost(inlinePanel, onClose) {

            // HEADER
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF141414))
                    .padding(horizontal = 12.dp, vertical = if (inlinePanel) 5.dp else 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    if (!inlinePanel) {
                        Text(
                            "📊 Keyframe Graph",
                            color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        "${clipName.take(20)}  •  ${
                            String.format(
                                "%.2fs",
                                graphDurationSec
                            )
                        }  •  swipe to scroll",
                        color = Color(0xFF888888), fontSize = 10.sp
                    )
                }
                if (!inlinePanel) {
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
            }

            // PROPERTY SELECTOR
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
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (visibleSelectedProp == prop) Color(0xFF33245C)
                                    else Color(0xFF202020)
                                )
                                .pointerInput(prop) {
                                    detectTapGestures {
                                        selectedProp = prop
                                        selectedGraphKeyframe = null
                                    }
                                }
                                .padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(propColors[prop] ?: Color.White)
                            )
                            Spacer(Modifier.width(3.dp))
                            Text(
                                prop,
                                color = if (visibleSelectedProp == prop) Color.White else Color(0xFFCCCCCC),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF141414))
                    .padding(horizontal = 12.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Zoom", color = Color(0xFFAAAAAA), fontSize = 10.sp)
                Slider(
                    value = zoom,
                    onValueChange = { zoom = it },
                    valueRange = 0.5f..4f,
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFF9B72FF),
                        activeTrackColor = Color(0xFF9B72FF),
                        inactiveTrackColor = Color(0xFF383838)
                    )
                )
                Text(
                    "${"%.1f".format(zoom)}x",
                    color = Color.White,
                    fontSize = 10.sp,
                    modifier = Modifier.width(34.dp)
                )
            }

            // SCROLLABLE CANVAS
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
                    BoxWithConstraints(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        val canvasWidthDp =
                            maxOf(maxWidth.value, graphDurationSec * 80f * zoom)
                        Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .horizontalScroll(hScroll)
                    ) {
                        Canvas(
                            modifier = Modifier
                                .width(canvasWidthDp.dp)
                                .fillMaxSize()
                                .pointerInput(
                                    visibleProps, propRanges, graphDurationSec, dragHitRadiusPx
                                ) {
                                    detectTapGestures { offset ->
                                        val padL = 44f
                                        val padT = 20f
                                        val padR = 20f
                                        val padB = 34f
                                        val plotW = size.width - padL - padR
                                        val plotH = size.height - padT - padB
                                        var best: Pair<String, Float>? = null
                                        var bestDistSq = dragHitRadiusPx * dragHitRadiusPx
                                        visibleProps.forEach { prop ->
                                            val range = propRanges[prop] ?: return@forEach
                                            (keyframeMap[prop] ?: emptyList()).forEach { keyframe ->
                                                val x = padL + keyframe.time / graphDurationSec * plotW
                                                val y = padT + plotH * (
                                                        1f - (keyframe.value - range.first) /
                                                                (range.second - range.first)
                                                        )
                                                val dx = offset.x - x
                                                val dy = offset.y - y
                                                val distanceSq = dx * dx + dy * dy
                                                if (distanceSq < bestDistSq) {
                                                    bestDistSq = distanceSq
                                                    best = prop to keyframe.time
                                                }
                                            }
                                        }
                                        best?.let {
                                            selectedGraphKeyframe = it
                                            selectedProp = it.first
                                            onSelectKeyframe(it.first, it.second)
                                        }
                                    }
                                }
                                .pointerInput(
                                    visibleProps, propRanges, graphDurationSec, dragHitRadiusPx
                                ) {
                                    if (!readOnly) {
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

                                            visibleProps.forEach { prop ->
                                                val range = propRanges[prop] ?: return@forEach
                                                val kfs = keyframeMap[prop] ?: return@forEach
                                                kfs.forEach { kf ->
                                                    val px =
                                                        padL + (kf.time / graphDurationSec) * plotW
                                                    val py = padT + plotH * (1f -
                                                            (kf.value - range.first) / (range.second - range.first))
                                                    val dx = offset.x - px
                                                    val dy = offset.y - py
                                                    val d2 = dx * dx + dy * dy
                                                    if (d2 < bestDistSq &&
                                                        d2 < dragHitRadiusPx * dragHitRadiusPx
                                                    ) {
                                                        bestDistSq = d2
                                                        bestProp = prop
                                                        bestTime = kf.time
                                                    }
                                                }
                                            }

                                            if (bestProp != null) {
                                                dragProp = bestProp
                                                dragOriginalTime = bestTime
                                                selectedProp = bestProp
                                                selectedGraphKeyframe = bestProp to bestTime
                                                onKeyframeEditStart()
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
                                                    graphDurationSec)
                                                .coerceIn(0f, graphDurationSec)
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
                                            selectedGraphKeyframe = prop to newTime
                                            dragOriginalTime = newTime
                                        },
                                        onDragEnd = {
                                            if (dragProp != null) onKeyframeEditEnd()
                                            dragProp = null
                                        },
                                        onDragCancel = {
                                            if (dragProp != null) onKeyframeEditEnd()
                                            dragProp = null
                                        }
                                    )
                                    }
                                }
                        ) {
                            val padL = 44f
                            val padT = 20f
                            val padR = 20f
                            val padB = 34f
                            val plotW = size.width - padL - padR
                            val plotH = size.height - padT - padB

                            // HORIZONTAL GRID (per second)
                            val totalSeconds = graphDurationSec.toInt() + 1
                            for (s in 0..totalSeconds) {
                                val sec = s.toFloat()
                                if (sec > graphDurationSec) break
                                val x = padL + (sec / graphDurationSec) * plotW
                                drawLine(
                                    color = if (s % 5 == 0) Color(0xFF333333) else Color(0xFF1A1A1A),
                                    start = Offset(x, padT),
                                    end = Offset(x, padT + plotH),
                                    strokeWidth = if (s % 5 == 0) 1.5f else 1f
                                )
                            }

                            // HORIZONTAL GRID (values)
                            for (i in 0..4) {
                                val y = padT + plotH * (i / 4f)
                                drawLine(
                                    color = Color(0xFF1A1A1A),
                                    start = Offset(padL, y),
                                    end = Offset(padL + plotW, y),
                                    strokeWidth = 1f
                                )
                            }

                            // BORDER
                            drawRect(
                                color = Color(0xFF262626),
                                topLeft = Offset(padL, padT),
                                size = Size(plotW, plotH),
                                style = Stroke(width = 1.5f)
                            )

                            // CURVES + DOTS
                            visibleProps.forEach { prop ->
                                val kfs = (keyframeMap[prop] ?: emptyList()).sortedBy { it.time }
                                if (kfs.isEmpty()) return@forEach
                                val color = propColors[prop] ?: Color.White
                                val range = propRanges[prop] ?: return@forEach
                                val span = (range.second - range.first)
                                    .takeIf { abs(it) > 0.001f } ?: 1f

                                val points = ArrayList<Offset>(201)
                                val samples = 200
                                for (i in 0..samples) {
                                    val t = graphDurationSec * (i / samples.toFloat())
                                    val v = KeyframeStore.sample(keyframeMap, prop, t, kfs[0].value)
                                    val x = padL + (t / graphDurationSec) * plotW
                                    val y = padT + plotH * (1f - (v - range.first) / span)
                                    points.add(Offset(x, y))
                                }
                                clipRect(padL, padT, padL + plotW, padT + plotH) {
                                    drawPath(
                                        createSmoothGraphPath(points),
                                        color,
                                        style = Stroke(width = 2.5f)
                                    )
                                }

                                kfs.forEach { kf ->
                                    val x = padL + (kf.time / graphDurationSec) * plotW
                                    val y = padT + plotH * (1f - (kf.value - range.first) / span)
                                    val isActive = dragProp == prop &&
                                            abs(kf.time - dragOriginalTime) < 0.05f
                                    val isSelected = activeSelection?.first == prop &&
                                            abs(activeSelection.second - kf.time) < 0.05f
                                    val isEaseSelected = selectAllKeyframes || isSelected

                                    if (isEaseSelected) {
                                        drawCircle(
                                            Color(0xFF1689FF),
                                            radius = 12f,
                                            center = Offset(x, y),
                                            style = Stroke(width = 3f)
                                        )
                                    }

                                    if (kf.ease != "linear" || isEaseSelected) {
                                        val markerColor = if (isEaseSelected || isActive) {
                                            Color(0xFF1689FF)
                                        } else {
                                            color
                                        }
                                        val markerPath = Path().apply {
                                            moveTo(x - 9f, y - 7f)
                                            lineTo(x + 9f, y - 7f)
                                            cubicTo(x + 9f, y - 2f, x + 3f, y - 3f, x + 3f, y)
                                            cubicTo(x + 3f, y + 3f, x + 9f, y + 2f, x + 9f, y + 7f)
                                            lineTo(x - 9f, y + 7f)
                                            cubicTo(x - 9f, y + 2f, x - 3f, y + 3f, x - 3f, y)
                                            cubicTo(x - 3f, y - 3f, x - 9f, y - 2f, x - 9f, y - 7f)
                                            close()
                                        }
                                        drawPath(markerPath, markerColor)
                                    } else {
                                        val diamond = Path().apply {
                                            moveTo(x, y - 8f)
                                            lineTo(x + 8f, y)
                                            lineTo(x, y + 8f)
                                            lineTo(x - 8f, y)
                                            close()
                                        }
                                        drawPath(
                                            diamond,
                                            if (isActive) Color.White else color
                                        )
                                    }
                                }
                            }

                            // TIME LABELS
                            // (Canvas can't draw text; skip)
                    }
                }
            }
            }

            val selectedFrame = activeSelection?.let { (prop, time) ->
                keyframeMap[prop].orEmpty().minByOrNull { abs(it.time - time) }
                    ?.takeIf { abs(it.time - time) < 0.1f }
            }
            if (enableEaseEditing && !readOnly &&
                activeSelection != null && selectedFrame != null
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF141414))
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        "linear" to "Linear",
                        "easeIn" to "Ease In",
                        "easeOut" to "Ease Out",
                        "easeInOut" to "Ease In-Out"
                    ).forEach { (ease, label) ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(7.dp))
                                .background(
                                    if (selectedFrame.ease == ease) Color(0xFF5B3DA5)
                                    else Color(0xFF282828)
                                )
                                .pointerInput(activeSelection, ease) {
                                    detectTapGestures {
                                        onSetKeyframeEase(
                                            activeSelection.first,
                                            activeSelection.second,
                                            ease
                                        )
                                    }
                                }
                                .padding(horizontal = 10.dp, vertical = 7.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(label, color = Color.White, fontSize = 10.sp)
                        }
                    }
                }
            }

            // HINT
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

@Composable
private fun KeyframeGraphHost(
    inlinePanel: Boolean,
    onClose: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    if (inlinePanel) {
        FeaturePanel(title = "📊 Keyframe Graph", onClose = onClose) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp)
                    .fillMaxHeight(),
                content = content
            )
        }
    } else {
        Dialog(
            onDismissRequest = onClose,
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF0A0A0A))
            ) {
                Column(modifier = Modifier.fillMaxSize(), content = content)
            }
        }
    }
}