package com.moody.moodyvideoeditor.ui.features

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moody.moodyvideoeditor.data.Keyframe
import com.moody.moodyvideoeditor.data.KeyframeLibrary
import com.moody.moodyvideoeditor.ui.components.FeaturePanel
import com.moody.moodyvideoeditor.utils.KeyframeStore
import com.moody.moodyvideoeditor.utils.SpeedEngine
import kotlin.math.abs

// Mirrors js/features/speed.js
// Shows: info bar + presets + fine slider + reset.
@Composable
fun SpeedPanel(
    clipName: String,
    clipId: String? = null,
    baseDurationMs: Long,
    currentSpeed: Float,
    hasClipSelected: Boolean,
    clipDurationSec: Float,
    playheadTimeSec: Float,
    speedKeyframes: List<Keyframe>,
    onAddOrToggleKeyframe: () -> Unit,
    onUpdateKeyframe: (oldTime: Float, newTime: Float, newValue: Float) -> Unit,
    onDeleteKeyframe: (time: Float) -> Unit,
    onSetKeyframeEase: (time: Float, ease: String) -> Unit,
    onGraphEditStart: () -> Unit,
    onGraphEditEnd: () -> Unit,
    onSpeedChanged: (Float) -> Unit,
    onReset: () -> Unit,
    onClose: () -> Unit
) {
    FeaturePanel(title = "⏩ Speed · ${String.format("%.2f", currentSpeed)}x", onClose = onClose) {

        // NO CLIP
        if (!hasClipSelected) {
            EmptyState(
                icon = "👆",
                title = "No clip selected",
                text = "Tap a video or audio clip on the timeline first."
            )
            return@FeaturePanel
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScrollWithScrollbar(rememberScrollState())
        ) {
        var sliderValue by remember(currentSpeed) { mutableFloatStateOf(currentSpeed) }
        var selectedGraphTime by remember(clipId, clipName) { mutableStateOf<Float?>(null) }
        var graphZoom by remember { mutableFloatStateOf(1f) }
        val graphDurationSec by remember(clipId, clipName) {
            mutableFloatStateOf(clipDurationSec.coerceAtLeast(0.1f))
        }


        // PRESETS ROW
        Text(
            "$clipName · source ${"%.2f".format(baseDurationMs / 1000f)}s",
            color = Color(0xFF888888),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 4.dp)
        )
        Spacer(Modifier.height(4.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScrollWithTwoFinger(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            SpeedEngine.PRESETS.forEach { p ->
                val isActive = kotlin.math.abs(currentSpeed - p) < 0.01f
                Box(
                    modifier = Modifier
                        .height(34.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (isActive) Color(0xFF7C3AED) else Color(0xFF181818)
                        )
                        .pointerInput(p) {
                            detectTapGestures {
                                sliderValue = p
                                onSpeedChanged(p)
                            }
                        }
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "${p}x",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // FINE SLIDER
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "Fine",
                color = Color(0xFF888888),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(36.dp)
            )
            Slider(
                value = sliderValue,
                onValueChange = {
                    sliderValue = it
                    onSpeedChanged(it)
                },
                valueRange = SpeedEngine.MIN_SPEED..SpeedEngine.MAX_SPEED,
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFF7C3AED),
                    activeTrackColor = Color(0xFF7C3AED),
                    inactiveTrackColor = Color(0xFF303030)
                )
            )
            Text(
                String.format("%.2fx", sliderValue),
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(52.dp)
            )
        }

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Speed ramp",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "${speedKeyframes.size} keyframes · drag handles to set time and speed",
                    color = Color(0xFF888888),
                    fontSize = 9.sp
                )
            }
            Box(
                modifier = Modifier
                    .height(34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF7C3AED))
                    .pointerInput(Unit) {
                        detectTapGestures { onAddOrToggleKeyframe() }
                    }
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (speedKeyframes.any { abs(it.time - playheadTimeSec) < 0.05f }) {
                        "− Keyframe"
                    } else {
                        "+ Keyframe"
                    },
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Graph zoom", color = Color(0xFF888888), fontSize = 10.sp)
            Slider(
                value = graphZoom,
                onValueChange = { graphZoom = it },
                valueRange = 0.5f..4f,
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFF7C3AED),
                    activeTrackColor = Color(0xFF7C3AED),
                    inactiveTrackColor = Color(0xFF303030)
                )
            )
            Text(
                "${"%.1f".format(graphZoom)}x",
                color = Color.White,
                fontSize = 10.sp,
                modifier = Modifier.width(36.dp)
            )
        }

        SpeedRampGraph(
            keyframes = speedKeyframes,
            durationSec = graphDurationSec,
            graphKey = "$clipId:$clipName",
            currentSpeed = currentSpeed,
            playheadTimeSec = playheadTimeSec,
            graphZoom = graphZoom,
            onSelectKeyframe = { selectedGraphTime = it },
            onUpdateKeyframe = onUpdateKeyframe,
            onEditStart = onGraphEditStart,
            onEditEnd = onGraphEditEnd
        )

        val selectedKeyframe = selectedGraphTime?.let { time ->
            speedKeyframes.minByOrNull { abs(it.time - time) }
                ?.takeIf { abs(it.time - time) < 0.1f }
        } ?: speedKeyframes.minByOrNull { abs(it.time - playheadTimeSec) }
            ?.takeIf { abs(it.time - playheadTimeSec) < 0.1f }
            ?: speedKeyframes.firstOrNull()
        if (selectedKeyframe != null) {
            Text(
                "Selected · ${"%.2f".format(selectedKeyframe.time)}s · ${
                    "%.2f".format(selectedKeyframe.value)
                }x",
                color = Color(0xFFBDBDBD),
                fontSize = 10.sp
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScrollWithTwoFinger(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                listOf(
                    "linear" to "Linear",
                    "easeIn" to "Ease In",
                    "easeOut" to "Ease Out",
                    "easeInOut" to "Ease In-Out"
                ).forEach { (ease, label) ->
                    val active = selectedKeyframe.ease == ease
                    Box(
                        modifier = Modifier
                            .height(30.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .background(if (active) Color(0xFF7C3AED) else Color(0xFF181818))
                            .pointerInput(selectedKeyframe.time, ease) {
                                detectTapGestures {
                                    onSetKeyframeEase(selectedKeyframe.time, ease)
                                }
                            }
                            .padding(horizontal = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(label, color = Color.White, fontSize = 9.sp)
                    }
                }
                Box(
                    modifier = Modifier
                        .height(30.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(Color(0xFF4A2024))
                        .pointerInput(selectedKeyframe.time) {
                            detectTapGestures { onDeleteKeyframe(selectedKeyframe.time) }
                        }
                        .padding(horizontal = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Delete", color = Color(0xFFFF7777), fontSize = 9.sp)
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // RESET
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFFFF6B6B).copy(alpha = 0.15f))
                .pointerInput(Unit) { detectTapGestures { onReset() } },
            contentAlignment = Alignment.Center
        ) {
            Text(
                "↺ Reset Speed",
                color = Color(0xFFFF6B6B),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
        }
    }
}

@Composable
private fun SpeedRampGraph(
    keyframes: List<Keyframe>,
    durationSec: Float,
    graphKey: String,
    currentSpeed: Float,
    playheadTimeSec: Float,
    graphZoom: Float,
    onSelectKeyframe: (Float) -> Unit,
    onUpdateKeyframe: (oldTime: Float, newTime: Float, newValue: Float) -> Unit,
    onEditStart: () -> Unit,
    onEditEnd: () -> Unit
) {
    val duration by remember(graphKey) {
        mutableFloatStateOf(durationSec.coerceAtLeast(0.1f))
    }
    val sorted = keyframes.sortedBy { it.time }
    val latestSorted by rememberUpdatedState(sorted)
    var selectedTime by remember(graphKey) { mutableStateOf<Float?>(null) }
    var dragTime by remember { mutableFloatStateOf(-1f) }
    val selectedGraphTime by remember(graphKey, playheadTimeSec) {
        mutableStateOf(
            sorted.minByOrNull { abs(it.time - playheadTimeSec) }
                ?.takeIf { abs(it.time - playheadTimeSec) < 0.1f }?.time
        )
    }
    val hitRadiusPx = with(LocalDensity.current) { 40.dp.toPx() }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(250.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF10131A))
    ) {
        val graphWidth = maxOf(maxWidth.value, duration * 70f * graphZoom)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .horizontalScrollWithTwoFinger(rememberScrollState())
        ) {
            Canvas(
                modifier = Modifier
                    .width(graphWidth.dp)
                    .fillMaxHeight()
                    .pointerInput(graphKey) {
                detectTapGestures { position ->
                    val padX = 18f
                    val padY = 14f
                    val plotWidth = (size.width - padX * 2f).coerceAtLeast(1f)
                    val plotHeight = (size.height - padY * 2f).coerceAtLeast(1f)
                    selectedTime = latestSorted.minByOrNull { keyframe ->
                        val x = padX + keyframe.time / duration * plotWidth
                        val y = padY + (1f - (keyframe.value - SpeedEngine.MIN_SPEED) /
                                (SpeedEngine.MAX_SPEED - SpeedEngine.MIN_SPEED)) * plotHeight
                        (position.x - x) * (position.x - x) +
                                (position.y - y) * (position.y - y)
                    }?.takeIf { keyframe ->
                        val x = padX + keyframe.time / duration * plotWidth
                        val y = padY + (1f - (keyframe.value - SpeedEngine.MIN_SPEED) /
                                (SpeedEngine.MAX_SPEED - SpeedEngine.MIN_SPEED)) * plotHeight
                        abs(position.x - x) < hitRadiusPx && abs(position.y - y) < hitRadiusPx
                    }?.time
                    selectedTime?.let(onSelectKeyframe)
                }
            }
            .pointerInput(graphKey) {
                detectDragGestures(
                    onDragStart = { position ->
                        val padX = 18f
                        val padY = 14f
                        val plotWidth = (size.width - padX * 2f).coerceAtLeast(1f)
                        val plotHeight = (size.height - padY * 2f).coerceAtLeast(1f)
                        val hit = latestSorted.minByOrNull { keyframe ->
                            val x = padX + keyframe.time / duration * plotWidth
                            val y = padY + (1f - (keyframe.value - SpeedEngine.MIN_SPEED) /
                                    (SpeedEngine.MAX_SPEED - SpeedEngine.MIN_SPEED)) * plotHeight
                            (position.x - x) * (position.x - x) +
                                    (position.y - y) * (position.y - y)
                        }?.takeIf { keyframe ->
                            val x = padX + keyframe.time / duration * plotWidth
                            val y = padY + (1f - (keyframe.value - SpeedEngine.MIN_SPEED) /
                                    (SpeedEngine.MAX_SPEED - SpeedEngine.MIN_SPEED)) * plotHeight
                            abs(position.x - x) < hitRadiusPx &&
                                    abs(position.y - y) < hitRadiusPx
                        }
                        if (hit != null) {
                            dragTime = hit.time
                            selectedTime = hit.time
                            onSelectKeyframe(hit.time)
                            onEditStart()
                        }
                    },
                    onDrag = { change, _ ->
                        if (dragTime < 0f) return@detectDragGestures
                        change.consume()
                        val padX = 18f
                        val padY = 14f
                        val plotWidth = (size.width - padX * 2f).coerceAtLeast(1f)
                        val plotHeight = (size.height - padY * 2f).coerceAtLeast(1f)
                        val newTime = ((change.position.x - padX) / plotWidth * duration)
                            .coerceIn(0f, duration)
                        val newSpeed = (SpeedEngine.MAX_SPEED -
                                (change.position.y - padY) / plotHeight *
                                (SpeedEngine.MAX_SPEED - SpeedEngine.MIN_SPEED))
                            .coerceIn(SpeedEngine.MIN_SPEED, SpeedEngine.MAX_SPEED)
                        onUpdateKeyframe(dragTime, newTime, newSpeed)
                        dragTime = newTime
                        selectedTime = newTime
                        onSelectKeyframe(newTime)
                    },
                    onDragEnd = {
                        if (dragTime >= 0f) onEditEnd()
                        dragTime = -1f
                    },
                    onDragCancel = {
                        if (dragTime >= 0f) onEditEnd()
                        dragTime = -1f
                    }
                )
                    }
            ) {
        val padX = 18f
        val padY = 14f
        val plotWidth = (size.width - padX * 2f).coerceAtLeast(1f)
        val plotHeight = (size.height - padY * 2f).coerceAtLeast(1f)
        val speedRange = SpeedEngine.MAX_SPEED - SpeedEngine.MIN_SPEED
        val grid = Color(0xFF28303D)

        for (index in 0..4) {
            val y = padY + plotHeight * index / 4f
            drawLine(grid, Offset(padX, y), Offset(padX + plotWidth, y), 1f)
        }
        for (index in 0..4) {
            val x = padX + plotWidth * index / 4f
            drawLine(grid, Offset(x, padY), Offset(x, padY + plotHeight), 1f)
        }

        val curve = Path()
        val samples = 100
        for (index in 0..samples) {
            val time = duration * index / samples
            val speed = if (sorted.isEmpty()) currentSpeed else KeyframeStore.sample(
                mapOf(KeyframeLibrary.SPEED to sorted),
                KeyframeLibrary.SPEED,
                time,
                currentSpeed
            )
            val x = padX + time / duration * plotWidth
            val y = padY + (1f - (speed - SpeedEngine.MIN_SPEED) / speedRange) * plotHeight
            if (index == 0) curve.moveTo(x, y) else curve.lineTo(x, y)
        }
        drawPath(curve, Color(0xFF9B7BFF), style = Stroke(width = 3f))

        sorted.forEach { keyframe ->
            val x = padX + keyframe.time / duration * plotWidth
            val y = padY + (1f - (keyframe.value - SpeedEngine.MIN_SPEED) / speedRange) * plotHeight
            val selected = (selectedTime ?: selectedGraphTime)
                ?.let { abs(it - keyframe.time) < 0.06f } == true
            if (selected) {
                drawCircle(
                    Color(0xFFB69CFF),
                    radius = 11f,
                    center = Offset(x, y),
                    style = Stroke(width = 2f)
                )
            }
            drawCircle(Color.White, radius = 5.5f, center = Offset(x, y))
            drawCircle(Color(0xFF7C3AED), radius = 3f, center = Offset(x, y))
                }
            }
        }
    }
}


@Composable
private fun EmptyState(icon: String, title: String, text: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF181818))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(icon, fontSize = 24.sp)
        Text(title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text(text, color = Color(0xFF888888), fontSize = 10.sp)
    }
}