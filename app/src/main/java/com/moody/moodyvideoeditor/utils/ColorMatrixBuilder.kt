package com.moody.moodyvideoeditor.utils

import android.graphics.ColorMatrix
import com.moody.moodyvideoeditor.data.AdjustmentData

/**
 * ColorMatrix-based adjustments (real-time).
 * Supported: brightness, contrast, exposure, saturation, temperature,
 *            tint, whites, blacks, shadows, highlights, vibrance (approx),
 *            clarity (approx).
 * NOT supported: vignette, noise, sharpen, color channels.
 */
object ColorMatrixBuilder {

    fun build(adj: AdjustmentData): ColorMatrix {
        val cm = ColorMatrix()

        // 1) Brightness (-100..100)
        if (adj.brightness != 0f) {
            val offset = (adj.brightness / 100f) * 128f
            cm.postConcat(ColorMatrix(floatArrayOf(
                1f, 0f, 0f, 0f, offset,
                0f, 1f, 0f, 0f, offset,
                0f, 0f, 1f, 0f, offset,
                0f, 0f, 0f, 1f, 0f
            )))
        }

        // 2) Contrast
        if (adj.contrast != 0f) {
            val scale = 1f + (adj.contrast / 100f) * 0.5f
            val translate = (1f - scale) * 128f
            cm.postConcat(ColorMatrix(floatArrayOf(
                scale, 0f, 0f, 0f, translate,
                0f, scale, 0f, 0f, translate,
                0f, 0f, scale, 0f, translate,
                0f, 0f, 0f, 1f, 0f
            )))
        }

        // 3) Exposure
        if (adj.exposure != 0f) {
            val mult = Math.pow(2.0, (adj.exposure / 100.0)).toFloat()
            cm.postConcat(ColorMatrix(floatArrayOf(
                mult, 0f, 0f, 0f, 0f,
                0f, mult, 0f, 0f, 0f,
                0f, 0f, mult, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            )))
        }

        // 4) Saturation + Vibrance (combined)
        val totalSat = 1f + (adj.saturation / 100f) + (adj.vibrance / 200f)
        if (totalSat != 1f) {
            val sCM = ColorMatrix()
            sCM.setSaturation(totalSat.coerceIn(0f, 3f))
            cm.postConcat(sCM)
        }

        // 5) Temperature
        if (adj.temperature != 0f) {
            val r = 1f + (adj.temperature / 100f) * 0.3f
            val b = 1f - (adj.temperature / 100f) * 0.3f
            cm.postConcat(ColorMatrix(floatArrayOf(
                r, 0f, 0f, 0f, 0f,
                0f, 1f, 0f, 0f, 0f,
                0f, 0f, b, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            )))
        }

        // 6) Tint
        if (adj.tint != 0f) {
            val g = 1f - (adj.tint / 100f) * 0.3f
            val rb = 1f + (adj.tint / 100f) * 0.15f
            cm.postConcat(ColorMatrix(floatArrayOf(
                rb, 0f, 0f, 0f, 0f,
                0f, g, 0f, 0f, 0f,
                0f, 0f, rb, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            )))
        }

        // 7) Whites
        if (adj.whites != 0f) {
            val offset = (adj.whites / 100f) * 80f
            cm.postConcat(ColorMatrix(floatArrayOf(
                1f, 0f, 0f, 0f, offset,
                0f, 1f, 0f, 0f, offset,
                0f, 0f, 1f, 0f, offset,
                0f, 0f, 0f, 1f, 0f
            )))
        }

        // 8) Blacks
        if (adj.blacks != 0f) {
            val offset = (adj.blacks / 100f) * 60f
            cm.postConcat(ColorMatrix(floatArrayOf(
                1f, 0f, 0f, 0f, offset,
                0f, 1f, 0f, 0f, offset,
                0f, 0f, 1f, 0f, offset,
                0f, 0f, 0f, 1f, 0f
            )))
        }

        // 9) Shadows
        if (adj.shadows != 0f) {
            val offset = (adj.shadows / 100f) * 70f
            cm.postConcat(ColorMatrix(floatArrayOf(
                1f, 0f, 0f, 0f, offset,
                0f, 1f, 0f, 0f, offset,
                0f, 0f, 1f, 0f, offset,
                0f, 0f, 0f, 1f, 0f
            )))
        }

        // 10) Highlights
        if (adj.highlights != 0f) {
            val offset = (adj.highlights / 100f) * 50f
            cm.postConcat(ColorMatrix(floatArrayOf(
                1f, 0f, 0f, 0f, offset,
                0f, 1f, 0f, 0f, offset,
                0f, 0f, 1f, 0f, offset,
                0f, 0f, 0f, 1f, 0f
            )))
        }

        // 11) Clarity — approximate as a small contrast boost
        if (adj.clarity != 0f) {
            val scale = 1f + (adj.clarity / 100f) * 0.25f
            val translate = (1f - scale) * 128f
            cm.postConcat(ColorMatrix(floatArrayOf(
                scale, 0f, 0f, 0f, translate,
                0f, scale, 0f, 0f, translate,
                0f, 0f, scale, 0f, translate,
                0f, 0f, 0f, 1f, 0f
            )))
        }

        return cm
    }

    fun hasRealTimeAdjustments(adj: AdjustmentData): Boolean {
        return adj.brightness != 0f || adj.contrast != 0f || adj.exposure != 0f ||
                adj.whites != 0f || adj.blacks != 0f || adj.shadows != 0f ||
                adj.highlights != 0f || adj.saturation != 0f || adj.vibrance != 0f ||
                adj.clarity != 0f || adj.temperature != 0f || adj.tint != 0f
    }
}