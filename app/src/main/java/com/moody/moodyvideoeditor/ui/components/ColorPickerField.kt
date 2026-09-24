package com.moody.moodyvideoeditor.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

@Composable
fun ColorPickerField(
    label: String,
    colorLong: Long,
    onChange: (Long) -> Unit
) {
    // Internal state — initialized once from parent
    var hsv by remember { mutableStateOf(argbToHsv(colorLong)) }
    var hexText by remember { mutableStateOf(longToHex(colorLong)) }
    var lastSeenParent by remember { mutableStateOf(colorLong) }

    // Sync ONLY when parent changes externally (not from our own onChange)
    LaunchedEffect(colorLong) {
        if (colorLong != lastSeenParent) {
            hsv = argbToHsv(colorLong)
            hexText = longToHex(colorLong)
            lastSeenParent = colorLong
        }
    }

    // Wrap onChange to remember our own changes
    fun push(newColor: Long) {
        lastSeenParent = newColor
        onChange(newColor)
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                label,
                color = Color(0xFF888888),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(60.dp)
            )
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(colorLong))
            )
            OutlinedTextField(
                value = hexText,
                onValueChange = { newHex ->
                    val filtered = newHex.filter { it.isLetterOrDigit() || it == '#' }.take(7)
                    hexText = filtered
                    val parsed = hexToLong(filtered)
                    if (parsed != null) {
                        hsv = argbToHsv(parsed)
                        push(parsed)
                    }
                },
                placeholder = { Text("#RRGGBB", color = Color(0xFF666666), fontSize = 10.sp) },
                modifier = Modifier.weight(1f).height(46.dp),
                singleLine = true,
                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFF7C3AED),
                    unfocusedBorderColor = Color(0xFF303030)
                )
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(110.dp)
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                val newHsv =
                                    computeHsvFromWheel(offset, size.width.toFloat(), hsv[2])
                                hsv = newHsv
                                val argb = hsvToArgb(newHsv[0], newHsv[1], newHsv[2])
                                hexText = longToHex(argb)
                                push(argb)
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                val pos = change.position
                                val newHsv = computeHsvFromWheel(pos, size.width.toFloat(), hsv[2])
                                hsv = newHsv
                                val argb = hsvToArgb(newHsv[0], newHsv[1], newHsv[2])
                                hexText = longToHex(argb)
                                push(argb)
                            }
                        )
                    }
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            val newHsv = computeHsvFromWheel(offset, size.width.toFloat(), hsv[2])
                            hsv = newHsv
                            val argb = hsvToArgb(newHsv[0], newHsv[1], newHsv[2])
                            hexText = longToHex(argb)
                            push(argb)
                        }
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val radius = size.minDimension / 2f
                    val center = Offset(size.width / 2f, size.height / 2f)

                    val hueColors = (0..360 step 15).map { h -> Color.hsv(h.toFloat(), 1f, 1f) }
                    drawCircle(
                        brush = Brush.sweepGradient(colors = hueColors, center = center),
                        radius = radius,
                        center = center
                    )
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color.White, Color.Transparent),
                            center = center,
                            radius = radius
                        ),
                        radius = radius,
                        center = center
                    )

                    val angleRad = Math.toRadians(hsv[0].toDouble())
                    val dist = hsv[1] * radius * 0.92f
                    val px = center.x + cos(angleRad).toFloat() * dist
                    val py = center.y + sin(angleRad).toFloat() * dist

                    drawCircle(
                        color = Color.White, radius = 9f,
                        center = Offset(px, py), style = Stroke(width = 3f)
                    )
                    drawCircle(
                        color = Color.Black.copy(alpha = 0.5f), radius = 9f,
                        center = Offset(px, py), style = Stroke(width = 1f)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Brightness  ${(hsv[2] * 100).toInt()}%",
                    color = Color(0xFF888888), fontSize = 9.sp, fontWeight = FontWeight.Bold
                )
                Slider(
                    value = hsv[2],
                    onValueChange = { newV ->
                        val newHsv = floatArrayOf(hsv[0], hsv[1], newV)
                        hsv = newHsv
                        val argb = hsvToArgb(newHsv[0], newHsv[1], newHsv[2])
                        hexText = longToHex(argb)
                        push(argb)
                    },
                    valueRange = 0f..1f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFF7C3AED),
                        activeTrackColor = Color(0xFF7C3AED),
                        inactiveTrackColor = Color(0xFF303030)
                    )
                )
            }
        }
    }
}

// ─── Helpers ─────────────────────────────────────────────────
private fun computeHsvFromWheel(offset: Offset, sizeW: Float, currentV: Float): FloatArray {
    val radius = sizeW / 2f
    val cx = radius
    val cy = radius
    val dx = offset.x - cx
    val dy = offset.y - cy
    val dist = sqrt(dx * dx + dy * dy)
    val sat = (dist / radius).coerceIn(0f, 1f)
    var angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
    if (angle < 0f) angle += 360f
    val v = if (currentV < 0.05f) 1f else currentV
    return floatArrayOf(angle, sat, v)
}

fun hsvToArgb(h: Float, s: Float, v: Float): Long {
    val hh = ((h % 360f) + 360f) % 360f
    val ss = s.coerceIn(0f, 1f)
    val vv = v.coerceIn(0f, 1f)
    val c = vv * ss
    val x = c * (1f - abs((hh / 60f) % 2f - 1f))
    val m = vv - c
    val (r1, g1, b1) = when {
        hh < 60f -> Triple(c, x, 0f)
        hh < 120f -> Triple(x, c, 0f)
        hh < 180f -> Triple(0f, c, x)
        hh < 240f -> Triple(0f, x, c)
        hh < 300f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    val r = ((r1 + m) * 255f).toInt().coerceIn(0, 255)
    val g = ((g1 + m) * 255f).toInt().coerceIn(0, 255)
    val b = ((b1 + m) * 255f).toInt().coerceIn(0, 255)
    return (0xFFL shl 24) or (r.toLong() shl 16) or (g.toLong() shl 8) or b.toLong()
}

fun argbToHsv(color: Long): FloatArray {
    val r = ((color shr 16) and 0xFF).toInt() / 255f
    val g = ((color shr 8) and 0xFF).toInt() / 255f
    val b = (color and 0xFF).toInt() / 255f
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val d = max - min
    val h = when {
        d == 0f -> 0f
        max == r -> 60f * (((g - b) / d) % 6f)
        max == g -> 60f * (((b - r) / d) + 2f)
        else -> 60f * (((r - g) / d) + 4f)
    }
    val hh = if (h < 0f) h + 360f else h
    val s = if (max == 0f) 0f else d / max
    return floatArrayOf(hh, s, max)
}

fun longToHex(colorLong: Long): String {
    val r = ((colorLong shr 16) and 0xFF).toInt()
    val g = ((colorLong shr 8) and 0xFF).toInt()
    val b = (colorLong and 0xFF).toInt()
    return "#%02X%02X%02X".format(r, g, b)
}

fun hexToLong(hex: String): Long? {
    val h = hex.removePrefix("#").trim()
    return when (h.length) {
        6 -> try {
            val r = h.substring(0, 2).toInt(16)
            val g = h.substring(2, 4).toInt(16)
            val b = h.substring(4, 6).toInt(16)
            (0xFFL shl 24) or (r.toLong() shl 16) or (g.toLong() shl 8) or b.toLong()
        } catch (_: Exception) {
            null
        }

        3 -> try {
            val r = h[0].toString().toInt(16) * 17
            val g = h[1].toString().toInt(16) * 17
            val b = h[2].toString().toInt(16) * 17
            (0xFFL shl 24) or (r.toLong() shl 16) or (g.toLong() shl 8) or b.toLong()
        } catch (_: Exception) {
            null
        }

        else -> null
    }
}