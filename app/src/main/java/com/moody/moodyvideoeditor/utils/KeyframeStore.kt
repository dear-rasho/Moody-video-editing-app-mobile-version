package com.moody.moodyvideoeditor.utils

import com.moody.moodyvideoeditor.data.Keyframe
import com.moody.moodyvideoeditor.data.KeyframeLibrary
import com.moody.moodyvideoeditor.data.KeyframeMap
import kotlin.math.abs
import kotlin.math.pow

/**
 * Mirrors js/workspace/keyframeStore.js
 * - Keyframe CRUD
 * - Sampling (interpolation)
 * - 28 easing functions
 * - Auto-keyframe behavior
 */
object KeyframeStore {

    private const val DEFAULT_EASE = "quadInOut"
    private const val TOLERANCE = 0.05f
    private const val STRICT_TOLERANCE = 0.02f

    // ═══════════════════════════════════════════════════════════
    //  READ
    // ═══════════════════════════════════════════════════════════
    fun getKeyframes(map: KeyframeMap, prop: String): List<Keyframe> =
        map[prop] ?: emptyList()

    fun hasAnyKeyframes(map: KeyframeMap): Boolean =
        map.values.any { it.isNotEmpty() }

    fun findIndex(list: List<Keyframe>, time: Float, tolerance: Float = TOLERANCE): Int {
        return list.indexOfFirst { abs(it.time - time) <= tolerance }
    }

    fun hasKeyframeAt(map: KeyframeMap, prop: String, time: Float): Boolean =
        findIndex(getKeyframes(map, prop), time) >= 0

    fun hasAnyKeyframeAt(map: KeyframeMap, time: Float): Boolean =
        KeyframeLibrary.ANIMATABLE_PROPS.any { hasKeyframeAt(map, it, time) }

    fun getPropsWithKeyframeAt(map: KeyframeMap, time: Float): List<String> =
        KeyframeLibrary.ANIMATABLE_PROPS.filter { hasKeyframeAt(map, it, time) }

    // ═══════════════════════════════════════════════════════════
    //  WRITE
    // ═══════════════════════════════════════════════════════════
    fun setKeyframe(
        map: KeyframeMap,
        prop: String,
        time: Float,
        value: Float,
        ease: String? = null
    ): KeyframeMap {
        val existing = getKeyframes(map, prop).toMutableList()
        val idx = findIndex(existing, time)
        if (idx >= 0) {
            existing[idx] = existing[idx].copy(
                value = value,
                ease = ease ?: existing[idx].ease
            )
        } else {
            existing.add(Keyframe(time, value, ease ?: DEFAULT_EASE))
            existing.sortBy { it.time }
        }
        return map + (prop to existing)
    }

    fun removeKeyframe(
        map: KeyframeMap,
        prop: String,
        time: Float,
        tolerance: Float = TOLERANCE
    ): KeyframeMap {
        val list = getKeyframes(map, prop).toMutableList()
        val idx = findIndex(list, time, tolerance)
        if (idx < 0) return map
        list.removeAt(idx)
        return map + (prop to list)
    }

    fun removeAllKeyframesAtTime(
        map: KeyframeMap,
        time: Float,
        tolerance: Float = TOLERANCE
    ): KeyframeMap {
        var result = map
        KeyframeLibrary.ANIMATABLE_PROPS.forEach { prop ->
            result = removeKeyframe(result, prop, time, tolerance)
        }
        return result
    }

    fun setAllEasesAtTime(
        map: KeyframeMap,
        time: Float,
        ease: String,
        tolerance: Float = TOLERANCE
    ): KeyframeMap {
        var result = map
        KeyframeLibrary.ANIMATABLE_PROPS.forEach { prop ->
            val list = getKeyframes(result, prop).toMutableList()
            val idx = findIndex(list, time, tolerance)
            if (idx >= 0) {
                list[idx] = list[idx].copy(ease = ease)
                result = result + (prop to list)
            }
        }
        return result
    }

    fun getEaseAtTime(map: KeyframeMap, time: Float, tolerance: Float = TOLERANCE): String {
        KeyframeLibrary.ANIMATABLE_PROPS.forEach { prop ->
            val list = getKeyframes(map, prop)
            val idx = findIndex(list, time, tolerance)
            if (idx >= 0) return list[idx].ease
        }
        return DEFAULT_EASE
    }

    fun clearAll(map: KeyframeMap): KeyframeMap = emptyMap()

    // ═══════════════════════════════════════════════════════════
    //  AUTO-KEYFRAME — mirrors keyframeStore.js autoKeyframeIfActive()
    // ═══════════════════════════════════════════════════════════
    fun autoKeyframeIfActive(
        map: KeyframeMap,
        prop: String,
        time: Float,
        value: Float
    ): KeyframeMap {
        if (!hasAnyKeyframes(map)) return map

        val list = getKeyframes(map, prop).toMutableList()
        val idx = findIndex(list, time, STRICT_TOLERANCE)
        if (idx >= 0) {
            list[idx] = list[idx].copy(value = value)
        } else {
            list.add(Keyframe(time, value, DEFAULT_EASE))
            list.sortBy { it.time }
        }
        return map + (prop to list)
    }

    // ═══════════════════════════════════════════════════════════
    //  SAMPLE
    // ═══════════════════════════════════════════════════════════
    fun sample(map: KeyframeMap, prop: String, time: Float, baseValue: Float): Float {
        val list = getKeyframes(map, prop)
        if (list.isEmpty()) return baseValue
        if (list.size == 1) return list[0].value

        val first = list.first()
        val last = list.last()
        if (time <= first.time) return first.value
        if (time >= last.time) return last.value

        for (i in 0 until list.size - 1) {
            val a = list[i]
            val b = list[i + 1]
            if (time >= a.time && time <= b.time) {
                val span = (b.time - a.time).takeIf { abs(it) > 1e-6f } ?: 1f
                val raw = (time - a.time) / span
                val eased = easeFn(raw, a.ease)
                return a.value + (b.value - a.value) * eased
            }
        }
        return last.value
    }

    fun sampleAll(
        map: KeyframeMap,
        time: Float,
        base: TransformValues
    ): TransformValues {
        if (!hasAnyKeyframes(map)) return base
        return TransformValues(
            x = sample(map, "x", time, base.x),
            y = sample(map, "y", time, base.y),
            scale = sample(map, "scale", time, base.scale),
            rotation = sample(map, "rotation", time, base.rotation),
            anchorX = sample(map, "anchorX", time, base.anchorX),
            anchorY = sample(map, "anchorY", time, base.anchorY),
            cropL = sample(map, "cropL", time, base.cropL),
            cropR = sample(map, "cropR", time, base.cropR),
            cropT = sample(map, "cropT", time, base.cropT),
            cropB = sample(map, "cropB", time, base.cropB)
        )
    }

    // ═══════════════════════════════════════════════════════════
    //  EASING — mirrors keyframeStore.js easeFn()
    // ═══════════════════════════════════════════════════════════
    fun easeFn(tRaw: Float, type: String): Float {
        val t = tRaw.coerceIn(0f, 1f)
        return when (type) {
            "linear" -> t
            "easeIn", "quadIn" -> t * t
            "easeOut", "quadOut" -> 1f - (1f - t) * (1f - t)
            "easeInOut", "quadInOut" ->
                if (t < 0.5f) 2f * t * t
                else 1f - (-2f * t + 2f).pow(2) / 2f

            "sineIn" -> 1f - kotlin.math.cos((t * Math.PI / 2f).toFloat())
            "sineOut" -> kotlin.math.sin((t * Math.PI / 2f).toFloat())
            "sineInOut" -> (-(kotlin.math.cos(Math.PI * t) - 1) / 2).toFloat()

            "cubicIn" -> t * t * t
            "cubicOut" -> 1f - (1f - t).pow(3)
            "cubicInOut" ->
                if (t < 0.5f) 4f * t * t * t
                else 1f - (-2f * t + 2f).pow(3) / 2f

            "quartIn" -> t * t * t * t
            "quartOut" -> 1f - (1f - t).pow(4)
            "quartInOut" ->
                if (t < 0.5f) 8f * t * t * t * t
                else 1f - (-2f * t + 2f).pow(4) / 2f

            "quintIn" -> t.pow(5)
            "quintOut" -> 1f - (1f - t).pow(5)
            "quintInOut" ->
                if (t < 0.5f) 16f * t.pow(5)
                else 1f - (-2f * t + 2f).pow(5) / 2f

            // 🆕 Circ family
            "circIn" -> 1f - kotlin.math.sqrt(1f - t * t)
            "circOut" -> kotlin.math.sqrt(1f - (t - 1f) * (t - 1f))
            "circInOut" -> if (t < 0.5f) {
                (1f - kotlin.math.sqrt(1f - 4f * t * t)) / 2f
            } else {
                (kotlin.math.sqrt(1f - (-2f * t + 2f) * (-2f * t + 2f)) + 1f) / 2f
            }

            "expoIn" -> if (t == 0f) 0f else 2f.pow(10f * t - 10f)
            "expoOut" -> if (t == 1f) 1f else 1f - 2f.pow(-10f * t)
            "expoInOut" -> when {
                t == 0f -> 0f
                t == 1f -> 1f
                t < 0.5f -> 2f.pow(20f * t - 10f) / 2f
                else -> (2f - 2f.pow(-20f * t + 10f)) / 2f
            }

            "backIn" -> {
                val c1 = 1.70158f
                val c3 = c1 + 1f
                c3 * t * t * t - c1 * t * t
            }

            "backOut" -> {
                val c1 = 1.70158f
                val c3 = c1 + 1f
                1f + c3 * (t - 1f).pow(3) + c1 * (t - 1f).pow(2)
            }

            "backInOut" -> {
                val c1 = 1.70158f
                val c2 = c1 * 1.525f
                if (t < 0.5f) {
                    (2f * t).pow(2) * ((c2 + 1f) * 2f * t - c2) / 2f
                } else {
                    ((2f * t - 2f).pow(2) * ((c2 + 1f) * (t * 2f - 2f) + c2) + 2f) / 2f
                }
            }

            "elasticIn" -> {
                if (t == 0f) 0f
                else if (t == 1f) 1f
                else {
                    val c4 = (2f * Math.PI / 3f).toFloat()
                    -(2f.pow(10f * t - 10f) * kotlin.math.sin((t * 10f - 10.75f) * c4))
                }
            }

            "elasticOut" -> {
                if (t == 0f) 0f
                else if (t == 1f) 1f
                else {
                    val c4 = (2f * Math.PI / 3f).toFloat()
                    2f.pow(-10f * t) * kotlin.math.sin((t * 10f - 0.75f) * c4) + 1f
                }
            }

            "bounceOut" -> {
                val n1 = 7.5625f
                val d1 = 2.75f
                var x = t
                when {
                    x < 1f / d1 -> n1 * x * x
                    x < 2f / d1 -> {
                        x -= 1.5f / d1; n1 * x * x + 0.75f
                    }

                    x < 2.5f / d1 -> {
                        x -= 2.25f / d1; n1 * x * x + 0.9375f
                    }

                    else -> {
                        x -= 2.625f / d1; n1 * x * x + 0.984375f
                    }
                }
            }

            else -> t
        }
    }
}