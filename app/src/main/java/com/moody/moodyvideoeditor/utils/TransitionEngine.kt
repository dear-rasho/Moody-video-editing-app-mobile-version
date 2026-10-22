package com.moody.moodyvideoeditor.utils

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.moody.moodyvideoeditor.data.TransitionState
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object TransitionEngine {

    fun isActive(state: TransitionState?, clipStartMs: Long, currentPosMs: Long): Boolean {
        if (state == null || !state.isActive) return false
        val dur = state.durationMs
        return currentPosMs >= clipStartMs && currentPosMs < clipStartMs + dur
    }

    fun progress(state: TransitionState, clipStartMs: Long, currentPosMs: Long): Float {
        val dur = state.durationMs.coerceAtLeast(1L)
        val p = (currentPosMs - clipStartMs).toFloat() / dur.toFloat()
        return p.coerceIn(0f, 1f)
    }

    fun draw(
        scope: DrawScope,
        W: Float,
        H: Float,
        timeMs: Long,
        state: TransitionState,
        clipStartMs: Long
    ) {
        if (!isActive(state, clipStartMs, timeMs)) return
        val p = progress(state, clipStartMs, timeMs)
        val k = state.key.lowercase()

        when {
            // 1. LIGHT LEAKS & FLASHES (White / Yellow / Fire)
            k.contains("lightleak") || k.contains("flare") || k.contains("flash") ||
                    k.contains("white") || k.contains("burn") || k.contains("bloom") || k.contains("glow") -> {
                drawFlash(scope, W, H, p, Color.White)
            }

            // 2. WIPES & MATTES (Linear, Shapes)
            k.contains("circle") || k.contains("heart") || k.contains("star") || k.contains("diamond") -> {
                drawCircleIn(scope, W, H, p)
            }
            k.contains("wipe") || k.contains("slide") || k.contains("pan") || k.contains("tilt") -> {
                // Slide handles its own movement in Renderer, no heavy overlay needed
            }
            k.contains("clock") || k.contains("radial") -> {
                drawClockWipe(scope, W, H, p)
            }
            k.contains("vert") || k.contains("horz") || k.contains("matrix") -> {
                drawIrisBox(scope, W, H, p)
            }

            // 3. GLITCH & DISTORTION (RGB Split & Noise)
            k.contains("glitch") || k.contains("rgb") || k.contains("signal") || k.contains("vcr") || k.contains("vhs") -> {
                drawGlitch(scope, W, H, p)
            }

            // 4. DEFAULT FADES
            k.contains("fade") || k.contains("dissolve") || k.contains("blur") -> {
                drawFade(scope, W, H, p, Color.Black)
            }
            else -> {
                drawFade(scope, W, H, p, Color.Black)
            }
        }
    }

    private fun drawFade(scope: DrawScope, W: Float, H: Float, p: Float, color: Color) {
        val alpha = (1f - p).coerceIn(0f, 1f)
        scope.drawRect(color = color.copy(alpha = alpha), topLeft = Offset.Zero, size = Size(W, H))
    }

    private fun drawFlash(scope: DrawScope, W: Float, H: Float, p: Float, color: Color) {
        // Light leak / Flash effect (bright at middle of transition)
        val alpha = if (p < 0.5f) p * 2f else (1f - p) * 2f
        scope.drawRect(
            color = color.copy(alpha = alpha.coerceIn(0f, 1f)),
            topLeft = Offset.Zero, size = Size(W, H)
        )
        // Add a radial glow for light leaks
        scope.drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFFFFCC66).copy(alpha = alpha * 0.8f), Color.Transparent),
                center = Offset(W / 2, H / 2),
                radius = maxOf(W, H) * 0.8f
            ),
            radius = maxOf(W, H) * 0.8f,
            center = Offset(W / 2, H / 2)
        )
    }

    private fun drawCircleIn(scope: DrawScope, W: Float, H: Float, p: Float) {
        val maxR = sqrt(W * W + H * H) / 2f
        val r = maxR * p
        val path = Path().apply {
            addRect(Rect(0f, 0f, W, H))
            addOval(Rect(W / 2f - r, H / 2f - r, W / 2f + r, H / 2f + r))
            fillType = PathFillType.EvenOdd
        }
        scope.drawPath(path, Color.Black.copy(alpha = (1f - p).coerceIn(0f, 1f)))
    }

    private fun drawIrisBox(scope: DrawScope, W: Float, H: Float, p: Float) {
        val size = maxOf(W, H) * p
        val left = W / 2f - size / 2f
        val top = H / 2f - size / 2f
        val alpha = (1f - p).coerceIn(0f, 1f)

        scope.drawRect(Color.Black.copy(alpha = alpha), Offset(0f, 0f), Size(W, top.coerceAtLeast(0f)))
        scope.drawRect(Color.Black.copy(alpha = alpha), Offset(0f, top + size), Size(W, (H - top - size).coerceAtLeast(0f)))
        scope.drawRect(Color.Black.copy(alpha = alpha), Offset(0f, top), Size(left.coerceAtLeast(0f), size))
        scope.drawRect(Color.Black.copy(alpha = alpha), Offset(left + size, top), Size((W - left - size).coerceAtLeast(0f), size))
    }

    private fun drawClockWipe(scope: DrawScope, W: Float, H: Float, p: Float) {
        val cx = W / 2f
        val cy = H / 2f
        val R = sqrt(W * W + H * H) / 2f
        val sweepDeg = (360f * p).coerceIn(0f, 360f)

        val path = Path().apply {
            moveTo(cx, cy)
            lineTo(cx, cy - R)
            addArc(
                oval = Rect(cx - R, cy - R, cx + R, cy + R),
                startAngleDegrees = -90f,
                sweepAngleDegrees = sweepDeg
            )
            close()
        }
        scope.drawPath(path, Color.Black.copy(alpha = (1f - p).coerceIn(0f, 1f)))
    }

    private fun drawGlitch(scope: DrawScope, W: Float, H: Float, p: Float) {
        val alpha = if (p < 0.5f) p * 2f else (1f - p) * 2f
        val bands = 6
        for (i in 0 until bands) {
            val y = (i.toFloat() / bands) * H
            val h = H / bands
            val off = sin((p * 30f + i).toDouble()).toFloat() * 30f * alpha
            scope.drawRect(
                color = Color(1f, 0f, 0f, (alpha * 0.35f).coerceIn(0f, 1f)),
                topLeft = Offset(off, y), size = Size(W, h)
            )
            scope.drawRect(
                color = Color(0f, 1f, 1f, (alpha * 0.35f).coerceIn(0f, 1f)),
                topLeft = Offset(-off, y), size = Size(W, h)
            )
        }
    }
}