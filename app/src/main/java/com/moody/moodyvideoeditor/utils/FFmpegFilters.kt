package com.moody.moodyvideoeditor.utils

import com.moody.moodyvideoeditor.data.AdjustmentData

/**
 * 24 adjustment sliders ko FFmpeg filter string mein convert karta hai.
 * Yeh export ke time apply hoti hai.
 */
object FFmpegFilters {

    fun build(adj: AdjustmentData): String {
        if (adj.isDefault) return ""
        val filters = mutableListOf<String>()

        // ─── LIGHT (7) ──────────────────────────────────────
        val eqParts = mutableListOf<String>()

        // Brightness (-1..1)
        if (adj.brightness != 0f) {
            eqParts.add("brightness=${(adj.brightness / 100f).coerceIn(-1f, 1f)}")
        }
        // Contrast (0..2, default 1)
        if (adj.contrast != 0f) {
            eqParts.add("contrast=${(1f + adj.contrast / 100f).coerceIn(0f, 2f)}")
        }
        // Saturation (0..3, default 1)
        if (adj.saturation != 0f) {
            eqParts.add("saturation=${(1f + adj.saturation / 100f).coerceIn(0f, 3f)}")
        }
        // Gamma for exposure
        if (adj.exposure != 0f) {
            val gamma = Math.pow(2.0, -adj.exposure / 100.0).toFloat()
            eqParts.add("gamma=${gamma.coerceIn(0.3f, 3f)}")
        }
        if (eqParts.isNotEmpty()) {
            filters.add("eq=${eqParts.joinToString(":")}")
        }

        // Shadows & Highlights (via curves)
        if (adj.shadows != 0f || adj.highlights != 0f) {
            val sAmt = adj.shadows / 100f
            val hAmt = adj.highlights / 100f
            val s0 = (0.15f * sAmt).coerceIn(-0.3f, 0.3f)
            val h0 = (0.85f - 0.15f * hAmt).coerceIn(0.5f, 1.0f)
            filters.add("curves=all='0/0 0.15/${0.15f + s0} 0.85/$h0 1/1'")
        }

        // Whites & Blacks (via levels)
        if (adj.whites != 0f || adj.blacks != 0f) {
            val blackPoint = (0f + adj.blacks / 500f).coerceIn(0f, 0.2f)
            val whitePoint = (1f + adj.whites / 500f).coerceIn(0.8f, 1f)
            filters.add("colorlevels=rimin=$blackPoint:gimin=$blackPoint:bimin=$blackPoint:rimax=$whitePoint:gimax=$whitePoint:bimax=$whitePoint")
        }

        // ─── COLOR (3) ───────────────────────────────────────
        // Vibrance (boost low-sat colors)
        if (adj.vibrance != 0f) {
            val sat = (1f + adj.vibrance / 100f).coerceIn(0f, 2f)
            filters.add("vibrance=intensity=${(adj.vibrance / 100f).coerceIn(-2f, 2f)}")
        }
        // Clarity (local contrast via unsharp)
        if (adj.clarity != 0f) {
            val amount = (adj.clarity / 100f).coerceIn(-2f, 2f)
            filters.add("unsharp=5:5:${amount}")
        }

        // ─── TEMPERATURE (2) ─────────────────────────────────
        if (adj.temperature != 0f || adj.tint != 0f) {
            val r = 1f + (adj.temperature / 100f) * 0.3f
            val g = 1f - (adj.tint / 100f) * 0.3f
            val b = 1f - (adj.temperature / 100f) * 0.3f
            filters.add("colorbalance=rs=${(r - 1f).coerceIn(-0.5f, 0.5f)}:gs=${(g - 1f).coerceIn(-0.5f, 0.5f)}:bs=${(b - 1f).coerceIn(-0.5f, 0.5f)}")
        }

        // ─── DETAILS (3) ─────────────────────────────────────
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

        // ─── COLOR CHANNELS (9) ──────────────────────────────
        // Yeh hue-based adjustments hain
        val channelFilters = buildColorChannels(adj)
        if (channelFilters.isNotBlank()) {
            filters.add(channelFilters)
        }

        return filters.joinToString(",")
    }

    /**
     * Color channels ke liye hue-based filtering.
     * FFmpeg mein direct "reds" filter nahi hai, isliye selective color correction
     * ke liye hue + saturation manipulation use karte hain.
     */
    private fun buildColorChannels(adj: AdjustmentData): String {
        val hueShift = (
                adj.reds * 0.0f +
                adj.oranges * 0.1f +
                adj.yellows * 0.2f +
                adj.greens * 0.33f +
                adj.cyans * 0.5f +
                adj.blues * 0.66f +
                adj.purples * 0.8f +
                adj.magentas * 0.9f +
                adj.skinTones * 0.05f
        ) / 300f

        val satBoost = (
                adj.reds + adj.oranges + adj.yellows + adj.greens +
                adj.cyans + adj.blues + adj.purples + adj.magentas + adj.skinTones
        ) / 900f

        if (hueShift == 0f && satBoost == 0f) return ""

        val parts = mutableListOf<String>()
        if (hueShift != 0f) {
            parts.add("hue=h=${(hueShift * 30f).coerceIn(-30f, 30f)}")
        }
        if (satBoost != 0f) {
            parts.add("eq=saturation=${(1f + satBoost).coerceIn(0.5f, 2f)}")
        }
        return parts.joinToString(",")
    }
}