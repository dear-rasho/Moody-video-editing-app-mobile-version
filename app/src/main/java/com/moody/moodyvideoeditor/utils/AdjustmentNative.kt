package com.moody.moodyvideoeditor.utils

import android.graphics.Bitmap
import android.graphics.ColorMatrix
import com.moody.moodyvideoeditor.data.AdjustmentData

object AdjustmentNative {
    init {
        System.loadLibrary("moodywhisper")
    }

    fun process(
        bitmap: Bitmap,
        adjustment: AdjustmentData,
        colorMatrix: ColorMatrix?
    ): Boolean {
        val values = floatArrayOf(
            adjustment.reds,
            adjustment.oranges,
            adjustment.yellows,
            adjustment.greens,
            adjustment.cyans,
            adjustment.blues,
            adjustment.purples,
            adjustment.magentas,
            adjustment.skinTones,
            adjustment.sharpen,
            adjustment.noise,
            adjustment.vignette
        )
        return nativeProcessBitmap(bitmap, values, colorMatrix?.array ?: IDENTITY_MATRIX)
    }

    fun buildColorMatrix(adjustment: AdjustmentData): ColorMatrix {
        return ColorMatrix(nativeBuildColorMatrix(adjustmentValues(adjustment)))
    }

    fun buildCubeLut(adjustment: AdjustmentData, size: Int): FloatArray {
        return nativeBuildCubeLut(adjustmentValues(adjustment), size)
            ?: error("Native adjustment LUT generation failed for size $size")
    }

    private fun adjustmentValues(adjustment: AdjustmentData) = floatArrayOf(
        adjustment.brightness,
        adjustment.contrast,
        adjustment.exposure,
        adjustment.whites,
        adjustment.blacks,
        adjustment.shadows,
        adjustment.highlights,
        adjustment.saturation,
        adjustment.vibrance,
        adjustment.clarity,
        adjustment.temperature,
        adjustment.tint,
        adjustment.noise,
        adjustment.sharpen,
        adjustment.vignette,
        adjustment.reds,
        adjustment.oranges,
        adjustment.yellows,
        adjustment.greens,
        adjustment.cyans,
        adjustment.blues,
        adjustment.purples,
        adjustment.magentas,
        adjustment.skinTones
    )

    private external fun nativeProcessBitmap(
        bitmap: Bitmap,
        adjustments: FloatArray,
        colorMatrix: FloatArray
    ): Boolean

    private external fun nativeBuildColorMatrix(adjustments: FloatArray): FloatArray

    private external fun nativeBuildCubeLut(adjustments: FloatArray, size: Int): FloatArray?

    private val IDENTITY_MATRIX = floatArrayOf(
        1f, 0f, 0f, 0f, 0f,
        0f, 1f, 0f, 0f, 0f,
        0f, 0f, 1f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f
    )
}
