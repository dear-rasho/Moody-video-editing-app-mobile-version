package com.moody.moodyvideoeditor.ui.features

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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moody.moodyvideoeditor.data.ColorWheelState
import com.moody.moodyvideoeditor.data.ToneValue
import com.moody.moodyvideoeditor.ui.components.FeaturePanel
import com.moody.moodyvideoeditor.utils.ColorWheelEngine

/**
 * Mirrors js/features/colorWheel.js
 * 3 interactive wheels (Shadows/Midtones/Highlights) + HDR White slider.
 */
@Composable
fun ColorWheelPanel(
    state: ColorWheelState,
    hasClipSelected: Boolean,
    onStateChanged: (ColorWheelState) -> Unit,
    onRemove: () -> Unit,
    onClose: () -> Unit
) {
    FeaturePanel(title = "🌈 Color Wheels", onClose = onClose) {

        if (!hasClipSelected) {
            EmptyState(
                icon = "👆",
                title = "No clip selected",
                text = "Pehle timeline pe ek clip select karo."
            )
            return@FeaturePanel
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {

            // ─── 3 WHEELS ROW (horizontal scroll) ────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                ColorWheelState.TONE_KEYS.forEachIndexed { index, key ->
                    WheelColumn(
                        label = ColorWheelState.TONE_LABELS[index],
                        value = state.toneFor(key),
                        onChange = { newVal ->
                            onStateChanged(state.setTone(key, newVal))
                        },
                        onReset = {
                            onStateChanged(state.setTone(key, ToneValue.ZERO))
                        }
                    )
                }
            }

            // ─── HDR WHITE ───────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "HDR",
                    color = Color(0xFF888888),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(36.dp)
                )
                Slider(
                    value = state.hdrWhite,
                    onValueChange = { onStateChanged(state.copy(hdrWhite = it)) },
                    valueRange = 0f..200f,
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFF7C3AED),
                        activeTrackColor = Color(0xFF7C3AED),
                        inactiveTrackColor = Color(0xFF303030)
                    )
                )
                Text(
                    "${state.hdrWhite.toInt()}",
                    color = if (state.hdrWhite != 100f) Color(0xFF7C3AED) else Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(36.dp)
                )
            }

            // ─── REMOVE ──────────────────────────────────
            if (state.hasAnyChange) {
                Spacer(Modifier.height(2.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFFF6B6B).copy(alpha = 0.15f))
                        .pointerInput(Unit) { detectTapGestures { onRemove() } },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "🗑 Remove Color Wheels",
                        color = Color(0xFFFF6B6B),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  SINGLE WHEEL COLUMN — label + wheel + intensity slider
// ═══════════════════════════════════════════════════════════════
@Composable
private fun WheelColumn(
    label: String,
    value: ToneValue,
    onChange: (ToneValue) -> Unit,
    onReset: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // ─── Header ─────────────────────────────────
        Row(
            modifier = Modifier.width(130.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                label.uppercase(),
                color = Color(0xFF888888),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFF181818))
                    .pointerInput(Unit) { detectTapGestures { onReset() } },
                contentAlignment = Alignment.Center
            ) {
                Text("↺", color = Color(0xFF888888), fontSize = 11.sp)
            }
        }

        // ─── Interactive Wheel ──────────────────────
        InteractiveWheel(
            value = value,
            onChange = onChange
        )

        // ─── Intensity Slider ───────────────────────
        Row(
            modifier = Modifier.width(130.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Slider(
                value = value.intensity,
                onValueChange = { onChange(value.copy(intensity = it)) },
                valueRange = 0f..100f,
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFF7C3AED),
                    activeTrackColor = Color(0xFF7C3AED),
                    inactiveTrackColor = Color(0xFF303030)
                )
            )
            Text(
                "${value.intensity.toInt()}%",
                color = if (value.intensity > 0f) Color(0xFF7C3AED) else Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(34.dp)
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  INTERACTIVE WHEEL — mirrors JS `.cw-wheel` + puck
// ═══════════════════════════════════════════════════════════════
@Composable
private fun InteractiveWheel(
    value: ToneValue,
    onChange: (ToneValue) -> Unit
) {
    var wheelSizePx by remember { mutableStateOf(0f) }

    Box(
        modifier = Modifier
            .size(120.dp)
            .aspectRatio(1f)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val x = (offset.x - center.x) / (size.width / 2f)
                        val y = (offset.y - center.y) / (size.height / 2f)
                        val (hue, sat) = ColorWheelEngine.positionToHueSat(x, y)
                        onChange(
                            value.copy(
                                hue = hue,
                                saturation = sat,
                                intensity = if (value.intensity == 0f) 50f else value.intensity
                            )
                        )
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val pos = change.position
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val x = (pos.x - center.x) / (size.width / 2f)
                        val y = (pos.y - center.y) / (size.height / 2f)
                        val (hue, sat) = ColorWheelEngine.positionToHueSat(x, y)
                        onChange(
                            value.copy(
                                hue = hue,
                                saturation = sat,
                                intensity = if (value.intensity == 0f) 50f else value.intensity
                            )
                        )
                    }
                )
            }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val x = (offset.x - center.x) / (size.width / 2f)
                    val y = (offset.y - center.y) / (size.height / 2f)
                    val (hue, sat) = ColorWheelEngine.positionToHueSat(x, y)
                    onChange(
                        value.copy(
                            hue = hue,
                            saturation = sat,
                            intensity = if (value.intensity == 0f) 50f else value.intensity
                        )
                    )
                }
            }
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            wheelSizePx = size.width
            val radius = size.minDimension / 2f
            val center = Offset(size.width / 2f, size.height / 2f)

            // 1) Conic gradient (hue ring) via sweep gradient
            val hueColors = listOf(
                Color.Red, Color.Yellow, Color.Green,
                Color.Cyan, Color.Blue, Color.Magenta, Color.Red
            )
            val sweep = Brush.sweepGradient(colors = hueColors, center = center)
            drawCircle(brush = sweep, radius = radius, center = center)

            // 2) Radial white overlay (center → white, edges → color)
            val radial = Brush.radialGradient(
                colors = listOf(Color.White, Color.Transparent),
                center = center,
                radius = radius
            )
            drawCircle(brush = radial, radius = radius, center = center)

            // 3) Subtle inner shadow ring
            drawCircle(
                color = Color(0x33000000),
                radius = radius - 1f,
                center = center,
                style = Stroke(width = 2f)
            )

            // 4) Puck — mirrors JS `.cw-puck`
            val (px, py) = ColorWheelEngine.puckOffset(value.hue, value.saturation)
            val puckX = center.x + px * radius * 0.9f
            val puckY = center.y + py * radius * 0.9f
            val puckR = 9f

            // Outer white ring
            drawCircle(
                color = Color.White,
                radius = puckR,
                center = Offset(puckX, puckY),
                style = Stroke(width = 3f)
            )
            // Center dot
            drawCircle(
                color = Color.White,
                radius = 2f,
                center = Offset(puckX, puckY)
            )
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