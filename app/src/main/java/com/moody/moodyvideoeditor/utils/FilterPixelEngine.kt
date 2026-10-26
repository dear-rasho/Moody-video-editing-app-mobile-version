package com.moody.moodyvideoeditor.utils

import android.graphics.ColorMatrix
import com.moody.moodyvideoeditor.data.ColorFilterValues

object FilterPixelEngine {

    fun apply(
        red: Float,
        green: Float,
        blue: Float,
        filter: ColorFilterValues
    ): Triple<Float, Float, Float> =
        apply(red, green, blue, EffectsEngine.buildColorMatrix(filter))

    fun apply(
        red: Float,
        green: Float,
        blue: Float,
        matrix: ColorMatrix
    ): Triple<Float, Float, Float> {
        val values = matrix.array
        return Triple(
            (values[0] * red + values[1] * green + values[2] * blue + values[3] * 255f + values[4])
                .coerceIn(0f, 255f),
            (values[5] * red + values[6] * green + values[7] * blue + values[8] * 255f + values[9])
                .coerceIn(0f, 255f),
            (values[10] * red + values[11] * green + values[12] * blue + values[13] * 255f + values[14])
                .coerceIn(0f, 255f)
        )
    }
}
