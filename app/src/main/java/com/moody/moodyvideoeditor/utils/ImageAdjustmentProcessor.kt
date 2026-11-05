package com.moody.moodyvideoeditor.utils

import android.graphics.Bitmap
import android.graphics.ColorMatrix
import android.util.Log
import com.moody.moodyvideoeditor.data.AdjustmentData

object ImageAdjustmentProcessor {

    private const val TAG = "ImgAdjProcessor"

    fun hasSharpenOrColorChannels(adj: AdjustmentData): Boolean =
        adj.sharpen != 0f || adj.reds != 0f || adj.oranges != 0f ||
                adj.yellows != 0f || adj.greens != 0f || adj.cyans != 0f ||
                adj.blues != 0f || adj.purples != 0f || adj.magentas != 0f ||
                adj.skinTones != 0f

    fun hasNoiseOrVignette(adj: AdjustmentData): Boolean =
        adj.noise != 0f || adj.vignette != 0f

    fun process(
        source: Bitmap,
        adj: AdjustmentData,
        detailAdjustment: AdjustmentData = adj,
        colorMatrix: ColorMatrix? = null
    ): Bitmap {
        if (source.width <= 0 || source.height <= 0) return source
        if (adj.isDefault && detailAdjustment.noise == 0f &&
            detailAdjustment.vignette == 0f && colorMatrix == null
        ) return source

        var workingBitmap: Bitmap? = null
        var processedBitmap: Bitmap? = null
        try {
            val maxDimension = 1440
            workingBitmap = if (source.width > maxDimension || source.height > maxDimension) {
                val scale = maxDimension.toFloat() / maxOf(source.width, source.height)
                Bitmap.createScaledBitmap(
                    source,
                    (source.width * scale).toInt().coerceAtLeast(1),
                    (source.height * scale).toInt().coerceAtLeast(1),
                    true
                )
            } else source

            processedBitmap = workingBitmap.copy(Bitmap.Config.ARGB_8888, true)
                ?: error("Could not allocate bitmap for native adjustment")
            val nativeAdjustment = adj.copy(
                noise = detailAdjustment.noise,
                vignette = detailAdjustment.vignette
            )
            val matrix = colorMatrix ?: ColorMatrix()
            check(AdjustmentNative.process(processedBitmap, nativeAdjustment, matrix)) {
                "Native adjustment rejected bitmap ${processedBitmap.width}x${processedBitmap.height}"
            }

            val output = if (processedBitmap.width == source.width &&
                processedBitmap.height == source.height
            ) {
                processedBitmap
            } else {
                Bitmap.createScaledBitmap(
                    processedBitmap,
                    source.width,
                    source.height,
                    true
                ).also { processedBitmap.recycle() }
            }
            processedBitmap = null
            return output
        } catch (error: RuntimeException) {
            Log.e(TAG, "Native adjustment processing failed", error)
            throw error
        } finally {
            processedBitmap?.takeIf { !it.isRecycled }?.recycle()
            workingBitmap?.takeIf { it !== source && !it.isRecycled }?.recycle()
        }
    }
}
