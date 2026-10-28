package com.moody.moodyvideoeditor.utils

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.ui.text.font.FontFamily
import com.moody.moodyvideoeditor.data.TextState
import kotlin.math.min

object TextRenderContract {

    const val REFERENCE_WIDTH = 720f
    const val REFERENCE_WIDTH_PX = REFERENCE_WIDTH

    // 🆕 Reference dimension = min(W, H) — aspect-ratio invariant
    fun referenceDim(canvasWidthPx: Float, canvasHeightPx: Float): Float =
        min(canvasWidthPx, canvasHeightPx).coerceAtLeast(1f)

    // Legacy: width-only (kept for compat)
    fun fontScale(canvasWidthPx: Float): Float = canvasWidthPx / REFERENCE_WIDTH

    // 🆕 Aspect-invariant font scale
    fun fontScale(canvasWidthPx: Float, canvasHeightPx: Float): Float =
        referenceDim(canvasWidthPx, canvasHeightPx) / REFERENCE_WIDTH

    // Legacy
    fun scaledFontSize(baseSizePx: Float, canvasWidthPx: Float): Float =
        (baseSizePx * fontScale(canvasWidthPx)).coerceAtLeast(8f)

    // 🆕 Aspect-invariant
    fun scaledFontSize(baseSizePx: Float, canvasWidthPx: Float, canvasHeightPx: Float): Float =
        (baseSizePx * fontScale(canvasWidthPx, canvasHeightPx)).coerceAtLeast(8f)

    // Legacy
    fun fittedFontSizePx(
        content: String,
        baseSizePx: Float,
        canvasWidthPx: Float,
        maxWidthPercent: Float,
        typeface: Typeface
    ): Float = fittedFontSizePx(
        content, baseSizePx, canvasWidthPx, canvasWidthPx, maxWidthPercent, typeface
    )

    // 🆕 Aspect-invariant
    fun fittedFontSizePx(
        content: String,
        baseSizePx: Float,
        canvasWidthPx: Float,
        canvasHeightPx: Float,
        maxWidthPercent: Float,
        typeface: Typeface
    ): Float {
        val initialSize = scaledFontSize(baseSizePx, canvasWidthPx, canvasHeightPx)
        val measurePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface
            textSize = initialSize
            textAlign = Paint.Align.CENTER
        }
        val naturalWidth = measurePaint.measureText(content)
        val maxWidthPx = canvasWidthPx * (maxWidthPercent.coerceIn(30f, 100f) / 100f)
        return if (naturalWidth > maxWidthPx && naturalWidth > 0f) {
            initialSize * (maxWidthPx / naturalWidth)
        } else {
            initialSize
        }
    }

    // Legacy
    fun posX(percent: Float, canvasW: Float): Float = percent / 100f * canvasW
    fun posY(percent: Float, canvasH: Float): Float = percent / 100f * canvasH

    // 🆕 Aspect-invariant position (center-anchored)
    fun posX(percent: Float, canvasWidthPx: Float, canvasHeightPx: Float): Float {
        val ref = referenceDim(canvasWidthPx, canvasHeightPx)
        return canvasWidthPx / 2f + (percent - 50f) / 100f * ref
    }
    fun posY(percent: Float, canvasWidthPx: Float, canvasHeightPx: Float): Float {
        val ref = referenceDim(canvasWidthPx, canvasHeightPx)
        return canvasHeightPx / 2f + (percent - 50f) / 100f * ref
    }

    fun resolveFontFamily(name: String): FontFamily = FontLibrary.familyFor(name)

    fun androidTypefaceFor(fontName: String, bold: Boolean, italic: Boolean): Typeface =
        FontLibrary.typefaceFor(fontName, bold, italic)

    fun androidAlignFor(alignment: String): Paint.Align = when (alignment) {
        "left" -> Paint.Align.LEFT
        "right" -> Paint.Align.RIGHT
        else -> Paint.Align.CENTER
    }

    data class GlowLayer(val fontScale: Float, val blurScale: Float, val alpha: Float)
    fun glowLayers(): List<GlowLayer> = listOf(
        GlowLayer(1.00f, 0.45f, 0.72f),
        GlowLayer(1.00f, 0.95f, 0.48f),
        GlowLayer(1.00f, 1.80f, 0.24f),
        GlowLayer(1.00f, 3.00f, 0.12f),
    )

    fun weight(st: TextState): Boolean = st.fontWeight == "bold"
    fun italic(st: TextState): Boolean = st.fontStyle == "italic"
}