package com.moody.moodyvideoeditor.data

data class LayerShadowState(
    val enabled: Boolean = false,
    val color: Long = 0xFF000000,
    val blur: Float = 8f,
    val distance: Float = 2.8f,
    val angle: Float = 45f
) {
    val offsetX: Float
        get() = distance * kotlin.math.cos(Math.toRadians(angle.toDouble())).toFloat()

    val offsetY: Float
        get() = distance * kotlin.math.sin(Math.toRadians(angle.toDouble())).toFloat()

    companion object {
        fun fromLegacyOffsets(
            enabled: Boolean,
            color: Long,
            blur: Float,
            offsetX: Float,
            offsetY: Float
        ): LayerShadowState {
            val distance = kotlin.math.hypot(offsetX, offsetY)
            val angle = Math.toDegrees(kotlin.math.atan2(offsetY, offsetX).toDouble())
                .let { (it + 360.0) % 360.0 }
                .toFloat()
            return LayerShadowState(
                enabled = enabled,
                color = color,
                blur = blur,
                distance = distance,
                angle = angle
            )
        }
    }
}
