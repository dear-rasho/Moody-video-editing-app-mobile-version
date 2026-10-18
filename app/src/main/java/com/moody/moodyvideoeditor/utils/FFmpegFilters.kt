package com.moody.moodyvideoeditor.utils

import com.moody.moodyvideoeditor.data.AdjustmentData
import java.util.Locale

object FFmpegFilters {

    fun build(adj: AdjustmentData): String {
        try {
            if (adj.isDefault) return ""
            val filters = mutableListOf<String>()
            val loc = Locale.US

            fun f(v: Float): String = String.format(loc, "%.4f", v)

            // ─── BRIGHTNESS ───
            if (adj.brightness != 0f) {
                val v = (adj.brightness / 100f) * (128f / 255f)
                filters.add("eq=brightness=${f(v.coerceIn(-1f, 1f))}")
            }

            // ─── CONTRAST ───
            if (adj.contrast != 0f) {
                val v = (1f + adj.contrast / 100f * 0.5f).coerceIn(0f, 3f)
                filters.add("eq=contrast=${f(v)}")
            }

            // ─── EXPOSURE ───
            if (adj.exposure != 0f) {
                val gamma = Math.pow(2.0, -adj.exposure / 100.0).toFloat()
                    .coerceIn(0.3f, 3f)
                filters.add("eq=gamma=${f(gamma)}")
            }

            // ─── SATURATION + VIBRANCE ───
            val totalSat = 1f + (adj.saturation / 100f) + (adj.vibrance / 200f)
            if (kotlin.math.abs(totalSat - 1f) > 0.01f) {
                filters.add("eq=saturation=${f(totalSat.coerceIn(0f, 3f))}")
            }

            // ─── SHADOWS / HIGHLIGHTS ───
            if (adj.shadows != 0f || adj.highlights != 0f) {
                val sAmt = adj.shadows / 100f
                val hAmt = adj.highlights / 100f
                val s0 = (0.15f * sAmt).coerceIn(-0.15f, 0.15f)
                val h0 = (0.85f - 0.15f * hAmt).coerceIn(0.7f, 1.0f)
                filters.add(
                    "curves=all='0/0 0.15/${f(0.15f + s0)} 0.85/${f(h0)} 1/1'"
                )
            }

            // ─── WHITES / BLACKS ───
            if (adj.whites != 0f || adj.blacks != 0f) {
                val bp = (0f + adj.blacks / 500f).coerceIn(0f, 0.15f)
                val wp = (1f + adj.whites / 500f).coerceIn(0.85f, 1f)
                filters.add(
                    "colorlevels=" +
                            "rimin=${f(bp)}:gimin=${f(bp)}:bimin=${f(bp)}:" +
                            "rimax=${f(wp)}:gimax=${f(wp)}:bimax=${f(wp)}"
                )
            }

            // ─── CLARITY ───
            if (adj.clarity != 0f) {
                val amount = (adj.clarity / 100f).coerceIn(-1f, 1f)
                filters.add("unsharp=5:5:${f(amount)}:5:5:0")
            }

            // ─── TEMPERATURE / TINT ───
            if (adj.temperature != 0f || adj.tint != 0f) {
                val r = 1f + (adj.temperature / 100f) * 0.3f
                val g = 1f - (adj.tint / 100f) * 0.3f
                val b = 1f - (adj.temperature / 100f) * 0.3f
                filters.add(
                    "colorbalance=" +
                            "rs=${f((r - 1f).coerceIn(-0.5f, 0.5f))}:" +
                            "gs=${f((g - 1f).coerceIn(-0.5f, 0.5f))}:" +
                            "bs=${f((b - 1f).coerceIn(-0.5f, 0.5f))}"
                )
            }

            // ─── NOISE ───
            if (adj.noise != 0f) {
                val strength = (adj.noise / 100f * 50f).coerceIn(0f, 50f)
                filters.add("noise=alls=${strength.toInt()}:allf=t+u")
            }

            // ─── SHARPEN — SAFE (max 2.0) ───
            if (adj.sharpen > 0f) {
                val amount = (adj.sharpen / 50f).coerceIn(0f, 2f)
                filters.add("unsharp=5:5:${f(amount)}:5:5:0")
            }

            // ─── VIGNETTE ───
            if (adj.vignette != 0f) {
                val angle = (adj.vignette / 100f * Math.PI / 4).toFloat()
                    .coerceIn(0f, 1.5f)
                filters.add("vignette=angle=${f(angle)}")
            }

            // ═══════════════════════════════════════════════════════════
            //  🎨 NOTE: COLOR CHANNELS (Reds, Oranges, Greens, etc.)
            //  Yahan nahi hain! Inhe humne FFmpegExecutor.kt mein
            //  "LUT (Look-Up Table)" ke zariye handle kiya hai taki
            //  Preview aur Export ka result 100% exact same aaye.
            //  Aur video par orange color ki overlay na aaye.
            // ═══════════════════════════════════════════════════════════

            return filters.joinToString(",")
        } catch (t: Throwable) {
            android.util.Log.e("FFmpegFilters", "build failed", t)
            return ""
        }
    }
}