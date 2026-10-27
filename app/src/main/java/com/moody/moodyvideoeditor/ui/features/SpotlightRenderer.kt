package com.moody.moodyvideoeditor.ui.features

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.moody.moodyvideoeditor.data.SpotlightState
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

@Composable
fun SpotlightOverlay(
    spotlight: SpotlightState,
    isSelected: Boolean,
    onGestureStart: () -> Unit,
    onGestureEnd: () -> Unit,
    onChange: (SpotlightState) -> Unit
) {
    val currentSpotlight by rememberUpdatedState(spotlight)
    val currentOnChange by rememberUpdatedState(onChange)

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(isSelected) {
                var activeHandle = -1
                detectDragGestures(
                    onDragStart = { position ->
                        if (!isSelected) return@detectDragGestures
                        val config = currentSpotlight
                        val src = Offset(
                            size.width * config.sourceX / 100f,
                            size.height * config.sourceY / 100f
                        )
                        val target = effectiveTarget(config)
                        val dst = Offset(
                            size.width * target.x / 100f,
                            size.height * target.y / 100f
                        )
                        val hitRadius = 48.dp.toPx()
                        val sourceDistance = (position - src).getDistance()
                        val targetDistance = (position - dst).getDistance()
                        activeHandle = when {
                            minOf(sourceDistance, targetDistance) > hitRadius -> -1
                            sourceDistance <= targetDistance -> 0
                            else -> 1
                        }
                        if (activeHandle >= 0) onGestureStart()
                    },
                    onDragEnd = {
                        if (activeHandle >= 0) onGestureEnd()
                        activeHandle = -1
                    },
                    onDragCancel = {
                        if (activeHandle >= 0) onGestureEnd()
                        activeHandle = -1
                    },
                    onDrag = { change, _ ->
                        if (activeHandle >= 0) {
                            val x = (change.position.x / size.width * 100f).coerceIn(0f, 100f)
                            val y = (change.position.y / size.height * 100f).coerceIn(0f, 100f)
                            val config = currentSpotlight
                            val unrotatedTarget = if (activeHandle == 1) {
                                unrotateTarget(config, x, y)
                            } else null
                            currentOnChange(
                                if (activeHandle == 0) config.copy(sourceX = x, sourceY = y)
                                else config.copy(
                                    targetX = unrotatedTarget?.x ?: x,
                                    targetY = unrotatedTarget?.y ?: y
                                )
                            )
                            change.consume()
                        }
                    }
                )
            }
    ) {
        drawSpotlight(spotlight, showHandles = isSelected)
    }
}

fun DrawScope.drawSpotlight(spotlight: SpotlightState, showHandles: Boolean = false) {
    val source = Offset(size.width * spotlight.sourceX / 100f, size.height * spotlight.sourceY / 100f)
    val targetPosition = effectiveTarget(spotlight)
    val target = Offset(size.width * targetPosition.x / 100f, size.height * targetPosition.y / 100f)
    val dx = target.x - source.x
    val dy = target.y - source.y
    val distance = kotlin.math.sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
    val directionX = dx / distance
    val directionY = dy / distance
    val perpendicularX = -directionY
    val perpendicularY = directionX
    val halfWidth = (distance * tan(Math.toRadians(
        spotlight.radiusDeg.coerceIn(6f, 80f).toDouble() / 2.0
    ).toFloat()))
        .coerceIn(1f, size.maxDimension * 1.5f)
    val sourceColor = Color(spotlight.color)
    val targetColor = Color(spotlight.targetColor)
    val intensity = spotlight.intensity.coerceIn(0f, 2.5f)
    val completePasses = intensity.toInt()
    val finalPass = intensity - completePasses
    val edgeBands = if (spotlight.softness <= 0.01f) 1 else 24
    val glowRadius = maxOf(halfWidth * 1.15f, distance * 0.1f)

    fun DrawScope.drawEmission(pass: Float) {
        if (pass <= 0f) return
        val bandAlpha = 0.3f * pass / edgeBands
        for (band in 0 until edgeBands) {
            val featherFraction = if (edgeBands == 1) 0f else band.toFloat() / (edgeBands - 1)
            val widthFactor = 1f - 0.96f * spotlight.softness.coerceIn(0f, 1f) * featherFraction
            val bandWidth = halfWidth * widthFactor
            val sourceWidth = (bandWidth * 0.018f).coerceAtLeast(0.5f)
            val path = Path().apply {
                moveTo(
                    source.x + perpendicularX * sourceWidth,
                    source.y + perpendicularY * sourceWidth
                )
                lineTo(
                    target.x + perpendicularX * bandWidth,
                    target.y + perpendicularY * bandWidth
                )
                lineTo(
                    target.x - perpendicularX * bandWidth,
                    target.y - perpendicularY * bandWidth
                )
                lineTo(
                    source.x - perpendicularX * sourceWidth,
                    source.y - perpendicularY * sourceWidth
                )
                close()
            }
            val beamBrush = Brush.linearGradient(
                colorStops = arrayOf(
                    0f to sourceColor.copy(alpha = 0f),
                    0.18f to sourceColor.copy(alpha = bandAlpha),
                    0.72f to targetColor.copy(alpha = bandAlpha * 0.58f),
                    0.94f to targetColor.copy(alpha = bandAlpha * 0.14f),
                    1f to targetColor.copy(alpha = 0f)
                ),
                start = source,
                end = target
            )
            drawPath(path, beamBrush, blendMode = BlendMode.Plus)
        }

        drawCircle(
            brush = Brush.radialGradient(
                colorStops = arrayOf(
                    0f to targetColor.copy(alpha = 0.4f * pass),
                    0.42f to targetColor.copy(alpha = 0.22f * pass),
                    0.76f to targetColor.copy(alpha = 0.07f * pass),
                    1f to Color.Transparent
                ),
                center = target,
                radius = glowRadius
            ),
            radius = glowRadius,
            center = target,
            blendMode = BlendMode.Plus
        )
    }

    repeat(completePasses) { drawEmission(1f) }
    drawEmission(finalPass)

    if (showHandles) {
        drawLine(
            color = Color.White.copy(alpha = 0.65f),
            start = source,
            end = target,
            strokeWidth = 1.dp.toPx()
        )
        drawCircle(Color(0xFFFFC857), radius = 9.dp.toPx(), center = source)
        drawCircle(Color.White, radius = 9.dp.toPx(), center = source, style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
        drawCircle(Color(0xFF3DD6D0), radius = 9.dp.toPx(), center = target)
        drawCircle(Color.White, radius = 9.dp.toPx(), center = target, style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
    }
}

private fun effectiveTarget(state: SpotlightState): Offset {
    val sourceX = state.sourceX
    val sourceY = state.sourceY
    val dx = state.targetX - sourceX
    val dy = state.targetY - sourceY
    val angle = Math.toRadians(state.rotationDeg.toDouble())
    return Offset(
        sourceX + (dx * cos(angle) - dy * sin(angle)).toFloat(),
        sourceY + (dx * sin(angle) + dy * cos(angle)).toFloat()
    )
}

private fun unrotateTarget(state: SpotlightState, targetX: Float, targetY: Float): Offset {
    val dx = targetX - state.sourceX
    val dy = targetY - state.sourceY
    val angle = Math.toRadians(-state.rotationDeg.toDouble())
    return Offset(
        (state.sourceX + dx * cos(angle) - dy * sin(angle)).toFloat().coerceIn(0f, 100f),
        (state.sourceY + dx * sin(angle) + dy * cos(angle)).toFloat().coerceIn(0f, 100f)
    )
}
