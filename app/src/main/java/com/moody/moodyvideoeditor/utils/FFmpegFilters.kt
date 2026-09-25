package com.moody.moodyvideoeditor.utils

import com.moody.moodyvideoeditor.data.AdjustmentData

object FFmpegFilters {

    fun build(adj: AdjustmentData): String {
        if (adj.isDefault) return ""
        val filters = mutableListOf<String>()

        val eqParts = mutableListOf<String>()

        if (adj.brightness != 0f) {
            eqParts.add("brightness=${(adj.brightness / 100f).coerceIn(-1f, 1f)}")
        }
        if (adj.contrast != 0f) {
            eqParts.add("contrast=${(1f + adj.contrast / 100f).coerceIn(0f, 2f)}")
        }
        if (adj.saturation != 0f) {
            eqParts.add("saturation=${(1f + adj.saturation / 100f).coerceIn(0f, 3f)}")
        }
        if (adj.exposure != 0f) {
            val gamma = Math.pow(2.0, -adj.exposure / 100.0).toFloat()
            eqParts.add("gamma=${gamma.coerceIn(0.3f, 3f)}")
        }
        if (eqParts.isNotEmpty()) {
            filters.add("eq=${eqParts.joinToString(":")}")
        }

        if (adj.shadows != 0f || adj.highlights != 0f) {
            val sAmt = adj.shadows / 100f
            val hAmt = adj.highlights / 100f
            val s0 = (0.15f * sAmt).coerceIn(-0.3f, 0.3f)
            val h0 = (0.85f - 0.15f * hAmt).coerceIn(0.5f, 1.0f)
            filters.add("curves=all='0/0 0.15/${0.15f + s0} 0.85/$h0 1/1'")
        }

        if (adj.whites != 0f || adj.blacks != 0f) {
            val blackPoint = (0f + adj.blacks / 500f).coerceIn(0f, 0.2f)
            val whitePoint = (1f + adj.whites / 500f).coerceIn(0.8f, 1f)
            filters.add("colorlevels=rimin=$blackPoint:gimin=$blackPoint:bimin=$blackPoint:rimax=$whitePoint:gimax=$whitePoint:bimax=$whitePoint")
        }

        if (adj.vibrance != 0f) {
            filters.add("vibrance=intensity=${(adj.vibrance / 100f).coerceIn(-2f, 2f)}")
        }

        if (adj.clarity != 0f) {
            val amount = (adj.clarity / 100f).coerceIn(-2f, 2f)
            filters.add("unsharp=5:5:${amount}")
        }

        if (adj.temperature != 0f || adj.tint != 0f) {
            val r = 1f + (adj.temperature / 100f) * 0.3f
            val g = 1f - (adj.tint / 100f) * 0.3f
            val b = 1f - (adj.temperature / 100f) * 0.3f
            filters.add(
                "colorbalance=rs=${
                    (r - 1f).coerceIn(
                        -0.5f,
                        0.5f
                    )
                }:gs=${(g - 1f).coerceIn(-0.5f, 0.5f)}:bs=${(b - 1f).coerceIn(-0.5f, 0.5f)}"
            )
        }

        if (adj.noise != 0f) {
            val strength = (adj.noise / 100f * 50f).coerceIn(0f, 50f)
            filters.add("noise=alls=$strength:allf=t+u")
        }

        if (adj.sharpen != 0f) {
            val amount = (adj.sharpen / 100f).coerceIn(0f, 2f)
            filters.add("unsharp=5:5:${amount}:5:5:0")
        }

        if (adj.vignette != 0f) {
            val angle = (adj.vignette / 100f * Math.PI / 4).toFloat()
            filters.add("vignette=angle=$angle")
        }

        return filters.joinToString(",")
    }
}