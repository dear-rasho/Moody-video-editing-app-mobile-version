package com.moody.moodyvideoeditor.utils

/**
 * Mirrors js/workspace/timeline.js ruler step logic.
 *
 * Auto-picks step value where labels stay >= MIN_LABEL_GAP_PX apart.
 */
object TimelineRuler {

    /** Label spacing threshold in dp (doc: 56px) */
    const val MIN_LABEL_GAP_DP = 56f

    /** Steps in seconds (doc list) */
    private val STEPS_SEC = listOf(
        0.01f, 0.02f, 0.05f, 0.1f, 0.25f, 0.5f,
        1f, 2f, 5f, 10f, 15f, 30f,
        60f, 120f, 300f, 600f, 900f, 1800f, 3600f
    )

    /**
     * Pick best step so that each step consumes >= minGapDp on screen.
     *
     * @param zoom actual zoom (0.10..3.0)
     * @param dpPerSecond base density (20dp at zoom 1.0)
     * @param minGapDp minimum pixels between labels
     */
    fun pickStepSec(
        zoom: Float,
        dpPerSecond: Float = 20f,
        minGapDp: Float = MIN_LABEL_GAP_DP
    ): Float {
        val effectivePps = dpPerSecond * zoom  // dp per second on screen
        if (effectivePps <= 0f) return STEPS_SEC.last()

        // pick smallest step where step*pps >= minGapDp
        for (s in STEPS_SEC) {
            if (s * effectivePps >= minGapDp) return s
        }
        return STEPS_SEC.last()
    }

    /**
     * Format label based on step size.
     *  - < 1s   → "0.25s"
     *  - < 60s  → "5s"
     *  - >= 60s → "1:30"
     */
    fun formatLabel(timeMs: Long, stepSec: Float): String {
        val sec = timeMs / 1000f
        return when {
            stepSec < 1f -> "%.2fs".format(sec)
            sec < 60f -> "${sec.toInt()}s"
            else -> {
                val total = sec.toInt()
                val m = total / 60
                val s = total % 60
                "%d:%02d".format(m, s)
            }
        }
    }

    /** Generate all marks for a duration + step */
    fun marks(totalMs: Long, stepSec: Float): List<Long> {
        if (stepSec <= 0f) return emptyList()
        val stepMs = (stepSec * 1000f).toLong().coerceAtLeast(1L)
        val out = mutableListOf<Long>()
        var t = 0L
        while (t <= totalMs) {
            out.add(t)
            t += stepMs
            if (out.size > 5000) break   // safety
        }
        return out
    }
}