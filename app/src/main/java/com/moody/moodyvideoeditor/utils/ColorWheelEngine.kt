package com.moody.moodyvideoeditor.utils

import com.moody.moodyvideoeditor.data.ColorWheelState
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Mirrors js/workspace/effectRenderer.js applyColorWheel()
 * RGB ↔ HSL helpers + tone-based color grading math.
 */
object ColorWheelEngine {

    // ═══════════════════════════════════════════════════════════
    //  RGB ↔ HSL — mirrors effectRenderer.js
    // ═══════════════════════════════════════════════════════════
    data class Hsl(val h: Float, val s: Float, val l: Float)
    data class Rgb(val r: Float, val g: Float, val b: Float)

    fun rgbToHsl(r: Float, g: Float, b: Float): Hsl {
        val rn = r / 255f
        val gn = g / 255f
        val bn = b / 255f
        val maxC = maxOf(rn, gn, bn)
        val minC = minOf(rn, gn, bn)
        var h = 0f
        var s = 0f
        val l = (maxC + minC) / 2f

        if (maxC != minC) {
            val d = maxC - minC
            s = if (l > 0.5f) d / (2f - maxC - minC) else d / (maxC + minC)
            h = when (maxC) {
                rn -> ((gn - bn) / d + (if (gn < bn) 6f else 0f)) * 60f
                gn -> ((bn - rn) / d + 2f) * 60f
                else -> ((rn - gn) / d + 4f) * 60f
            }
        }
        return Hsl(h, s * 100f, l * 100f)
    }

    fun hslToRgb(h: Float, s: Float, l: Float): Rgb {
        var hh = ((h % 360f) + 360f) % 360f
        val ss = (s.coerceIn(0f, 100f)) / 100f
        val ll = (l.coerceIn(0f, 100f)) / 100f

        val k = { n: Float -> (n + hh / 30f) % 12f }
        val a = ss * minOf(ll, 1f - ll)
        val f = { n: Float ->
            val kn = k(n)
            ll - a * maxOf(-1f, minOf(kn - 3f, minOf(9f - kn, 1f)))
        }
        return Rgb(
            (f(0f) * 255f),
            (f(8f) * 255f),
            (f(4f) * 255f)
        )
    }

    // ═══════════════════════════════════════════════════════════
    //  TONE WEIGHTS — mirrors effectRenderer.js applyColorWheel
    //  Shadows: weight from luminance (higher at dark)
    //  Midtones: peak at 0.5
    //  Highlights: peak at 1.0
    // ═══════════════════════════════════════════════════════════
    private fun shadowWeight(lum: Float): Float = max(0f, 1f - lum * 2f)
    private fun midtoneWeight(lum: Float): Float = max(0f, 1f - abs(lum - 0.5f) * 2f)
    private fun highlightWeight(lum: Float): Float = max(0f, lum * 2f - 1f)

    /**
     * Apply color wheel grading to a single pixel.
     * Returns adjusted RGB.
     */
    fun applyPixel(
        rIn: Float,
        gIn: Float,
        bIn: Float,
        state: ColorWheelState
    ): Triple<Float, Float, Float> {
        var r = rIn
        var g = gIn
        var b = bIn

        // 1) HDR White boost
        val hdr = state.hdrWhite / 100f
        if (hdr > 1f) {
            val boost = (hdr - 1f) * 127f
            r = min(255f, r + boost)
            g = min(255f, g + boost)
            b = min(255f, b + boost)
        }

        // 2) Tone weight contribution
        val lum = (0.299f * r + 0.587f * g + 0.114f * b) / 255f

        var weightSum = 0f
        var targetHueSum = 0f
        var targetSatSum = 0f

        if (state.shadows.isActive) {
            val w = shadowWeight(lum) * (state.shadows.intensity / 100f)
            if (w > 0f) {
                targetHueSum += state.shadows.hue * w
                targetSatSum += state.shadows.saturation * w
                weightSum += w
            }
        }
        if (state.midtones.isActive) {
            val w = midtoneWeight(lum) * (state.midtones.intensity / 100f)
            if (w > 0f) {
                targetHueSum += state.midtones.hue * w
                targetSatSum += state.midtones.saturation * w
                weightSum += w
            }
        }
        if (state.highlights.isActive) {
            val w = highlightWeight(lum) * (state.highlights.intensity / 100f)
            if (w > 0f) {
                targetHueSum += state.highlights.hue * w
                targetSatSum += state.highlights.saturation * w
                weightSum += w
            }
        }

        if (weightSum > 0.001f) {
            val avgHue = ((targetHueSum / weightSum) % 360f + 360f) % 360f
            val avgSat = min(100f, targetSatSum / weightSum)
            val strength = min(1f, weightSum)

            val hsl = rgbToHsl(r, g, b)
            val ph = hsl.h
            val ps = hsl.s
            val pl = hsl.l

            var hDiff = avgHue - ph
            while (hDiff > 180f) hDiff -= 360f
            while (hDiff < -180f) hDiff += 360f
            val newHue = ph + hDiff * strength * 0.85f
            val satMul = 1f + (avgSat / 100f) * strength * 0.9f
            val newSat = min(100f, ps * satMul)

            val rgb = hslToRgb(newHue, newSat, pl)
            r = rgb.r; g = rgb.g; b = rgb.b
        }

        return Triple(
            r.coerceIn(0f, 255f),
            g.coerceIn(0f, 255f),
            b.coerceIn(0f, 255f)
        )
    }

    /**
     * Whether the wheel should be applied at all.
     */
    fun isActive(state: ColorWheelState): Boolean = state.hasAnyChange

    // ═══════════════════════════════════════════════════════════
    //  PUCK POSITION — for the wheel UI
    //  Mirrors JS updatePuck()
    // ═══════════════════════════════════════════════════════════
    /**
     * Convert (hue, sat) → normalized (x, y) in [-1, 1] range.
     * Hue: angle (0..360). Sat: distance (0..1).
     */
    fun puckOffset(hue: Float, saturation: Float): Pair<Float, Float> {
        val angleRad = (hue / 360f) * 2f * Math.PI.toFloat()
        val dist = (saturation / 100f).coerceIn(0f, 1f)
        return Pair(
            cos(angleRad) * dist,
            sin(angleRad) * dist
        )
    }

    /**
     * Convert drag position (x, y) in wheel → (hue, sat).
     * x, y are in range [-1, 1] relative to wheel center.
     */
    fun positionToHueSat(x: Float, y: Float): Pair<Float, Float> {
        val dist = sqrt(x * x + y * y).coerceIn(0f, 1f)
        val sat = dist * 100f
        var angle = Math.toDegrees(kotlin.math.atan2(y.toDouble(), x.toDouble())).toFloat()
        if (angle < 0f) angle += 360f
        return Pair(angle, sat)
    }
}