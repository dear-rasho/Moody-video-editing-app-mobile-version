package com.moody.moodyvideoeditor.utils

import com.moody.moodyvideoeditor.data.KeyframeMap
import com.moody.moodyvideoeditor.data.SpotlightState

object SpotlightAnimation {
    val properties = listOf(
        "spotlight.sourceX",
        "spotlight.sourceY",
        "spotlight.targetX",
        "spotlight.targetY",
        "spotlight.rotation",
        "spotlight.intensity",
        "spotlight.radius",
        "spotlight.softness",
        "spotlight.colorR",
        "spotlight.colorG",
        "spotlight.colorB",
        "spotlight.targetColorR",
        "spotlight.targetColorG",
        "spotlight.targetColorB"
    )

    fun value(state: SpotlightState, property: String): Float = when (property) {
        "spotlight.sourceX" -> state.sourceX
        "spotlight.sourceY" -> state.sourceY
        "spotlight.targetX" -> state.targetX
        "spotlight.targetY" -> state.targetY
        "spotlight.rotation" -> state.rotationDeg
        "spotlight.intensity" -> state.intensity
        "spotlight.radius" -> state.radiusDeg
        "spotlight.softness" -> state.softness
        "spotlight.colorR" -> channel(state.color, 16)
        "spotlight.colorG" -> channel(state.color, 8)
        "spotlight.colorB" -> channel(state.color, 0)
        "spotlight.targetColorR" -> channel(state.targetColor, 16)
        "spotlight.targetColorG" -> channel(state.targetColor, 8)
        "spotlight.targetColorB" -> channel(state.targetColor, 0)
        else -> 0f
    }

    fun sample(
        state: SpotlightState,
        keyframes: KeyframeMap,
        timeSec: Float
    ): SpotlightState {
        fun s(property: String, base: Float) =
            KeyframeStore.sample(keyframes, property, timeSec, base)

        fun color(prefix: String, base: Long): Long {
            val r = s("$prefix" + "R", channel(base, 16)).toInt().coerceIn(0, 255)
            val g = s("$prefix" + "G", channel(base, 8)).toInt().coerceIn(0, 255)
            val b = s("$prefix" + "B", channel(base, 0)).toInt().coerceIn(0, 255)
            return (base and 0xFF000000L) or (r.toLong() shl 16) or
                    (g.toLong() shl 8) or b.toLong()
        }

        return state.copy(
            sourceX = s("spotlight.sourceX", state.sourceX).coerceIn(0f, 100f),
            sourceY = s("spotlight.sourceY", state.sourceY).coerceIn(0f, 100f),
            targetX = s("spotlight.targetX", state.targetX).coerceIn(0f, 100f),
            targetY = s("spotlight.targetY", state.targetY).coerceIn(0f, 100f),
            rotationDeg = s("spotlight.rotation", state.rotationDeg).coerceIn(-180f, 180f),
            intensity = s("spotlight.intensity", state.intensity).coerceIn(0f, 2.5f),
            radiusDeg = s("spotlight.radius", state.radiusDeg).coerceIn(6f, 80f),
            softness = s("spotlight.softness", state.softness).coerceIn(0f, 1f),
            color = color("spotlight.color", state.color),
            targetColor = color("spotlight.targetColor", state.targetColor)
        )
    }

    fun updateAutoKeyframes(
        oldState: SpotlightState,
        newState: SpotlightState,
        keyframes: KeyframeMap,
        timeSec: Float
    ): KeyframeMap {
        var result = keyframes
        properties.forEach { property ->
            val oldValue = value(oldState, property)
            val newValue = value(newState, property)
            if (oldValue != newValue) {
                result = KeyframeStore.autoKeyframeIfActive(result, property, timeSec, newValue)
            }
        }
        return result
    }

    private fun channel(color: Long, shift: Int): Float =
        ((color shr shift) and 0xFFL).toFloat()
}
