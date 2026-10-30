package com.moody.moodyvideoeditor.utils

import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import com.moody.moodyvideoeditor.data.LayerShadowState
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.abs
import kotlin.math.max

object LayerShadowRenderer {
    fun create(
        state: LayerShadowState,
        pixelScale: Float = 1f,
        input: RenderEffect? = null
    ): RenderEffect? {
        if (Build.VERSION.SDK_INT < 31 || !state.enabled) return input

        val color = state.color.toInt()
        val alpha = Color.alpha(color) / 255f
        val matrix = ColorMatrix(
            floatArrayOf(
                0f, 0f, 0f, Color.red(color) / 255f, 0f,
                0f, 0f, 0f, Color.green(color) / 255f, 0f,
                0f, 0f, 0f, Color.blue(color) / 255f, 0f,
                0f, 0f, 0f, alpha, 0f
            )
        )
        val colorFilter = ColorMatrixColorFilter(matrix)
        val source = input ?: RenderEffect.createColorFilterEffect(
            ColorMatrixColorFilter(ColorMatrix())
        )
        val mask = RenderEffect.createColorFilterEffect(colorFilter, source)
        val blur = state.blur * pixelScale
        val blurred = if (blur > 0.01f) {
            RenderEffect.createBlurEffect(blur, blur, mask, Shader.TileMode.DECAL)
        } else {
            mask
        }
        val shadow = RenderEffect.createOffsetEffect(
            state.offsetX * pixelScale,
            state.offsetY * pixelScale,
            blurred
        )
        return RenderEffect.createBlendModeEffect(
            shadow,
            source,
            android.graphics.BlendMode.SRC_OVER
        )
    }

    fun applyToBitmap(
        canvas: android.graphics.Canvas,
        state: LayerShadowState,
        width: Int,
        height: Int,
        draw: (android.graphics.Canvas) -> Unit
    ) {
        if (!state.enabled) {
            draw(canvas)
            return
        }
        val source = android.graphics.Bitmap.createBitmap(
            width,
            height,
            android.graphics.Bitmap.Config.ARGB_8888
        )
        try {
            draw(android.graphics.Canvas(source))
            val pixelScale = width / 720f
            val offsets = IntArray(2)
            val maskPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                val blur = state.blur * pixelScale
                if (blur > 0.01f) {
                    maskFilter = android.graphics.BlurMaskFilter(
                        blur,
                        android.graphics.BlurMaskFilter.Blur.NORMAL
                    )
                }
            }
            val alphaBitmap = source.extractAlpha(maskPaint, offsets)
            try {
                val shadowPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    colorFilter = android.graphics.PorterDuffColorFilter(
                        state.color.toInt(),
                        android.graphics.PorterDuff.Mode.SRC_IN
                    )
                }
                val coloredShadow = android.graphics.Bitmap.createBitmap(
                    alphaBitmap.width,
                    alphaBitmap.height,
                    android.graphics.Bitmap.Config.ARGB_8888
                )
                try {
                    android.graphics.Canvas(coloredShadow).drawBitmap(
                        alphaBitmap, 0f, 0f, shadowPaint
                    )
                    canvas.drawBitmap(
                        coloredShadow,
                        offsets[0] + state.offsetX * pixelScale,
                        offsets[1] + state.offsetY * pixelScale,
                        null
                    )
                    canvas.drawBitmap(source, 0f, 0f, null)
                } finally {
                    coloredShadow.recycle()
                }
            } finally {
                alphaBitmap.recycle()
            }
        } finally {
            source.recycle()
        }
    }

    fun ffmpegPadding(state: LayerShadowState, targetWidth: Int): Int {
        val scale = targetWidth / 720f
        val dx = abs(state.offsetX * scale)
        val dy = abs(state.offsetY * scale)
        val blur = state.blur * scale
        return ceil(max(dx, dy) + blur * 3f + 2f)
            .toInt()
            .coerceAtLeast(2)
    }

    fun ffmpegOffset(state: LayerShadowState, targetWidth: Int): Pair<Float, Float> {
        val scale = targetWidth / 720f
        return state.offsetX * scale to state.offsetY * scale
    }

    fun ffmpegBlur(state: LayerShadowState, targetWidth: Int): Float =
        state.blur * (targetWidth / 720f)

    fun ffmpegNumber(value: Float): String =
        String.format(Locale.US, "%.3f", value)
}
