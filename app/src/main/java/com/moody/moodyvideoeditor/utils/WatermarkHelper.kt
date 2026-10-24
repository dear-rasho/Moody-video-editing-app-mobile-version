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

        // Size: Video ki choti side (min dimension) ka 18% (Har ratio mein barabar aur bada lagega)
        val wmWidth = (Math.min(targetW, targetH) * 0.18f).toInt().coerceAtLeast(80)

        try {
            // 1. Image ko load kiya
            filterParts.add("movie='$escapedPath',format=rgba,scale=$wmWidth:-1[wm_raw]")

            // 2. FIX: Black box ab proportional hai (Image se 10% bada hoga har ratio mein same dikhega)
            filterParts.add("[wm_raw]pad=iw*1.1:ih*1.1:(ow-iw)/2:(oh-ih)/2:color=black@0.6[wm]")

            // 3. Position: Bilkul Bottom-Right (Neeche touch karega)
            filterParts.add("[$finalVideoLabel][wm]overlay=W-w-10:H-h[final_wm]")

            return "final_wm"
        } catch (e: Throwable) {
            return finalVideoLabel
        }
    }
}