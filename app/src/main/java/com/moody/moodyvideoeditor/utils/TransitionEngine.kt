package com.moody.moodyvideoeditor.utils

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.moody.moodyvideoeditor.data.TransitionState
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Mirrors js/workspace/transitionEngine.js
 * Renders transition overlays on top of the preview canvas.
 */
object TransitionEngine {

    /** Is the transition currently active at this timeline position? */
    fun isActive(state: TransitionState?, clipStartMs: Long, currentPosMs: Long): Boolean {
        if (state == null || !state.isActive) return false
        val dur = state.durationMs
        return currentPosMs >= clipStartMs && currentPosMs < clipStartMs + dur
    }

    /** 0..1 progress through the transition */
    fun progress(state: TransitionState, clipStartMs: Long, currentPosMs: Long): Float {
        val dur = state.durationMs.coerceAtLeast(1L)
        val p = (currentPosMs - clipStartMs).toFloat() / dur.toFloat()
        return p.coerceIn(0f, 1f)
    }

    /**
     * Draws a transition overlay on top of the given scope.
     */
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

        when (state.key) {
            // ─── FADES ─────────────────────────────
            "fade" -> drawFade(scope, W, H, p, Color.Black, fadeOut = false)
            "dissolve" -> drawFade(scope, W, H, p, Color.Black, fadeOut = false)
            "fadeBlack" -> drawFade(scope, W, H, p, Color.Black, fadeOut = true)
            "fadeWhite" -> drawFade(scope, W, H, p, Color.White, fadeOut = true)

            // ─── FLASH ────────────────────────────
            "flashWhite" -> drawFlash(scope, W, H, p, Color.White)

            // ─── BLUR (approx via white veil) ─────
            "blur" -> drawFade(scope, W, H, p, Color(0xAAFFFFFF), fadeOut = false)

            // ─── SLIDES / PUSHES ──────────────────
            "pushLeft", "slideLeft" -> drawSlide(scope, W, H, p, direction = 0)
            "pushRight", "slideRight" -> drawSlide(scope, W, H, p, direction = 1)
            "pushUp", "slideUp" -> drawSlide(scope, W, H, p, direction = 2)
            "pushDown", "slideDown" -> drawSlide(scope, W, H, p, direction = 3)

            // ─── WIPES ────────────────────────────
            "wipeLeft" -> drawWipe(scope, W, H, p, direction = 0)
            "wipeRight" -> drawWipe(scope, W, H, p, direction = 1)
            "wipeUp" -> drawWipe(scope, W, H, p, direction = 2)
            "wipeDown" -> drawWipe(scope, W, H, p, direction = 3)

            // ─── SHAPES ───────────────────────────
            "circleIn" -> drawCircleIn(scope, W, H, p)
            "irisBox" -> drawIrisBox(scope, W, H, p)
            "clockWipe" -> drawClockWipe(scope, W, H, p)

            // ─── ZOOMS ────────────────────────────
            "zoomIn", "zoomOut", "crossZoom" ->
                drawFade(scope, W, H, p, Color.Black, fadeOut = false)

            // ─── SPINS ────────────────────────────
            "spinCW", "spinCCW", "swirl" ->
                drawFade(scope, W, H, p, Color.Black, fadeOut = false)

            // ─── GLITCH ───────────────────────────
            "rgbSplit", "glitch" -> drawGlitch(scope, W, H, p)
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  OVERLAY DRAWERS
    // ═══════════════════════════════════════════════════════════

    private fun drawFade(
        scope: DrawScope,
        W: Float,
        H: Float,
        p: Float,
        color: Color,
        fadeOut: Boolean
    ) {
        val alpha = if (fadeOut) {
            if (p < 0.5f) p * 2f else (1f - p) * 2f
        } else {
            1f - p
        }.coerceIn(0f, 1f)

        scope.drawRect(
            color = color.copy(alpha = alpha),
            topLeft = Offset.Zero,
            size = Size(W, H)
        )
    }

    private fun drawFlash(scope: DrawScope, W: Float, H: Float, p: Float, color: Color) {
        val alpha = if (p < 0.15f) p / 0.15f
        else if (p < 0.3f) 1f - (p - 0.15f) / 0.15f
        else 0f
        scope.drawRect(
            color = color.copy(alpha = alpha.coerceIn(0f, 1f)),
            topLeft = Offset.Zero,
            size = Size(W, H)
        )
    }

    /** direction: 0=left, 1=right, 2=up, 3=down */
    private fun drawSlide(scope: DrawScope, W: Float, H: Float, p: Float, direction: Int) {
        val slide = 1f - p
        val barW = W * 0.4f

        when (direction) {
            0 -> scope.drawRect(
                Color.Black.copy(alpha = slide),
                Offset(0f, 0f),
                Size(barW * slide, H)
            )

            1 -> scope.drawRect(
                Color.Black.copy(alpha = slide),
                Offset(W - barW * slide, 0f),
                Size(barW * slide, H)
            )

            2 -> scope.drawRect(
                Color.Black.copy(alpha = slide),
                Offset(0f, 0f),
                Size(W, barW * slide)
            )

            3 -> scope.drawRect(
                Color.Black.copy(alpha = slide),
                Offset(0f, H - barW * slide),
                Size(W, barW * slide)
            )
        }
        val edgeAlpha = (slide * 0.9f).coerceIn(0f, 1f)
        when (direction) {
            0 -> scope.drawRect(
                Color.White.copy(alpha = edgeAlpha),
                Offset(barW * slide - 3f, 0f),
                Size(3f, H)
            )

            1 -> scope.drawRect(
                Color.White.copy(alpha = edgeAlpha),
                Offset(W - barW * slide, 0f),
                Size(3f, H)
            )

            2 -> scope.drawRect(
                Color.White.copy(alpha = edgeAlpha),
                Offset(0f, barW * slide - 3f),
                Size(W, 3f)
            )

            3 -> scope.drawRect(
                Color.White.copy(alpha = edgeAlpha),
                Offset(0f, H - barW * slide),
                Size(W, 3f)
            )
        }
    }

    /** direction: 0=from left, 1=from right, 2=from top, 3=from bottom */
    private fun drawWipe(scope: DrawScope, W: Float, H: Float, p: Float, direction: Int) {
        val alpha = (1f - p).coerceIn(0f, 1f)
        when (direction) {
            0 -> scope.drawRect(
                Color.Black.copy(alpha = alpha),
                Offset(0f, 0f),
                Size(W * (1f - p), H)
            )

            1 -> scope.drawRect(
                Color.Black.copy(alpha = alpha),
                Offset(W * p, 0f),
                Size(W * (1f - p), H)
            )

            2 -> scope.drawRect(
                Color.Black.copy(alpha = alpha),
                Offset(0f, 0f),
                Size(W, H * (1f - p))
            )

            3 -> scope.drawRect(
                Color.Black.copy(alpha = alpha),
                Offset(0f, H * p),
                Size(W, H * (1f - p))
            )
        }
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

        scope.drawRect(
            Color.Black.copy(alpha = alpha),
            Offset(0f, 0f),
            Size(W, top.coerceAtLeast(0f))
        )
        scope.drawRect(
            Color.Black.copy(alpha = alpha),
            Offset(0f, top + size),
            Size(W, (H - top - size).coerceAtLeast(0f))
        )
        scope.drawRect(
            Color.Black.copy(alpha = alpha),
            Offset(0f, top),
            Size(left.coerceAtLeast(0f), size)
        )
        scope.drawRect(
            Color.Black.copy(alpha = alpha),
            Offset(left + size, top),
            Size((W - left - size).coerceAtLeast(0f), size)
        )
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
                sweepAngleDegrees = sweepDeg,

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
            val off = sin((p * 30f + i).toDouble()).toFloat() * 20f * alpha
            scope.drawRect(
                color = Color(1f, 0f, 0f, (alpha * 0.35f).coerceIn(0f, 1f)),
                topLeft = Offset(off, y),
                size = Size(W, h)
            )
            scope.drawRect(
                color = Color(0f, 1f, 1f, (alpha * 0.35f).coerceIn(0f, 1f)),
                topLeft = Offset(-off, y),
                size = Size(W, h)
            )
        }
    }
}