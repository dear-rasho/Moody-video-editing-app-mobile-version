package com.moody.moodyvideoeditor.utils

import android.graphics.ColorMatrix
import com.moody.moodyvideoeditor.data.ChromaState

/**
 * Mirrors js/workspace/exportRenderer.js applyChromaToData()
 * Preview = simplified via ColorMatrix (approximation).
 * Export = full per-pixel removal via FFmpeg chromakey filter.
 */
object ChromaEngine {

    /** RGB → distance-based approx ColorMatrix (preview only) */
    fun buildApproxMatrix(state: ChromaState): ColorMatrix {
        val cm = ColorMatrix()
        // Approx: reduce saturation of key color via hue rotation
        // (Full removal only in export via FFmpeg)
        val intensity = (state.intensity / 100f).coerceIn(0f, 1f)
        // If intensity = 100, reduce saturation overall slightly
        if (intensity > 0.1f) {
            val sat = ColorMatrix().apply { setSaturation(1f - intensity * 0.15f) }
            cm.postConcat(sat)
        }
        return cm
    }

    /** Extract RGB from packed ARGB long */
    fun toRgb(colorLong: Long): Triple<Int, Int, Int> {
        val r = ((colorLong shr 16) and 0xFF).toInt()
        val g = ((colorLong shr 8) and 0xFF).toInt()
        val b = (colorLong and 0xFF).toInt()
        return Triple(r, g, b)
    }

    /** FFmpeg chromakey filter string — used in export */
    fun buildFfmpegFilter(state: ChromaState): String {
        val (r, g, b) = toRgb(state.keyColor)
        val hex = String.format("0x%02X%02X%02X", r, g, b)
        val similarity = (state.similarity / 100f).coerceIn(0.01f, 1f)
        val blend = (state.smoothness / 100f).coerceIn(0f, 0.99f)
        return "chromakey=$hex:$similarity:$blend"
    }

    fun isActive(state: ChromaState?): Boolean = state != null && state.isActive
}