package com.moody.moodyvideoeditor.ui.features

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moody.moodyvideoeditor.data.ColorMatteDefaults
import com.moody.moodyvideoeditor.data.ColorMatteLibrary
import com.moody.moodyvideoeditor.data.ColorMatteMode
import com.moody.moodyvideoeditor.data.ColorMatteStyle
import com.moody.moodyvideoeditor.ui.components.ColorPickerField
import com.moody.moodyvideoeditor.ui.components.FeaturePanel

@Composable
fun ColorMattePanel(
    editLayerId: String? = null,
    initialStyle: ColorMatteStyle = ColorMatteStyle(),
    initialOpacity: Float = ColorMatteDefaults.DEFAULT_OPACITY,
    initialDurationMs: Long = ColorMatteDefaults.DEFAULT_DURATION_MS,
    onApply: (style: ColorMatteStyle, opacity: Float, durationMs: Long) -> Unit,
    onUpdate: (style: ColorMatteStyle, opacity: Float, durationMs: Long) -> Unit = { _, _, _ -> },
    onDelete: () -> Unit = {},
    onClose: () -> Unit
) {
    val isEditMode = editLayerId != null

    var style by remember(editLayerId) { mutableStateOf(initialStyle) }
    var opacity by remember(editLayerId) { mutableFloatStateOf(initialOpacity) }
    var durationMs by remember(editLayerId) { mutableLongStateOf(initialDurationMs) }

    fun notifyChange() {
        if (isEditMode) onUpdate(style, opacity, durationMs)
    }

    FeaturePanel(
        title = if (isEditMode) "🎨 Edit Color Matte" else "🎨 Color Matte",
        onClose = onClose
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 380.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {

            // ─── LIVE PREVIEW ───
            Text(
                "Preview",
                color = Color(0xFF888888),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 4.dp)
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF0F0F0F))
                    .border(1.dp, Color(0xFF2A2A2A), RoundedCornerShape(10.dp))
                    .padding(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(styleToBrush(style).let {
                            it
                        })
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .background(
                                Color.Black.copy(
                                    alpha = 1f - (opacity / 100f).coerceIn(0f, 1f)
                                )
                            )
                    )
                }
            }

            // ─── MODE SELECTOR ───
            Text(
                "Mode",
                color = Color(0xFF888888),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 4.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ModeChip(
                    label = "● Solid",
                    active = style.mode == ColorMatteMode.SOLID
                ) {
                    style = style.copy(mode = ColorMatteMode.SOLID)
                    notifyChange()
                }
                ModeChip(
                    label = "▬ Ramp",
                    active = style.mode == ColorMatteMode.RAMP
                ) {
                    style = style.copy(mode = ColorMatteMode.RAMP)
                    notifyChange()
                }
                ModeChip(
                    label = "◱ 4-Color",
                    active = style.mode == ColorMatteMode.FOUR_COLOR
                ) {
                    style = style.copy(mode = ColorMatteMode.FOUR_COLOR)
                    notifyChange()
                }
            }

            // ─── MODE-SPECIFIC EDITORS ───
            when (style.mode) {

                ColorMatteMode.SOLID -> {
                    Text(
                        "Quick Colors",
                        color = Color(0xFF888888),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 4.dp)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ColorMatteLibrary.SOLID_PRESETS.forEach { preset ->
                            val isActive = style.solidColor == preset.colorLong
                            SwatchBox(
                                color = preset.colorLong,
                                active = isActive,
                                label = preset.label
                            ) {
                                style = style.copy(solidColor = preset.colorLong)
                                notifyChange()
                            }
                        }
                    }

                    ColorPickerField(
                        label = "Color",
                        colorLong = style.solidColor,
                        onChange = {
                            style = style.copy(solidColor = it)
                            notifyChange()
                        }
                    )
                }

                ColorMatteMode.RAMP -> {
                    Text(
                        "Gradient Presets",
                        color = Color(0xFF888888),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 4.dp)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ColorMatteLibrary.RAMP_PRESETS.forEach { p ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            Brush.linearGradient(
                                                colors = listOf(
                                                    Color(p.color1),
                                                    Color(p.color2)
                                                )
                                            )
                                        )
                                        .border(
                                            1.dp, Color(0xFF2A2A2A),
                                            RoundedCornerShape(8.dp)
                                        )
                                        .pointerInput(p.label) {
                                            detectTapGestures {
                                                style = style.copy(
                                                    rampColor1 = p.color1,
                                                    rampColor2 = p.color2
                                                )
                                                notifyChange()
                                            }
                                        }
                                )
                                Text(
                                    p.label,
                                    color = Color(0xFF888888),
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    ColorPickerField(
                        label = "Color 1",
                        colorLong = style.rampColor1,
                        onChange = {
                            style = style.copy(rampColor1 = it)
                            notifyChange()
                        }
                    )
                    ColorPickerField(
                        label = "Color 2",
                        colorLong = style.rampColor2,
                        onChange = {
                            style = style.copy(rampColor2 = it)
                            notifyChange()
                        }
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "Angle",
                            color = Color(0xFF888888),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(64.dp)
                        )
                        Slider(
                            value = style.rampAngleDeg.coerceIn(0f, 360f),
                            onValueChange = {
                                style = style.copy(rampAngleDeg = it)
                                notifyChange()
                            },
                            valueRange = 0f..360f,
                            modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFF7C3AED),
                                activeTrackColor = Color(0xFF7C3AED),
                                inactiveTrackColor = Color(0xFF303030)
                            )
                        )
                        Text(
                            "${style.rampAngleDeg.toInt()}°",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(42.dp)
                        )
                    }
                }

                ColorMatteMode.FOUR_COLOR -> {
                    Text(
                        "4 Corner Colors",
                        color = Color(0xFF888888),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 4.dp)
                    )

                    ColorPickerField(
                        label = "Top Left",
                        colorLong = style.topLeft,
                        onChange = {
                            style = style.copy(topLeft = it)
                            notifyChange()
                        }
                    )
                    ColorPickerField(
                        label = "Top Right",
                        colorLong = style.topRight,
                        onChange = {
                            style = style.copy(topRight = it)
                            notifyChange()
                        }
                    )
                    ColorPickerField(
                        label = "Bot Left",
                        colorLong = style.bottomLeft,
                        onChange = {
                            style = style.copy(bottomLeft = it)
                            notifyChange()
                        }
                    )
                    ColorPickerField(
                        label = "Bot Right",
                        colorLong = style.bottomRight,
                        onChange = {
                            style = style.copy(bottomRight = it)
                            notifyChange()
                        }
                    )
                }
            }

            // ─── OPACITY ───
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "Opacity",
                    color = Color(0xFF888888),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(64.dp)
                )
                Slider(
                    value = opacity.coerceIn(0f, 100f),
                    onValueChange = {
                        opacity = it
                        notifyChange()
                    },
                    valueRange = 0f..100f,
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFF7C3AED),
                        activeTrackColor = Color(0xFF7C3AED),
                        inactiveTrackColor = Color(0xFF303030)
                    )
                )
                Text(
                    "${opacity.toInt()}%",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(42.dp)
                )
            }

            // ─── DURATION ───
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "Duration",
                    color = Color(0xFF888888),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(64.dp)
                )
                Slider(
                    value = (durationMs / 1000f).coerceIn(0.5f, 60f),
                    onValueChange = {
                        durationMs = (it * 1000f).toLong()
                        notifyChange()
                    },
                    valueRange = 0.5f..60f,
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFF7C3AED),
                        activeTrackColor = Color(0xFF7C3AED),
                        inactiveTrackColor = Color(0xFF303030)
                    )
                )
                Text(
                    "%.1fs".format(durationMs / 1000f),
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(42.dp)
                )
            }

            // ─── ACTION BUTTONS ───
            Spacer(Modifier.height(4.dp))

            if (isEditMode) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFFF6B6B).copy(alpha = 0.15f))
                            .pointerInput(Unit) {
                                detectTapGestures {
                                    onDelete()
                                    onClose()
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "🗑 Delete",
                            color = Color(0xFFFF6B6B),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(2f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF7C3AED))
                            .pointerInput(Unit) {
                                detectTapGestures {
                                    onUpdate(style, opacity, durationMs)
                                    onClose()
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "✓ Done",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF7C3AED))
                        .pointerInput(Unit) {
                            detectTapGestures {
                                onApply(style, opacity, durationMs)
                                onClose()
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "🎨 Add Color Matte Layer",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Text(
                "💡 Layer on top of the selected track. Drag it, scale it, keyframe it — just like an image.",
                color = Color(0xFF666666),
                fontSize = 9.sp,
                modifier = Modifier.padding(start = 4.dp)
            )
        }
    }
}

// ─── Helpers ───

@Composable
private fun ModeChip(label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .height(32.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (active) Color(0xFF7C3AED) else Color(0xFF181818))
            .pointerInput(label) { detectTapGestures { onClick() } }
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun SwatchBox(
    color: Long,
    active: Boolean,
    label: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Box(
            modifier = Modifier
                .size(if (active) 40.dp else 36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(color))
                .border(
                    if (active) 2.dp else 1.dp,
                    if (active) Color.White else Color(0xFF2A2A2A),
                    RoundedCornerShape(8.dp)
                )
                .pointerInput(color) { detectTapGestures { onClick() } }
        )
        Text(
            label,
            color = if (active) Color.White else Color(0xFF888888),
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

// ─── Style → Brush converter (for preview) ───

private fun styleToBrush(style: ColorMatteStyle): Brush = when (style.mode) {
    ColorMatteMode.SOLID -> Brush.linearGradient(
        listOf(Color(style.solidColor), Color(style.solidColor))
    )

    ColorMatteMode.RAMP -> {
        val rad = Math.toRadians(style.rampAngleDeg.toDouble())
        val dx = kotlin.math.cos(rad).toFloat()
        val dy = kotlin.math.sin(rad).toFloat()
        Brush.linearGradient(
            colors = listOf(Color(style.rampColor1), Color(style.rampColor2)),
            start = Offset(-dx * 200f, -dy * 200f),
            end = Offset(dx * 200f, dy * 200f)
        )
    }

    ColorMatteMode.FOUR_COLOR -> Brush.linearGradient(
        // Approximation for the panel preview only.
        // Real 4-color blend happens in PreviewCanvas + FFmpeg.
        colors = listOf(
            Color(style.topLeft),
            Color(style.topRight),
            Color(style.bottomRight),
            Color(style.bottomLeft)
        )
    )
}