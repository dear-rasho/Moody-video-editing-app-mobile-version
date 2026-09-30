package com.moody.moodyvideoeditor.utils

import kotlin.math.exp
import kotlin.math.ln

object TimelineZoom {

    const val SLIDER_MIN = 0f
    const val SLIDER_MAX = 100f
    const val SLIDER_DEFAULT = 0f

    const val MIN_TIMELINE_MS = 40L * 60L * 1000L

    private const val MAX_DP_PER_SEC = 100f

    fun ppsForSlider(
        slider: Float,
        totalSec: Float,
        viewportContentWidthDp: Float
    ): Float {
        if (totalSec <= 0f || viewportContentWidthDp <= 0f) return 20f
        val s = slider.coerceIn(SLIDER_MIN, SLIDER_MAX) / 100f

        val fitPps = viewportContentWidthDp / totalSec
        val maxPps = MAX_DP_PER_SEC

        if (maxPps <= fitPps) return fitPps

        val minLog = ln(fitPps.toDouble())
        val maxLog = ln(maxPps.toDouble())
        return exp(minLog + s * (maxLog - minLog)).toFloat()
    }

    fun formatLabel(
        slider: Float,
        totalSec: Float,
        viewportContentWidthDp: Float
    ): String {
        if (totalSec <= 0f || viewportContentWidthDp <= 0f) return "Fit"
        val pps = ppsForSlider(slider, totalSec, viewportContentWidthDp)
        val fitPps = viewportContentWidthDp / totalSec

        return when {
            slider <= 1f -> "Fit"
            pps >= 80f -> "1s"
            else -> "%.0fx".format(pps / fitPps)
        }
    }
}