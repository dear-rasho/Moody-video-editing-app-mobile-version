package com.moody.moodyvideoeditor.utils

import com.moody.moodyvideoeditor.data.PositionKeyframe
import com.moody.moodyvideoeditor.data.StickerKeyframes
import com.moody.moodyvideoeditor.data.ValueKeyframe

/**
 * Mirrors js/features/stickers.js interpolation + easing logic.
 * Interpolates sticker position/scale/rotation at a given time.
 */
object StickerEngine {

    // ═══════════════════════════════════════════════════════════
    //  EASING — mirrors stickers.js getEasedValue()
    // ═══════════════════════════════════════════════════════════
    fun ease(t: Float, type: String): Float {
        val x = t.coerceIn(0f, 1f)
        return when (type) {
            "linear" -> x
            "easeIn" -> x * x
            "easeOut" -> 1f - (1f - x) * (1f - x)
            "easeInOut" -> if (x < 0.5f) 2f * x * x
            else 1f - Math.pow((-2f * x + 2f).toDouble(), 2.0).toFloat() / 2f

            "easeInCubic" -> x * x * x
            "easeOutCubic" -> 1f - Math.pow((1f - x).toDouble(), 3.0).toFloat()
            "easeInOutCubic" -> if (x < 0.5f) 4f * x * x * x
            else 1f - Math.pow((-2f * x + 2f).toDouble(), 3.0).toFloat() / 2f

            "easeInBack" -> {
                val c1 = 1.70158f
                val c3 = c1 + 1f
                c3 * x * x * x - c1 * x * x
            }

            "easeOutBack" -> {
                val c1 = 1.70158f
                val c3 = c1 + 1f
                1f + c3 * Math.pow((x - 1f).toDouble(), 3.0).toFloat() +
                        c1 * Math.pow((x - 1f).toDouble(), 2.0).toFloat()
            }

            "easeInOutBack" -> {
                val c1 = 1.70158f
                val c2 = c1 * 1.525f
                if (x < 0.5f)
                    (Math.pow((2f * x).toDouble(), 2.0).toFloat() *
                            ((c2 + 1f) * 2f * x - c2)) / 2f
                else
                    (Math.pow((2f * x - 2f).toDouble(), 2.0).toFloat() *
                            ((c2 + 1f) * (x * 2f - 2f) + c2) + 2f) / 2f
            }

            "easeOutBounce" -> {
                val n1 = 7.5625f
                val d1 = 2.75f
                var y = x
                when {
                    y < 1f / d1 -> n1 * y * y
                    y < 2f / d1 -> {
                        y -= 1.5f / d1; n1 * y * y + 0.75f
                    }

                    y < 2.5f / d1 -> {
                        y -= 2.25f / d1; n1 * y * y + 0.9375f
                    }

                    else -> {
                        y -= 2.625f / d1; n1 * y * y + 0.984375f
                    }
                }
            }

            "easeOutElastic" -> {
                val c4 = (2f * Math.PI / 3f).toFloat()
                when (x) {
                    0f -> 0f
                    1f -> 1f
                    else -> Math.pow(2.0, (-10f * x).toDouble()).toFloat() *
                            Math.sin(((x * 10f - 0.75f) * c4).toDouble()).toFloat() + 1f
                }
            }

            else -> x
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  INTERPOLATORS — mirrors stickers.js interpolate*()
    // ═══════════════════════════════════════════════════════════
    private fun sampleValue(kfs: List<ValueKeyframe>, t: Float, ease: String, base: Float): Float {
        if (kfs.isEmpty()) return base
        if (kfs.size == 1 || t <= kfs[0].time) return kfs[0].value
        val last = kfs.last()
        if (t >= last.time) return last.value
        for (i in 0 until kfs.size - 1) {
            val a = kfs[i]
            val b = kfs[i + 1]
            if (t in a.time..b.time) {
                val raw = (t - a.time) / ((b.time - a.time).takeIf { it > 0f } ?: 1f)
                val local = ease(raw, ease)
                return a.value + (b.value - a.value) * local
            }
        }
        return last.value
    }

    private fun samplePosition(
        kfs: List<PositionKeyframe>, t: Float, ease: String, baseX: Float, baseY: Float
    ): Pair<Float, Float> {
        if (kfs.isEmpty()) return Pair(baseX, baseY)
        if (kfs.size == 1 || t <= kfs[0].time) return Pair(kfs[0].x, kfs[0].y)
        val last = kfs.last()
        if (t >= last.time) return Pair(last.x, last.y)
        for (i in 0 until kfs.size - 1) {
            val a = kfs[i]
            val b = kfs[i + 1]
            if (t in a.time..b.time) {
                val raw = (t - a.time) / ((b.time - a.time).takeIf { it > 0f } ?: 1f)
                val local = ease(raw, ease)
                return Pair(
                    a.x + (b.x - a.x) * local,
                    a.y + (b.y - a.y) * local
                )
            }
        }
        return Pair(last.x, last.y)
    }

    /**
     * Mirrors JS: sample sticker state at time.
     * Called per-frame from preview + export.
     */
    data class Sampled(
        val x: Float, val y: Float,
        val scale: Float, val rotation: Float
    )

    fun sample(
        kfs: StickerKeyframes,
        timeSec: Float,
        baseX: Float, baseY: Float, baseScale: Float, baseRot: Float,
        easePos: String, easeScale: String, easeRot: String
    ): Sampled {
        val (x, y) = if (kfs.position.isNotEmpty())
            samplePosition(kfs.position, timeSec, easePos, baseX, baseY)
        else Pair(baseX, baseY)

        val s = if (kfs.scale.isNotEmpty())
            sampleValue(kfs.scale, timeSec, easeScale, baseScale)
        else baseScale

        val r = if (kfs.rotation.isNotEmpty())
            sampleValue(kfs.rotation, timeSec, easeRot, baseRot)
        else baseRot

        return Sampled(x, y, s, r)
    }

    // ═══════════════════════════════════════════════════════════
    //  KEYFRAME MANAGEMENT — mirrors JS add/remove
    // ═══════════════════════════════════════════════════════════
    fun addPositionKf(
        list: List<PositionKeyframe>,
        kf: PositionKeyframe,
        tol: Float = 0.05f
    ): List<PositionKeyframe> {
        val filtered = list.filter { kotlin.math.abs(it.time - kf.time) > tol }
        return (filtered + kf).sortedBy { it.time }
    }

    fun addValueKf(
        list: List<ValueKeyframe>,
        kf: ValueKeyframe,
        tol: Float = 0.05f
    ): List<ValueKeyframe> {
        val filtered = list.filter { kotlin.math.abs(it.time - kf.time) > tol }
        return (filtered + kf).sortedBy { it.time }
    }

    fun removeKfAtTime(list: List<ValueKeyframe>, time: Float): List<ValueKeyframe> =
        list.filter { kotlin.math.abs(it.time - time) > 0.05f }

    fun removePosKfAtTime(list: List<PositionKeyframe>, time: Float): List<PositionKeyframe> =
        list.filter { kotlin.math.abs(it.time - time) > 0.05f }

    fun formatTime(s: Float): String = String.format("%.2fs", s)
}