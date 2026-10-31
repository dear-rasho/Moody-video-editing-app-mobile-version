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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moody.moodyvideoeditor.data.TextState
import com.moody.moodyvideoeditor.utils.AnimationsEngine
import com.moody.moodyvideoeditor.utils.FontLibrary

// Mirrors js/workspace/textRenderer.js
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
    val frame = AnimationsEngine.computeTextFrame(
        text.animation,
        text.captionPresetAnimation,
        progress,
        elapsed
    )

    val family = FontLibrary.familyFor(text.fontFamily)
    val solidColor = Color(text.color)
    val density = LocalDensity.current
    val outlineWidthPx = with(density) { text.strokeWidth.dp.toPx() }
    val textAlignment = when (text.alignment) {
        "left" -> TextAlign.Left
        "right" -> TextAlign.Right
        else -> TextAlign.Center
    }
    val fontWeight = if (text.fontWeight == "bold") FontWeight.Bold else FontWeight.Normal
    val fontStyle = if (text.fontStyle == "italic") FontStyle.Italic else FontStyle.Normal

    val gradient: Brush? = if (text.gradientEnabled) {
        val extent = 600f
        Brush.linearGradient(
            colors = listOf(Color(text.gradientColor1), Color(text.gradientColor2)),
            start = Offset(
                (text.gradientStartX.coerceIn(0f, 1f) - 0.5f) * extent * 2f,
                (text.gradientStartY.coerceIn(0f, 1f) - 0.5f) * extent * 2f
            ),
            end = Offset(
                (text.gradientEndX.coerceIn(0f, 1f) - 0.5f) * extent * 2f,
                (text.gradientEndY.coerceIn(0f, 1f) - 0.5f) * extent * 2f
            )
        )
    } else null

    val shadow = if (text.shadowEnabled) Shadow(
        color = Color(text.shadowColor),
        offset = Offset(text.shadowOffsetX, text.shadowOffsetY),
        blurRadius = text.shadowBlur
    ) else null

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .offset(
                    x = ((text.positionX - 50f) * 3f).dp,
                    y = ((text.positionY - 50f) * 1.7f).dp
                )
                .graphicsLayer(
                    scaleX = (text.scale / 100f) * frame.scaleX,
                    scaleY = (text.scale / 100f) * frame.scaleY,
                    rotationZ = text.rotation + frame.rotationZ,
                    rotationX = frame.rotationX,
                    rotationY = frame.rotationY,
                    cameraDistance = 16f * density.density,
                    alpha = (text.opacity / 100f) * frame.alpha,
                    translationX = frame.translateX,
                    translationY = frame.translateY
                ),
            contentAlignment = Alignment.Center
        ) {
            if (text.glowEnabled && text.glowRadius > 0f) {
                Text(
                    text = text.content,
                    color = Color(text.glowColor).copy(alpha = 0.32f),
                    fontSize = text.fontSize.sp,
                    fontWeight = fontWeight,
                    fontStyle = fontStyle,
                    fontFamily = family,
                    textAlign = textAlignment,
                    style = TextStyle(
                        shadow = Shadow(
                            color = Color(text.glowColor),
                            offset = Offset.Zero,
                            blurRadius = text.glowRadius
                        )
                    )
                )
            }
            if (text.strokeEnabled && text.strokeWidth > 0f) {
                Text(
                    text = text.content,
                    color = Color(text.strokeColor),
                    fontSize = text.fontSize.sp,
                    fontWeight = fontWeight,
                    fontStyle = fontStyle,
                    fontFamily = family,
                    textAlign = textAlignment,
                    style = TextStyle(
                        drawStyle = Stroke(
                            width = outlineWidthPx,
                            join = androidx.compose.ui.graphics.StrokeJoin.Round
                        )
                    )
                )
            }
        Text(
            text = text.content,
            color = if (gradient != null) Color.Unspecified else solidColor,
            fontSize = text.fontSize.sp,
            fontWeight = fontWeight,
            fontStyle = fontStyle,
            fontFamily = family,
            textAlign = textAlignment,
            style = TextStyle(
                brush = gradient,
                shadow = shadow
            )
        )
        }
    }
}
