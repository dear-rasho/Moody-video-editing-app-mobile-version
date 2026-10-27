package com.moody.moodyvideoeditor.utils

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Log
import java.io.File
import java.io.FileOutputStream

object WatermarkHelper {

    private var cachedPath: String? = null

    private fun getWatermarkPath(context: Context): String? {
        if (cachedPath != null && File(cachedPath!!).exists()) {
            return cachedPath
        }

        val file = File(context.filesDir, "watermark_img.png")
        try {
            val resId = context.resources.getIdentifier("watermark_img", "drawable", context.packageName)
            if (resId != 0) {
                val bmp = BitmapFactory.decodeResource(context.resources, resId)
                FileOutputStream(file).use { out ->
                    bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
                }
                bmp.recycle()
                cachedPath = file.absolutePath
                return cachedPath
            }
        } catch (e: Throwable) {
            Log.e("WATERMARK", "Error: ${e.message}")
        }
        return null
    }

    fun addWatermarkToFilter(
        context: Context,
        filterParts: MutableList<String>,
        finalVideoLabel: String,
        targetW: Int,
        targetH: Int
    ): String {
        val wmPath = getWatermarkPath(context)
        if (wmPath == null) return finalVideoLabel

        var escapedPath = wmPath.replace("\\", "/")
        escapedPath = escapedPath.replace(":", "\\:")
        escapedPath = escapedPath.replace("'", "\\'")

        // Keep the watermark proportional across portrait and landscape exports.
        val wmWidth = (Math.min(targetW, targetH) * 0.18f).toInt().coerceAtLeast(80)

        try {
            filterParts.add("movie='$escapedPath',format=rgba,scale=$wmWidth:-1[wm]")

            val (x, y) = when (SettingsConsumer.watermarkPosition) {
                "top_right" -> "W-w-10" to "10"
                "bottom_left" -> "10" to "H-h-10"
                "bottom_right" -> "W-w-10" to "H-h-10"
                else -> "10" to "10"
            }
            filterParts.add(
                "[$finalVideoLabel][wm]overlay=x='$x':y='$y'[final_wm]"
            )

            return "final_wm"
        } catch (e: Throwable) {
            Log.e("WATERMARK", "Failed to add watermark filter", e)
            return finalVideoLabel
        }
    }
}