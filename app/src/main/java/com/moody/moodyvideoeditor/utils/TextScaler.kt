package com.moody.moodyvideoeditor.utils

// ═══════════════════════════════════════════════════════════════
//  TEXT SCALER — Single source of truth for text sizing
// ═══════════════════════════════════════════════════════════════
//
//  REFERENCE_WIDTH = 720f — the "logical" canvas width.
//
//  Both Preview (Compose) and Export (Canvas) use this SAME reference:
//    scale = canvasWidthPx / 720
//    fontSizePx = storedFontSize * scale
//
//  This guarantees identical visual proportion in preview and export.
//
object TextScaler {

    // ✅ Single source of truth
    const val REFERENCE_WIDTH = 720f

    // Legacy aliases (kept for compatibility)
    const val REFERENCE_WIDTH_DP = REFERENCE_WIDTH
    const val REFERENCE_WIDTH_PX = REFERENCE_WIDTH

    // Scale factor: actual canvas width (in px) / reference width
    fun scaleFactor(canvasWidthPx: Float): Float =
        (canvasWidthPx / REFERENCE_WIDTH).coerceIn(0.1f, 5.0f)

    // ─────────────────────────────────────────────────────────────
    //  Font size — accepts BOTH canvasWidthPx and canvasWidthDp names
    //  (values are treated the same — pass actual canvas width in px)
    // ─────────────────────────────────────────────────────────────
    fun fontSize(
        baseSize: Int,
        canvasWidthPx: Float = 0f,
        canvasWidthDp: Float = 0f,
        minPx: Float = 4f,
        maxPx: Float = 600f
    ): Float {
        val w = if (canvasWidthPx > 0f) canvasWidthPx else canvasWidthDp
        val scaled = baseSize * scaleFactor(w)
        return scaled.coerceIn(minPx, maxPx)
    }

    // Letter spacing — accepts both names
    fun letterSpacing(
        base: Float,
        canvasWidthPx: Float = 0f,
        canvasWidthDp: Float = 0f
    ): Float {
        val w = if (canvasWidthPx > 0f) canvasWidthPx else canvasWidthDp
        return base * scaleFactor(w)
    }

    // Stroke width — accepts both names
    fun strokeWidth(
        base: Float,
        canvasWidthPx: Float = 0f,
        canvasWidthDp: Float = 0f
    ): Float {
        val w = if (canvasWidthPx > 0f) canvasWidthPx else canvasWidthDp
        return (base * scaleFactor(w)).coerceIn(0f, 60f)
    }

    // Glow radius — accepts both names
    fun glowRadius(
        base: Float,
        canvasWidthPx: Float = 0f,
        canvasWidthDp: Float = 0f
    ): Float {
        val w = if (canvasWidthPx > 0f) canvasWidthPx else canvasWidthDp
        return (base * scaleFactor(w)).coerceIn(0f, 200f)
    }

    // Max text width in px based on maxWidth %
    fun maxTextWidth(
        maxWidthPct: Float,
        canvasWidthPx: Float = 0f,
        canvasWidthDp: Float = 0f
    ): Float {
        val w = if (canvasWidthPx > 0f) canvasWidthPx else canvasWidthDp
        return w * (maxWidthPct.coerceIn(20f, 100f) / 100f)
    }

    // Line height (px)
    fun lineHeight(fontSizePx: Float, multiplier: Float): Float =
        fontSizePx * multiplier.coerceIn(0.8f, 3f)

    // ─────────────────────────────────────────────────────────────
    //  Position clamp — matches export formula EXACTLY
    //  Both preview and export use: coerceIn(0f, 100f)
    //  Accepts BOTH Dp and Px parameter names.
    // ─────────────────────────────────────────────────────────────
    fun clampPosition(
        x: Float,
        y: Float,
        textWidthDp: Float = 0f,
        textHeightDp: Float = 0f,
        canvasWidthDp: Float = 0f,
        canvasHeightDp: Float = 0f,
        textWidthPx: Float? = null,
        textHeightPx: Float? = null,
        canvasWidthPx: Float? = null,
        canvasHeightPx: Float? = null
    ): Pair<Float, Float> {
        // Values are only 0..100 — clamp and return.
        // Text can extend slightly outside canvas (matches preview).
        val cx = x.coerceIn(0f, 100f)
        val cy = y.coerceIn(0f, 100f)
        return cx to cy
    }
}