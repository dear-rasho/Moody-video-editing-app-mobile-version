package com.moody.moodyvideoeditor.utils

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.moody.moodyvideoeditor.data.EdgeGlowConfig

fun DrawScope.drawEdgeGlow(
    config: EdgeGlowConfig,
    timeSec: Float,
    radiusScale: Float = 1f
) {
    val frame = EffectsEngine.computeEdgeGlow(config, timeSec)
    val alpha = (
        config.intensity / 100f * frame.intensityMul * frame.alpha
        ).coerceIn(0f, 1f)
    if (alpha < 0.02f) return

    val thickness = (config.radius * frame.radiusMul * radiusScale).coerceIn(
        1f,
        minOf(size.width, size.height) * 0.45f
    )
    val color = frame.color.copy(alpha = alpha)
    val ox = frame.offsetX * radiusScale
    val oy = frame.offsetY * radiusScale

    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(color, Color.Transparent),
            startY = 0f,
            endY = thickness
        ),
        topLeft = Offset(ox, oy),
        size = Size(size.width, thickness)
    )
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(Color.Transparent, color),
            startY = size.height - thickness,
            endY = size.height
        ),
        topLeft = Offset(ox, size.height - thickness + oy),
        size = Size(size.width, thickness)
    )
    drawRect(
        brush = Brush.horizontalGradient(
            colors = listOf(color, Color.Transparent),
            startX = 0f,
            endX = thickness
        ),
        topLeft = Offset(ox, oy),
        size = Size(thickness, size.height)
    )
    drawRect(
        brush = Brush.horizontalGradient(
            colors = listOf(Color.Transparent, color),
            startX = size.width - thickness,
            endX = size.width
        ),
        topLeft = Offset(size.width - thickness + ox, oy),
        size = Size(thickness, size.height)
    )
}
