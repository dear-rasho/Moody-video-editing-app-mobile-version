package com.moody.moodyvideoeditor.data

import org.json.JSONObject

data class SpotlightState(
    val sourceX: Float = 50f,
    val sourceY: Float = 6f,
    val targetX: Float = 50f,
    val targetY: Float = 68f,
    val rotationDeg: Float = 0f,
    val intensity: Float = 1f,
    val color: Long = 0xFFFFF1CCL,
    val targetColor: Long = 0xFFFFF1CCL,
    val radiusDeg: Float = 28f,
    val softness: Float = 0.65f
) {
    fun toJson() = JSONObject().apply {
        put("sourceX", sourceX.toDouble())
        put("sourceY", sourceY.toDouble())
        put("targetX", targetX.toDouble())
        put("targetY", targetY.toDouble())
        put("rotationDeg", rotationDeg.toDouble())
        put("intensity", intensity.toDouble())
        put("color", color)
        put("targetColor", targetColor)
        put("radiusDeg", radiusDeg.toDouble())
        put("softness", softness.toDouble())
    }

    companion object {
        fun fromJson(json: JSONObject?) = json?.let {
            SpotlightState(
                sourceX = it.optDouble("sourceX", 50.0).toFloat().coerceIn(0f, 100f),
                sourceY = it.optDouble("sourceY", 6.0).toFloat().coerceIn(0f, 100f),
                targetX = it.optDouble("targetX", 50.0).toFloat().coerceIn(0f, 100f),
                targetY = it.optDouble("targetY", 68.0).toFloat().coerceIn(0f, 100f),
                rotationDeg = it.optDouble("rotationDeg", 0.0).toFloat().coerceIn(-180f, 180f),
                intensity = it.optDouble("intensity", 1.0).toFloat().coerceIn(0f, 2.5f),
                color = it.optLong("color", 0xFFFFF1CCL),
                targetColor = it.optLong("targetColor", it.optLong("color", 0xFFFFF1CCL)),
                radiusDeg = it.optDouble("radiusDeg", 28.0).toFloat().coerceIn(6f, 80f),
                softness = it.optDouble("softness", 0.65).toFloat().coerceIn(0f, 1f)
            )
        } ?: SpotlightState()
    }
}

data class SpotlightPreset(
    val id: String,
    val label: String,
    val state: SpotlightState
)

object SpotlightPresets {
    val all = listOf(
        SpotlightPreset("stage-white", "Stage White", SpotlightState()),
        SpotlightPreset(
            "neon-duo",
            "Neon Duo",
            SpotlightState(
                color = 0xFFFF38D1L,
                targetColor = 0xFF31D9FFFF,
                radiusDeg = 42f,
                softness = 0.78f
            )
        ),
        SpotlightPreset(
            "warm-gold",
            "Warm Gold",
            SpotlightState(
                color = 0xFFFFA52CL,
                targetColor = 0xFFFFE7A1L,
                intensity = 1.2f,
                radiusDeg = 34f,
                softness = 0.72f
            )
        )
    )
}
