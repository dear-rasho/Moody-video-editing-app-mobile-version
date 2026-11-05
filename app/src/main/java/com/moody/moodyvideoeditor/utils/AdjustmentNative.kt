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

    private external fun nativeProcessBitmap(
        bitmap: Bitmap,
        adjustments: FloatArray,
        colorMatrix: FloatArray
    ): Boolean

    private val IDENTITY_MATRIX = floatArrayOf(
        1f, 0f, 0f, 0f, 0f,
        0f, 1f, 0f, 0f, 0f,
        0f, 0f, 1f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f
    )
}
