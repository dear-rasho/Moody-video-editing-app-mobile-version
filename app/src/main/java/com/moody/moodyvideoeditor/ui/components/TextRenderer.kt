package com.moody.moodyvideoeditor.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moody.moodyvideoeditor.data.TextState
import com.moody.moodyvideoeditor.utils.AnimationsEngine
import com.moody.moodyvideoeditor.utils.FontLibrary
import kotlin.math.cos
import kotlin.math.sin

/**
 * Mirrors js/workspace/textRenderer.js
 */
@Composable
fun TextOverlay(
    text: TextState,
    currentPosMs: Long,
    clipStartMs: Long
) {
    if (text.content.isBlank()) return

    FontLibrary.loadFont(text.fontFamily)

    val elapsed = ((currentPosMs - clipStartMs) / 1000f).coerceAtLeast(0f)
    val animDur = text.animationDuration.coerceAtLeast(0.1f)
    val progress = (elapsed / animDur).coerceIn(0f, 1f)
    val frame = AnimationsEngine.computeFrame(text.animation, progress, elapsed)

    val family = FontLibrary.familyFor(text.fontFamily)
    val solidColor = Color(text.color)

    // ─── GRADIENT (angle properly rotates the ramp) ───
    val gradient: Brush? = if (text.gradientEnabled) {
        val rad = Math.toRadians(text.gradientAngle.toDouble())
        val dx = cos(rad).toFloat()
        val dy = sin(rad).toFloat()
        // Big radius so gradient spans any text size
        val extent = 600f
        Brush.linearGradient(
            colors = listOf(Color(text.gradientColor1), Color(text.gradientColor2)),
            start = Offset(-dx * extent, -dy * extent),
            end = Offset(dx * extent, dy * extent)
        )
    } else null

    val shadow = if (text.shadowEnabled) Shadow(
        color = Color(text.shadowColor),
        offset = Offset(text.shadowOffsetX, text.shadowOffsetY),
        blurRadius = text.shadowBlur
    ) else null

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text.content,
            color = if (gradient != null) Color.Unspecified else solidColor,
            fontSize = text.fontSize.sp,
            fontWeight = if (text.fontWeight == "bold") FontWeight.Bold else FontWeight.Normal,
            fontStyle = if (text.fontStyle == "italic") FontStyle.Italic else FontStyle.Normal,
            fontFamily = family,
            textAlign = when (text.alignment) {
                "left" -> TextAlign.Left
                "right" -> TextAlign.Right
                else -> TextAlign.Center
            },
            style = TextStyle(
                brush = gradient,
                shadow = shadow
            ),
            modifier = Modifier
                .offset(
                    x = ((text.positionX - 50f) * 3f).dp,
                    y = ((text.positionY - 50f) * 1.7f).dp
                )
                .graphicsLayer(
                    scaleX = (text.scale / 100f) * frame.scaleX,
                    scaleY = (text.scale / 100f) * frame.scaleY,
                    rotationZ = text.rotation + frame.rotationZ,
                    alpha = (text.opacity / 100f) * frame.alpha,
                    translationX = frame.translateX,
                    translationY = frame.translateY
                )
        )
    }
}

@Composable
fun StickerOverlay(
    emoji: String,
    x: Float,
    y: Float,
    scale: Float,
    rotation: Float
) {
    if (emoji.isBlank()) return

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = emoji,
            fontSize = 48.sp,
            modifier = Modifier
                .offset(
                    x = ((x - 50f) * 3f).dp,
                    y = ((y - 50f) * 1.7f).dp
                )
                .graphicsLayer(
                    scaleX = scale / 100f,
                    scaleY = scale / 100f,
                    rotationZ = rotation
                )
        )
    }
}