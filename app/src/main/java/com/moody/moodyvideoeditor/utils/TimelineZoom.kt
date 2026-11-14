package com.moody.moodyvideoeditor.utils

import kotlin.math.exp
import kotlin.math.ln

object TimelineZoom {

    const val SLIDER_MIN = -50f
    const val SLIDER_MAX = 50f
    const val SLIDER_DEFAULT = 0f

    const val MIN_TIMELINE_MS = 40L * 60L * 1000L

    const val MAX_COMPOSE_CONTENT_WIDTH_PX = 240_000f

    const val DEFAULT_DP_PER_SEC = 60f
    private const val MIN_DP_PER_SEC = 4f
    private const val MAX_DP_PER_SEC = 220f

    fun ppsForSlider(slider: Float): Float {
        val value = slider.coerceIn(SLIDER_MIN, SLIDER_MAX)
        val fraction = if (value < SLIDER_DEFAULT) {
            (value - SLIDER_MIN) / (SLIDER_DEFAULT - SLIDER_MIN)
        } else {
            (value - SLIDER_DEFAULT) / (SLIDER_MAX - SLIDER_DEFAULT)
        }
        val start = if (value < SLIDER_DEFAULT) MIN_DP_PER_SEC else DEFAULT_DP_PER_SEC
        val end = if (value < SLIDER_DEFAULT) DEFAULT_DP_PER_SEC else MAX_DP_PER_SEC
        return exp(ln(start.toDouble()) + fraction * (ln(end.toDouble()) - ln(start.toDouble())))
            .toFloat()
    }

    fun formatLabel(slider: Float): String {
        val pps = ppsForSlider(slider)
        return if (slider == SLIDER_DEFAULT) "1s" else "%.0f dp/s".format(pps)
    }

    fun safeContentWidthDp(widthDp: Float, density: Float): Float {
        require(density.isFinite() && density > 0f)
        val maxWidthDp = MAX_COMPOSE_CONTENT_WIDTH_PX / density
        val nonNegativeWidthDp = if (widthDp.isNaN()) 0f else widthDp.coerceAtLeast(0f)
        return nonNegativeWidthDp.coerceAtMost(maxWidthDp)
    }
}