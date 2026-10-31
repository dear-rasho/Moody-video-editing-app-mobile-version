package com.moody.moodyvideoeditor.utils

import com.moody.moodyvideoeditor.data.AdjustmentData
import java.util.Locale

object FFmpegFilters {

    // Per-pixel adjustments use the ColorMatrixBuilder-based LUT; this handles spatial effects.
    fun build(adj: AdjustmentData): String {
        try {
            if (adj.noise <= 0f && adj.sharpen <= 0f && adj.vignette == 0f) return ""
            val filters = mutableListOf<String>()
            val loc = Locale.US

            fun f(v: Float): String = String.format(loc, "%.4f", v)

            if (adj.noise > 0f) {
                val strength = (adj.noise / 100f * 50f).coerceIn(0f, 50f)
                filters.add("noise=alls=${strength.toInt()}:allf=t+u")
            }

            if (adj.sharpen > 0f) {
                val amount = (adj.sharpen / 50f).coerceIn(0f, 2f)
                filters.add("unsharp=3:3:${f(amount)}:3:3:${f(amount)}")
            }

            if (adj.vignette != 0f) {
                val angle = (adj.vignette / 100f * Math.PI / 4).toFloat()
                    .coerceIn(0f, 1.5f)
                filters.add("vignette=angle=${f(angle)}")
            }

            return filters.joinToString(",")
        } catch (t: Throwable) {
            android.util.Log.e("FFmpegFilters", "build failed", t)
            return ""
        }
    }
}