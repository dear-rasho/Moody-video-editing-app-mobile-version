package com.moody.moodyvideoeditor.utils

import com.moody.moodyvideoeditor.data.RatioState

/**
 * Mirrors js/workspace/ratioControl.js
 */
object RatioEngine {

    fun applyRatio(state: RatioState, availW: Float, availH: Float): Pair<Float, Float> {
        if (availW <= 0f || availH <= 0f) return Pair(availW, availH)
        val targetAR = state.aspect
        val availAR = availW / availH
        return if (targetAR > availAR) {
            Pair(availW, availW / targetAR)
        } else {
            Pair(availH * targetAR, availH)
        }
    }

    /** FFmpeg crop filter to enforce ratio in export */
    fun buildFfmpegFilter(state: RatioState): String {
        val ar = state.aspect
        return "crop=ih*$ar:ih"
    }
}