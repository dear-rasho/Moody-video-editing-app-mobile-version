package com.moody.moodyvideoeditor.ui.features
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
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
import androidx.compose.runtime.rememberUpdatedState
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
import com.moody.moodyvideoeditor.data.ColorMatteDefaults
import com.moody.moodyvideoeditor.data.ColorMatteLibrary
import com.moody.moodyvideoeditor.data.ColorMatteMode
import com.moody.moodyvideoeditor.data.ColorMatteStyle
import com.moody.moodyvideoeditor.ui.components.ColorPickerField
import com.moody.moodyvideoeditor.ui.components.FeaturePanel
import com.moody.moodyvideoeditor.ui.components.FourColorMatteGradient

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
                .verticalScrollWithScrollbar(rememberScrollState()),
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
                    .size(120.dp)
                    .align(Alignment.CenterHorizontally)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF0F0F0F))
                    .border(1.dp, Color(0xFF2A2A2A), RoundedCornerShape(10.dp))
                    .padding(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(8.dp))
                ) {
                    if (style.mode == ColorMatteMode.SOLID) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(style.solidColor.toInt()))
                        )
                    } else {
                        MatteAngleControl(
                            style = style,
                            modifier = Modifier.fillMaxSize(),
                            onAngleChanged = { angle ->
                                style = if (style.mode == ColorMatteMode.RAMP) {
                                    style.copy(rampAngleDeg = angle)
                                } else {
                                    style.copy(fourColorAngleDeg = angle)
                                }
                                notifyChange()
                            }
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
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
                    .horizontalScrollWithTwoFinger(rememberScrollState()),
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
                            .horizontalScrollWithTwoFinger(rememberScrollState()),
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
                            .horizontalScrollWithTwoFinger(rememberScrollState()),
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

                    Text(
                        "Drag either color dot on the preview to set the ramp angle.",
                        color = Color(0xFF666666),
                        fontSize = 9.sp,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }

                ColorMatteMode.FOUR_COLOR -> {
                    Text(
                        "4 Corner Colors — drag any dot on the preview to rotate",
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
private fun MatteAngleControl(
    style: ColorMatteStyle,
    modifier: Modifier,
    onAngleChanged: (Float) -> Unit
) {
    val latestStyle by rememberUpdatedState(style)
    var activeHandle by remember { mutableStateOf(0) }

    Box(modifier = modifier) {
        if (style.mode == ColorMatteMode.FOUR_COLOR) {
            FourColorMatteGradient(style, Modifier.fillMaxSize())
        }
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(style.mode) {
                    fun handlePoints(current: ColorMatteStyle): List<Offset> {
                        val center = Offset(size.width / 2f, size.height / 2f)
                        if (current.mode == ColorMatteMode.RAMP) {
                            val radians = Math.toRadians(current.rampAngleDeg.toDouble())
                            val halfLength = kotlin.math.hypot(
                                size.width.toDouble(),
                                size.height.toDouble()
                            ).toFloat() / 2f
                            val dx = kotlin.math.cos(radians).toFloat() * halfLength
                            val dy = kotlin.math.sin(radians).toFloat() * halfLength
                            return listOf(
                                Offset(center.x - dx, center.y - dy),
                                Offset(center.x + dx, center.y + dy)
                            )
                        }

                        val radians = Math.toRadians(current.fourColorAngleDeg.toDouble())
                        val cos = kotlin.math.cos(radians).toFloat()
                        val sin = kotlin.math.sin(radians).toFloat()
                        val radius = minOf(size.width, size.height) * 0.38f
                        val baseAngles = listOf(-135f, -45f, 135f, 45f)
                        return baseAngles.map { baseAngle ->
                            val angle = Math.toRadians(baseAngle.toDouble())
                            val x = kotlin.math.cos(angle).toFloat() * radius
                            val y = kotlin.math.sin(angle).toFloat() * radius
                            Offset(
                                center.x + x * cos - y * sin,
                                center.y + x * sin + y * cos
                            )
                        }
                    }

                    detectDragGestures(
                        onDragStart = { position ->
                            val points = handlePoints(latestStyle)
                            activeHandle = points.indices.minByOrNull {
                                (points[it] - position).getDistance()
                            } ?: 0
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val center = Offset(size.width / 2f, size.height / 2f)
                            val dx = change.position.x - center.x
                            val dy = change.position.y - center.y
                            val pointerAngle =
                                Math.toDegrees(kotlin.math.atan2(dy, dx).toDouble())
                            val angle = if (latestStyle.mode == ColorMatteMode.RAMP) {
                                pointerAngle + if (activeHandle == 0) 180.0 else 0.0
                            } else {
                                val baseAngles = listOf(-135.0, -45.0, 135.0, 45.0)
                                pointerAngle - baseAngles[activeHandle.coerceIn(0, 3)]
                            }
                            onAngleChanged(((angle % 360.0 + 360.0) % 360.0).toFloat())
                        }
                    )
                }
        ) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val points: List<Offset>
            val colors: List<Color>
            if (style.mode == ColorMatteMode.RAMP) {
                val radians = Math.toRadians(style.rampAngleDeg.toDouble())
                val halfLength = kotlin.math.hypot(
                    size.width.toDouble(),
                    size.height.toDouble()
                ).toFloat() / 2f
                val dx = kotlin.math.cos(radians).toFloat() * halfLength
                val dy = kotlin.math.sin(radians).toFloat() * halfLength
                points = listOf(
                    Offset(center.x - dx, center.y - dy),
                    Offset(center.x + dx, center.y + dy)
                )
                colors = listOf(Color(style.rampColor1.toInt()), Color(style.rampColor2.toInt()))
            } else {
                val radians = Math.toRadians(style.fourColorAngleDeg.toDouble())
                val cos = kotlin.math.cos(radians).toFloat()
                val sin = kotlin.math.sin(radians).toFloat()
                val radius = minOf(size.width, size.height) * 0.38f
                val baseAngles = listOf(-135f, -45f, 135f, 45f)
                points = baseAngles.map { baseAngle ->
                    val angle = Math.toRadians(baseAngle.toDouble())
                    val x = kotlin.math.cos(angle).toFloat() * radius
                    val y = kotlin.math.sin(angle).toFloat() * radius
                    Offset(center.x + x * cos - y * sin, center.y + x * sin + y * cos)
                }
                colors = listOf(
                    Color(style.topLeft.toInt()),
                    Color(style.topRight.toInt()),
                    Color(style.bottomLeft.toInt()),
                    Color(style.bottomRight.toInt())
                )
            }

            if (style.mode == ColorMatteMode.RAMP) drawRect(styleToBrush(style))
            if (style.mode == ColorMatteMode.RAMP) {
                drawLine(
                    Color.White.copy(alpha = 0.75f),
                    points[0],
                    points[1],
                    strokeWidth = 2.dp.toPx()
                )
            } else {
                drawLine(Color.White.copy(alpha = 0.55f), points[0], points[3], strokeWidth = 1.dp.toPx())
                drawLine(Color.White.copy(alpha = 0.55f), points[1], points[2], strokeWidth = 1.dp.toPx())
                drawLine(Color.White.copy(alpha = 0.55f), points[0], points[1], strokeWidth = 1.dp.toPx())
                drawLine(Color.White.copy(alpha = 0.55f), points[2], points[3], strokeWidth = 1.dp.toPx())
            }
            points.forEachIndexed { index, point ->
                drawCircle(colors[index], radius = 7.dp.toPx(), center = point)
                drawCircle(
                    Color.White,
                    radius = 7.dp.toPx(),
                    center = point,
                    style = Stroke(width = 1.5.dp.toPx())
                )
            }
        }
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
                .background(Color(color.toInt()))
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
        listOf(Color(style.solidColor.toInt()), Color(style.solidColor.toInt()))
    )

    ColorMatteMode.RAMP -> {
        val rad = Math.toRadians(style.rampAngleDeg.toDouble())
        val dx = kotlin.math.cos(rad).toFloat()
        val dy = kotlin.math.sin(rad).toFloat()
        Brush.linearGradient(
            colors = listOf(
                Color(style.rampColor1.toInt()),
                Color(style.rampColor2.toInt())
            ),
            start = Offset(-dx * 200f, -dy * 200f),
            end = Offset(dx * 200f, dy * 200f)
        )
    }

    ColorMatteMode.FOUR_COLOR -> Brush.linearGradient(
        // Approximation for the panel preview only.
        // Real 4-color blend happens in PreviewCanvas + FFmpeg.
        colors = listOf(
            Color(style.topLeft.toInt()),
            Color(style.topRight.toInt()),
            Color(style.bottomRight.toInt()),
            Color(style.bottomLeft.toInt())
        )
    )
}