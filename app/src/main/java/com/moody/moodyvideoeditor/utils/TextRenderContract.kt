package com.moody.moodyvideoeditor.utils

import androidx.compose.ui.text.font.FontFamily
import com.moody.moodyvideoeditor.data.TextState

object TextRenderContract {

    const val REFERENCE_WIDTH_PX = 400f

    // ─── SIZE ─────────────────────────────────────
    fun fontScale(canvasWidthPx: Float): Float =
        (canvasWidthPx / REFERENCE_WIDTH_PX).coerceIn(0.35f, 3.5f)

    fun scaledFontSize(baseSizePx: Float, canvasWidthPx: Float): Float =
        (baseSizePx * fontScale(canvasWidthPx)).coerceIn(8f, 400f)

    // ─── POSITION ─────────────────────────────────
    fun posX(percent: Float, canvasW: Float): Float = percent / 100f * canvasW
    fun posY(percent: Float, canvasH: Float): Float = percent / 100f * canvasH

    // ─── FONT ─────────────────────────────────────
    fun resolveFontFamily(name: String): FontFamily {
        val n = name.lowercase().trim()
        return when {
            n.contains("mono") || n.contains("code") || n.contains("courier") ||
                    n.contains("consol") || n.contains("menlo") || n.contains("monaco") ->
                FontFamily.Monospace

            n.contains("script") || n.contains("hand") || n.contains("brush") ||
                    n.contains("cursive") || n.contains("comic") || n.contains("dancing") ||
                    n.contains("pacific") || n.contains("vibes") || n.contains("allura") ||
                    n.contains("satisfy") || n.contains("kaushan") || n.contains("parisienne") ||
                    n.contains("sacramento") || n.contains("tangerine") || n.contains("indie") ||
                    n.contains("patrick") || n.contains("kalam") || n.contains("amita") ||
                    n.contains("chopin") || n.contains("musiclife") || n.contains("caveat") ->
                FontFamily.Cursive

            n.contains("serif") || n.contains("times") || n.contains("georgia") ||
                    n.contains("garamond") || n.contains("baskerville") ||
                    n.contains("playfair") || n.contains("cinzel") || n.contains("bodoni") ||
                    n.contains("cormorant") || n.contains("merriweather") || n.contains("lora") ||
                    n.contains("crimson") || n.contains("prata") || n.contains("cardo") ||
                    n.contains("spectral") || n.contains("abril") || n.contains("palatino") ||
                    n.contains("book") || n.contains("cinematic") || n.contains("elegant") ||
                    n.contains("didot") ->
                FontFamily.Serif

            else -> FontFamily.SansSerif
        }
    }

    fun androidTypefaceFor(
        family: FontFamily,
        bold: Boolean,
        italic: Boolean
    ): android.graphics.Typeface {
        val base = when (family) {
            FontFamily.Monospace -> android.graphics.Typeface.MONOSPACE
            FontFamily.Serif -> android.graphics.Typeface.SERIF
            FontFamily.Cursive ->
                android.graphics.Typeface.create("cursive", android.graphics.Typeface.NORMAL)

            else -> android.graphics.Typeface.SANS_SERIF
        }
        val style = when {
            bold && italic -> android.graphics.Typeface.BOLD_ITALIC
            bold -> android.graphics.Typeface.BOLD
            italic -> android.graphics.Typeface.ITALIC
            else -> android.graphics.Typeface.NORMAL
        }
        return android.graphics.Typeface.create(base, style)
    }

    fun androidAlignFor(alignment: String): android.graphics.Paint.Align = when (alignment) {
        "left" -> android.graphics.Paint.Align.LEFT
        "right" -> android.graphics.Paint.Align.RIGHT
        else -> android.graphics.Paint.Align.CENTER
    }

    // ─── GLOW ─────────────────────────────────────
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

    // ─── HELPERS ──────────────────────────────────
    fun weight(st: TextState): Boolean = st.fontWeight == "bold"
    fun italic(st: TextState): Boolean = st.fontStyle == "italic"
}