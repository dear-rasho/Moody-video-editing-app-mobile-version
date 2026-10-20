package com.moody.moodyvideoeditor.utils

import com.moody.moodyvideoeditor.data.ColorWheelState
import com.moody.moodyvideoeditor.data.ToneValue
import kotlin.math.abs
import kotlin.math.max

object ColorWheelEngine {

    fun applyPixel(rIn: Float, gIn: Float, bIn: Float, state: ColorWheelState): Triple<Float, Float, Float> {
        var r = rIn
        var g = gIn
        var b = bIn

        val hdr = state.hdrWhite / 100f
        if (hdr != 1f) {
            val inputLum = (0.299f * r + 0.587f * g + 0.114f * b) / 255f
            val whiteWeight = ((inputLum - 0.55f) / 0.45f).coerceIn(0f, 1f)
            val whiteShift = (hdr - 1f) * 127f * whiteWeight
            r = (r + whiteShift).coerceIn(0f, 255f)
            g = (g + whiteShift).coerceIn(0f, 255f)
            b = (b + whiteShift).coerceIn(0f, 255f)
        }

        val lum = (0.299f * r + 0.587f * g + 0.114f * b) / 255f
        val sW = max(0f, 1f - lum * 2f)
        val mW = max(0f, 1f - abs(lum - 0.5f) * 2f)
        val hW = max(0f, lum * 2f - 1f)

        val (sR, sG, sB) = getRGBShift(state.shadows)
        val (mR, mG, mB) = getRGBShift(state.midtones)
        val (hR, hG, hB) = getRGBShift(state.highlights)

        r = (r + (sR * sW + mR * mW + hR * hW) * 255f).coerceIn(0f, 255f)
        g = (g + (sG * sW + mG * mW + hG * hW) * 255f).coerceIn(0f, 255f)
        b = (b + (sB * sW + mB * mW + hB * hW) * 255f).coerceIn(0f, 255f)

        return Triple(r, g, b)
    }

    fun getRGBShift(tone: ToneValue): Triple<Float, Float, Float> {
        if (!tone.isActive) return Triple(0f, 0f, 0f)
        val intensity = (tone.intensity / 100f).coerceIn(0f, 1f)
        if (intensity <= 0.01f) return Triple(0f, 0f, 0f)

        val rad = Math.toRadians(tone.hue.toDouble())
        val sat = (tone.saturation / 100f).coerceIn(0f, 1f)

        val r = (kotlin.math.cos(rad) * 0.5f + 0.5f).toFloat()
        val g = (kotlin.math.cos(rad - 2.094f) * 0.5f + 0.5f).toFloat()
        val b = (kotlin.math.cos(rad + 2.094f) * 0.5f + 0.5f).toFloat()

        val rs = ((r - 0.5f) * 2f * sat * intensity * 1.2f).coerceIn(-1f, 1f)
        val gs = ((g - 0.5f) * 2f * sat * intensity * 1.2f).coerceIn(-1f, 1f)
        val bs = ((b - 0.5f) * 2f * sat * intensity * 1.2f).coerceIn(-1f, 1f)

        return Triple(rs, gs, bs)
    }

    fun isActive(state: ColorWheelState): Boolean = state.hasAnyChange

    fun puckOffset(hue: Float, saturation: Float): Pair<Float, Float> {
        val angleRad = (hue / 360f) * 2f * Math.PI.toFloat()
        val dist = (saturation / 100f).coerceIn(0f, 1f)
        return Pair(
            kotlin.math.cos(angleRad) * dist,
            kotlin.math.sin(angleRad) * dist
        )
    }
    /**
     * Approximate ColorMatrix fallback for API < 33 (RuntimeShader unavailable).
     * ⚠️ This is an APPROXIMATION — exact match requires API 33+ with shader.
     */
    fun buildColorMatrix(state: ColorWheelState): android.graphics.ColorMatrix? {
        if (!state.hasAnyChange) return null
        val cm = android.graphics.ColorMatrix()

        val (sR, sG, sB) = getRGBShift(state.shadows)
        val (mR, mG, mB) = getRGBShift(state.midtones)
        val (hR, hG, hB) = getRGBShift(state.highlights)

        // Weighted average of all tone shifts
        val avgR = (sR + mR + hR) / 3f
        val avgG = (sG + mG + hG) / 3f
        val avgB = (sB + mB + hB) / 3f

        cm.postConcat(
            android.graphics.ColorMatrix(
                floatArrayOf(
                    1f, 0f, 0f, 0f, avgR * 255f,
                    0f, 1f, 0f, 0f, avgG * 255f,
                    0f, 0f, 1f, 0f, avgB * 255f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        )

        // HDR approximation (highlight-weighted)
        val hdr = state.hdrWhite / 100f
        if (hdr != 1f) {
            val offset = (hdr - 1f) * 127f * 0.45f
            cm.postConcat(
                android.graphics.ColorMatrix(
                    floatArrayOf(
                        1f, 0f, 0f, 0f, offset,
                        0f, 1f, 0f, 0f, offset,
                        0f, 0f, 1f, 0f, offset,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            )
        }

        return cm
    }
    fun positionToHueSat(x: Float, y: Float): Pair<Float, Float> {
        val dist = kotlin.math.sqrt(x * x + y * y).coerceIn(0f, 1f)
        val sat = dist * 100f
        var angle = Math.toDegrees(kotlin.math.atan2(y.toDouble(), x.toDouble())).toFloat()
        if (angle < 0f) angle += 360f
        return Pair(angle, sat)
    }
}