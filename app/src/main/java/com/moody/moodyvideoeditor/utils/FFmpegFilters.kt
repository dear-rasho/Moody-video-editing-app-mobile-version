package com.moody.moodyvideoeditor.utils

import com.moody.moodyvideoeditor.data.AdjustmentData

object FFmpegFilters {

    fun build(adj: AdjustmentData): String {
        if (adj.isDefault) return ""
        val filters = mutableListOf<String>()


        //  BRIGHTNESS — MULTIPLICATIVE (matches ColorMatrix)
        //  Preview: multiplier = 1 + brightness/100
        //  AdjustmentData.brightness range: -100..100, 0 = no change

        if (adj.brightness != 0f) {
            // Preview formula: offset = (brightness/100) * 128 (additive in 0-255)
            // For FFmpeg we use colorlevels to simulate
            // Equivalent multiplier approach: brightness/100 * 128 gives delta
            // FFmpeg eq brightness range is -1..1 which multiplies by 255
            // So eq_brightness = (brightness/100) * 128 / 255
            val eqBrightness = (adj.brightness / 100f) * (128f / 255f)
            filters.add("eq=brightness=${eqBrightness.coerceIn(-1f, 1f)}")
        }


        //  CONTRAST — MATCH ColorMatrix
        //  Preview: scale = 1 + contrast/100 * 0.5

        if (adj.contrast != 0f) {
            val scale = (1f + adj.contrast / 100f * 0.5f).coerceIn(0f, 3f)
            filters.add("eq=contrast=$scale")
        }


        //  EXPOSURE — gamma

        if (adj.exposure != 0f) {
            val gamma = Math.pow(2.0, -adj.exposure / 100.0).toFloat()
            filters.add("eq=gamma=${gamma.coerceIn(0.3f, 3f)}")
        }


        //  SATURATION + VIBRANCE — matches ColorMatrix
        //  Preview: totalSat = 1 + saturation/100 + vibrance/200

        val totalSat = 1f + (adj.saturation / 100f) + (adj.vibrance / 200f)
        if (kotlin.math.abs(totalSat - 1f) > 0.01f) {
            filters.add("eq=saturation=${totalSat.coerceIn(0f, 3f)}")
        }


        //  SHADOWS / HIGHLIGHTS — curves (matches preview approximation)

        if (adj.shadows != 0f || adj.highlights != 0f) {
            val sAmt = adj.shadows / 100f
            val hAmt = adj.highlights / 100f
            val s0 = (0.15f * sAmt).coerceIn(-0.15f, 0.15f)
            val h0 = (0.85f - 0.15f * hAmt).coerceIn(0.7f, 1.0f)
            filters.add(
                "curves=all='0/0 0.15/${0.15f + s0} 0.85/$h0 1/1'"
            )
        }


        //  WHITES / BLACKS

        if (adj.whites != 0f || adj.blacks != 0f) {
            val blackPoint = (0f + adj.blacks / 500f).coerceIn(0f, 0.15f)
            val whitePoint = (1f + adj.whites / 500f).coerceIn(0.85f, 1f)
            filters.add(
                "colorlevels=" +
                        "rimin=$blackPoint:gimin=$blackPoint:bimin=$blackPoint:" +
                        "rimax=$whitePoint:gimax=$whitePoint:bimax=$whitePoint"
            )
        }


        //  CLARITY — unsharp

        if (adj.clarity != 0f) {
            val amount = (adj.clarity / 100f).coerceIn(-1f, 1f)
            filters.add("unsharp=5:5:$amount:5:5:0")
        }


        //  TEMPERATURE / TINT — colorbalance (matches preview)

        if (adj.temperature != 0f || adj.tint != 0f) {
            val r = 1f + (adj.temperature / 100f) * 0.3f
            val g = 1f - (adj.tint / 100f) * 0.3f
            val b = 1f - (adj.temperature / 100f) * 0.3f
            filters.add(
                "colorbalance=" +
                        "rs=${(r - 1f).coerceIn(-0.5f, 0.5f)}:" +
                        "gs=${(g - 1f).coerceIn(-0.5f, 0.5f)}:" +
                        "bs=${(b - 1f).coerceIn(-0.5f, 0.5f)}"
            )
        }


        //  NOISE / SHARPEN / VIGNETTE

        if (adj.noise != 0f) {
            val strength = (adj.noise / 100f * 50f).coerceIn(0f, 50f)
            filters.add("noise=alls=$strength:allf=t+u")
        }

        if (adj.sharpen != 0f) {
            val amount = (adj.sharpen / 100f).coerceIn(0f, 2f)
            filters.add("unsharp=5:5:$amount:5:5:0")
        }

        if (adj.vignette != 0f) {
            val angle = (adj.vignette / 100f * Math.PI / 4).toFloat()
            filters.add("vignette=angle=$angle")
        }

        return filters.joinToString(",")
    }
}