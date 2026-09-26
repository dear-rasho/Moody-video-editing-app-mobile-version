package com.moody.moodyvideoeditor.utils

/**
 * Ratio-aware text scaling.
 * Font sizes are stored relative to a REFERENCE canvas width (400dp).
 * At render time, they scale to the actual canvas size.
 */
object TextScaler {

    const val REFERENCE_WIDTH_DP = 400f

    /** Scale factor: actual canvas width / reference width */
    fun scaleFactor(canvasWidthDp: Float): Float =
        (canvasWidthDp / REFERENCE_WIDTH_DP).coerceIn(0.35f, 3.5f)

    /** Effective font size (dp) — scales with canvas */
    fun fontSize(
        baseSize: Int,
        canvasWidthDp: Float,
        minDp: Float = 8f,
        maxDp: Float = 300f
    ): Float {
        val scaled = baseSize * scaleFactor(canvasWidthDp)
        return scaled.coerceIn(minDp, maxDp)
    }

    /** Effective letter spacing */
    fun letterSpacing(base: Float, canvasWidthDp: Float): Float =
        base * scaleFactor(canvasWidthDp)

    /** Effective stroke width */
    fun strokeWidth(base: Float, canvasWidthDp: Float): Float =
        (base * scaleFactor(canvasWidthDp)).coerceIn(0f, 20f)

    /** Effective glow radius */
    fun glowRadius(base: Float, canvasWidthDp: Float): Float =
        (base * scaleFactor(canvasWidthDp)).coerceIn(0f, 80f)

    /** Max text width (dp) based on maxWidth % */
    fun maxTextWidth(canvasWidthDp: Float, maxWidthPct: Float): Float =
        canvasWidthDp * (maxWidthPct.coerceIn(20f, 100f) / 100f)

    /** Effective line height (dp) */
    fun lineHeight(fontSizeDp: Float, multiplier: Float): Float =
        fontSizeDp * multiplier.coerceIn(0.8f, 3f)

    /** Clamp position so text stays inside canvas (0..100%) */
    fun clampPosition(
        x: Float,
        y: Float,
        textWidthDp: Float,
        textHeightDp: Float,
        canvasWidthDp: Float,
        canvasHeightDp: Float
    ): Pair<Float, Float> {
        val halfWPct = (textWidthDp / 2f / canvasWidthDp * 100f).coerceAtMost(50f)
        val halfHPct = (textHeightDp / 2f / canvasHeightDp * 100f).coerceAtMost(50f)

        val cx = x.coerceIn(halfWPct, 100f - halfWPct)
        val cy = y.coerceIn(halfHPct, 100f - halfHPct)
        return cx to cy
    }
}