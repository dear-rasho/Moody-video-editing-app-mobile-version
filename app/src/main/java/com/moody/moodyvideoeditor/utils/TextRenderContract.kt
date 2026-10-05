package com.moody.moodyvideoeditor.utils

import androidx.compose.ui.text.font.FontFamily
import com.moody.moodyvideoeditor.data.TextState

// ═══════════════════════════════════════════════════════════════
//  TEXT RENDER CONTRACT
//  Shared contract between Compose (preview) and Android (export)
//  so both render identically.
// ═══════════════════════════════════════════════════════════════
object TextRenderContract {

    // ✅ SAME reference as TextScaler
    const val REFERENCE_WIDTH = 720f

    // Legacy alias
    const val REFERENCE_WIDTH_PX = REFERENCE_WIDTH

    // ─── SIZE ────────────────────────────────────────────────────
    fun fontScale(canvasWidthPx: Float): Float =
        (canvasWidthPx / REFERENCE_WIDTH).coerceIn(0.1f, 5.0f)

    fun scaledFontSize(baseSizePx: Float, canvasWidthPx: Float): Float =
        (baseSizePx * fontScale(canvasWidthPx)).coerceIn(4f, 600f)

    // ─── POSITION ────────────────────────────────────────────────
    fun posX(percent: Float, canvasW: Float): Float = percent / 100f * canvasW
    fun posY(percent: Float, canvasH: Float): Float = percent / 100f * canvasH

    // ─── FONT FAMILY (Compose) ───────────────────────────────────
    fun resolveFontFamily(name: String): FontFamily = FontLibrary.familyFor(name)

    // ─── TYPEFACE (Android) ──────────────────────────────────────
    fun androidTypefaceFor(
        fontName: String,
        bold: Boolean,
        italic: Boolean
    ): android.graphics.Typeface = FontLibrary.typefaceFor(fontName, bold, italic)

    // ─── ALIGNMENT ───────────────────────────────────────────────
    fun androidAlignFor(alignment: String): android.graphics.Paint.Align = when (alignment) {
        "left" -> android.graphics.Paint.Align.LEFT
        "right" -> android.graphics.Paint.Align.RIGHT
        else -> android.graphics.Paint.Align.CENTER
    }

    // ─── GLOW LAYERS ─────────────────────────────────────────────
    data class GlowLayer(
        val fontScale: Float,
        val blurScale: Float,
        val alpha: Float
    )

    fun glowLayers(): List<GlowLayer> = listOf(
        GlowLayer(1.00f, 0.55f, 0.60f),
        GlowLayer(1.06f, 1.00f, 0.40f),
        GlowLayer(1.15f, 1.60f, 0.25f),
    )

    // ─── HELPERS ─────────────────────────────────────────────────
    fun weight(st: TextState): Boolean = st.fontWeight == "bold"
    fun italic(st: TextState): Boolean = st.fontStyle == "italic"
}