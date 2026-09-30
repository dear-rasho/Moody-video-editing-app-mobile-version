package com.moody.moodyvideoeditor.utils

import android.content.Context
import android.graphics.Typeface
import java.io.File

/**
 * Extracts a system font to cache dir for FFmpeg drawtext filter.
 * FFmpeg drawtext on Android needs a real font file path.
 */
object FontFileHelper {

    private var cachedFontPath: String? = null

    fun getFontPath(context: Context): String? {
        cachedFontPath?.let { if (File(it).exists()) return it }

        val candidates = listOf(
            "/system/fonts/Roboto-Regular.ttf",
            "/system/fonts/Roboto.ttf",
            "/system/fonts/DroidSans.ttf",
            "/system/fonts/NotoSans-Regular.ttf",
            "/system/fonts/AndroidClock.ttf",
            "/system/fonts/RobotoCondensed-Regular.ttf"
        )

        for (path in candidates) {
            val f = File(path)
            if (f.exists() && f.canRead()) {
                cachedFontPath = path
                return path
            }
        }

        // Fallback: copy from Typeface asset
        return try {
            val outFile = File(context.cacheDir, "default_font.ttf")
            if (!outFile.exists() || outFile.length() < 100) {
                context.assets.open("default_font.ttf").use { input ->
                    outFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
            if (outFile.exists() && outFile.length() > 100) {
                cachedFontPath = outFile.absolutePath
                outFile.absolutePath
            } else null
        } catch (e: Exception) {
            // Try system font from Typeface default
            try {
                val tf = Typeface.DEFAULT
                val outFile = File(context.cacheDir, "fallback_font.ttf")
                if (outFile.exists() && outFile.length() > 100) {
                    cachedFontPath = outFile.absolutePath
                    return outFile.absolutePath
                }
            } catch (_: Exception) {
            }
            null
        }
    }

    fun escapeFontPath(path: String): String =
        path.replace("\\", "/").replace(":", "\\:")
}