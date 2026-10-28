package com.moody.moodyvideoeditor.utils

import com.moody.moodyvideoeditor.data.AdjustmentData
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

object AdjustmentPixelEngine {

    fun apply(
        r: Float,
        g: Float,
        b: Float,
        adjustment: AdjustmentData
    ): Triple<Float, Float, Float> {
        return apply(r, g, b, adjustment, ColorMatrixBuilder.build(adjustment))
    }

    fun apply(
        r: Float,
        g: Float,
        b: Float,
        adjustment: AdjustmentData,
        colorMatrix: android.graphics.ColorMatrix
    ): Triple<Float, Float, Float> {
        val matrix = colorMatrix.array
        val adjustedR =
            (matrix[0] * r + matrix[1] * g + matrix[2] * b + matrix[3] * 255f + matrix[4])
                .coerceIn(0f, 255f)
        val adjustedG =
            (matrix[5] * r + matrix[6] * g + matrix[7] * b + matrix[8] * 255f + matrix[9])
                .coerceIn(0f, 255f)
        val adjustedB =
            (matrix[10] * r + matrix[11] * g + matrix[12] * b + matrix[13] * 255f + matrix[14])
                .coerceIn(0f, 255f)

        return applyColorChannels(adjustedR, adjustedG, adjustedB, adjustment)
    }

    fun applyColorChannels(
        rIn: Float,
        gIn: Float,
        bIn: Float,
        adjustment: AdjustmentData
    ): Triple<Float, Float, Float> {
        var r = rIn
        var g = gIn
        var b = bIn

        val maxChannel = max(r, max(g, b))
        val minChannel = min(r, min(g, b))
        val delta = maxChannel - minChannel
        val saturation = if (maxChannel == 0f) 0f else delta / maxChannel
        val hue = when {
            delta == 0f -> 0f
            maxChannel == r -> ((g - b) / delta + if (g < b) 6f else 0f) / 6f
            maxChannel == g -> ((b - r) / delta + 2f) / 6f
            else -> ((r - g) / delta + 4f) / 6f
        }

        fun weight(target: Float, range: Float): Float {
            var difference = abs(hue - target)
            difference = min(difference, 1f - difference)
            return (1f - smoothstep(range * 0.25f, range, difference)) *
                    smoothstep(0.02f, 0.15f, saturation)
        }

        fun amount(value: Float, target: Float, range: Float): Float {
            val normalized = (value / 100f).coerceIn(-5f, 5f)
            val hueWeight = weight(target, range)
            return if (abs(normalized) > 0.001f && hueWeight > 0.001f) {
                normalized * hueWeight
            } else 0f
        }

        val reds = amount(adjustment.reds, 0f, 0.083f)
        if (reds != 0f) {
            if (reds > 0f) {
                r += (255f - r) * reds * 0.9f
                g *= 1f - reds * 0.45f
                b *= 1f - reds * 0.45f
            } else {
                val average = (r + g + b) / 3f
                r += (average - r) * -reds * 0.9f
                g += (average - g) * -reds * 0.5f
                b += (average - b) * -reds * 0.5f
            }
        }

        val oranges = amount(adjustment.oranges, 0.083f, 0.070f)
        if (oranges != 0f) {
            if (oranges > 0f) {
                r += (255f - r) * oranges * 0.7f
                g += (180f - g) * oranges * 0.5f
                b *= 1f - oranges * 0.5f
            } else {
                val average = (r + g + b) / 3f
                r += (average - r) * -oranges * 0.7f
                g += (average - g) * -oranges * 0.7f
            }
        }

        val yellows = amount(adjustment.yellows, 0.167f, 0.083f)
        if (yellows != 0f) {
            if (yellows > 0f) {
                r += (255f - r) * yellows * 0.75f
                g += (255f - g) * yellows * 0.75f
                b *= 1f - yellows * 0.6f
            } else {
                val average = (r + g + b) / 3f
                r += (average - r) * -yellows * 0.7f
                g += (average - g) * -yellows * 0.7f
            }
        }

        val greens = amount(adjustment.greens, 0.333f, 0.125f)
        if (greens != 0f) {
            if (greens > 0f) {
                g += (255f - g) * greens * 0.9f
                r *= 1f - greens * 0.45f
                b *= 1f - greens * 0.45f
            } else {
                val average = (r + g + b) / 3f
                g += (average - g) * -greens * 0.9f
                r += (average - r) * -greens * 0.5f
                b += (average - b) * -greens * 0.5f
            }
        }

        val cyans = amount(adjustment.cyans, 0.5f, 0.083f)
        if (cyans != 0f) {
            if (cyans > 0f) {
                g += (255f - g) * cyans * 0.7f
                b += (255f - b) * cyans * 0.7f
                r *= 1f - cyans * 0.6f
            } else {
                val average = (r + g + b) / 3f
                g += (average - g) * -cyans * 0.7f
                b += (average - b) * -cyans * 0.7f
            }
        }

        val blues = amount(adjustment.blues, 0.667f, 0.083f)
        if (blues != 0f) {
            if (blues > 0f) {
                b += (255f - b) * blues * 0.9f
                r *= 1f - blues * 0.45f
                g *= 1f - blues * 0.45f
            } else {
                val average = (r + g + b) / 3f
                b += (average - b) * -blues * 0.9f
                r += (average - r) * -blues * 0.5f
                g += (average - g) * -blues * 0.5f
            }
        }

        val purples = amount(adjustment.purples, 0.778f, 0.070f)
        if (purples != 0f) {
            if (purples > 0f) {
                r += (220f - r) * purples * 0.7f
                b += (255f - b) * purples * 0.7f
                g *= 1f - purples * 0.6f
            } else {
                val average = (r + g + b) / 3f
                r += (average - r) * -purples * 0.7f
                b += (average - b) * -purples * 0.7f
            }
        }

        val magentas = amount(adjustment.magentas, 0.833f, 0.070f)
        if (adjustment.magentas != 0f && magentas != 0f) {
            if (magentas > 0f) {
                r += (255f - r) * magentas * 0.75f
                b += (255f - b) * magentas * 0.75f
                g *= 1f - magentas * 0.6f
            } else {
                val average = (r + g + b) / 3f
                r += (average - r) * -magentas * 0.7f
                b += (average - b) * -magentas * 0.7f
            }
        }

        val skinTones = (adjustment.skinTones / 100f).coerceIn(-5f, 5f)
        if (skinTones != 0f) {
            val skinWeight = weight(0.069f, 0.055f)
            r = (r + skinTones * skinWeight * 30f).coerceIn(0f, 255f)
            g = (g + skinTones * skinWeight * 15f).coerceIn(0f, 255f)
        }

        return Triple(
            r.coerceIn(0f, 255f),
            g.coerceIn(0f, 255f),
            b.coerceIn(0f, 255f)
        )
    }

    private fun smoothstep(edge0: Float, edge1: Float, value: Float): Float {
        val t = ((value - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }
}
