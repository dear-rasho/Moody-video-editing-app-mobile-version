package com.moody.moodyvideoeditor.utils

object TextScaler {

    // ✅ MUST match TextBitmapRenderer.REFERENCE_WIDTH_PX (720)
    const val REFERENCE_WIDTH_DP = 720f

    fun scaleFactor(canvasWidth: Float): Float =
        canvasWidth / REFERENCE_WIDTH_DP

    // ═══════════════════════════════════════════════════════════
    //  FONT SIZE — accepts EITHER canvasWidthDp OR canvasWidthPx
    // ═══════════════════════════════════════════════════════════
    fun fontSize(
        baseSize: Int,
        canvasWidthDp: Float = 0f,
        canvasWidthPx: Float = 0f,
        minDp: Float = 6f,
        maxDp: Float = 500f
    ): Float {
        val w = if (canvasWidthDp > 0f) canvasWidthDp else canvasWidthPx
        val scaled = baseSize * scaleFactor(w)
        return scaled.coerceIn(minDp, maxDp)
    }

    // ═══════════════════════════════════════════════════════════
    //  LETTER SPACING — accepts EITHER name
    // ═══════════════════════════════════════════════════════════
    fun letterSpacing(
        base: Float,
        canvasWidthDp: Float = 0f,
        canvasWidthPx: Float = 0f
    ): Float {
        val w = if (canvasWidthDp > 0f) canvasWidthDp else canvasWidthPx
        return base * scaleFactor(w)
    }

    // ═══════════════════════════════════════════════════════════
    //  STROKE WIDTH — accepts EITHER name
    // ═══════════════════════════════════════════════════════════
    fun strokeWidth(
        base: Float,
        canvasWidthDp: Float = 0f,
        canvasWidthPx: Float = 0f
    ): Float {
        val w = if (canvasWidthDp > 0f) canvasWidthDp else canvasWidthPx
        return (base * scaleFactor(w)).coerceIn(0f, 40f)
    }

    // ═══════════════════════════════════════════════════════════
    //  GLOW RADIUS — accepts EITHER name
    // ═══════════════════════════════════════════════════════════
    fun glowRadius(
        base: Float,
        canvasWidthDp: Float = 0f,
        canvasWidthPx: Float = 0f
    ): Float {
        val w = if (canvasWidthDp > 0f) canvasWidthDp else canvasWidthPx
        return (base * scaleFactor(w)).coerceIn(0f, 150f)
    }

    // ═══════════════════════════════════════════════════════════
    //  MAX TEXT WIDTH — accepts EITHER canvasWidthDp OR canvasWidthPx
    //  Called as:  TextScaler.maxTextWidth(
    //                  maxWidthPct = ...,
    //                  canvasWidthPx = ...
    //              )
    // ═══════════════════════════════════════════════════════════
    fun maxTextWidth(
        maxWidthPct: Float,
        canvasWidthDp: Float = 0f,
        canvasWidthPx: Float = 0f
    ): Float {
        val w = if (canvasWidthDp > 0f) canvasWidthDp else canvasWidthPx
        return w * (maxWidthPct.coerceIn(20f, 100f) / 100f)
    }

    // ═══════════════════════════════════════════════════════════
    //  LINE HEIGHT
    // ═══════════════════════════════════════════════════════════
    fun lineHeight(fontSizeDp: Float, multiplier: Float): Float {
        val safeMultiplier = multiplier.takeIf { it.isFinite() }
            ?.coerceIn(0.1f, 200f) ?: 1.2f
        return fontSizeDp * safeMultiplier
    }

    // ═══════════════════════════════════════════════════════════
    //  POSITION CLAMP — simple, matches export
    // ═══════════════════════════════════════════════════════════
    fun clampPosition(
        x: Float,
        y: Float,
        textWidthDp: Float,
        textHeightDp: Float,
        canvasWidthDp: Float,
        canvasHeightDp: Float
    ): Pair<Float, Float> {
        val cx = x.coerceIn(0f, 100f)
        val cy = y.coerceIn(0f, 100f)
        return cx to cy
    }
}