package com.moody.moodyvideoeditor.utils

import android.graphics.ColorMatrix
import com.moody.moodyvideoeditor.data.ChromaState

/**
 * Chroma helpers.
 * Preview (API 33+) → full removal via RuntimeShader in ChromaKeyPreviewFilter.
 * Export            → FFmpeg chromakey filter.
 * buildApproxMatrix is only a weak visual hint for API < 33.
 */
object ChromaEngine {

    /** Weak saturation reduction — fallback hint for devices < API 33. */
    fun buildApproxMatrix(state: ChromaState): ColorMatrix {
        val cm = ColorMatrix()
        val intensity = (state.intensity / 100f).coerceIn(0f, 1f)
        if (intensity > 0.1f) {
            val sat = ColorMatrix().apply { setSaturation(1f - intensity * 0.12f) }
            cm.postConcat(sat)
        }
        return cm
    }

    /** Extract RGB from packed ARGB long — also used by edge-glow filters. */
    fun toRgb(colorLong: Long): Triple<Int, Int, Int> {
        val r = ((colorLong shr 16) and 0xFF).toInt()
        val g = ((colorLong shr 8) and 0xFF).toInt()
        val b = (colorLong and 0xFF).toInt()
        return Triple(r, g, b)
    }

    /** FFmpeg chromakey filter string — used in export. */
    fun buildFfmpegFilter(state: ChromaState): String {
        val (r, g, b) = toRgb(state.keyColor)
        val hex = String.format("0x%02X%02X%02X", r, g, b)
        val similarity = (state.similarity / 100f * 0.25f).coerceIn(0.01f, 0.25f)
        val blend = (state.smoothness / 100f * 0.4f).coerceIn(0f, 0.4f)
        val intensity = (state.intensity / 100f).coerceIn(0f, 1f)
        return "chromakey=$hex:$similarity:$blend,lut=a='val*$intensity+255*(1-$intensity)'"
    }

    fun isActive(state: ChromaState?): Boolean = state != null && state.isActive
}