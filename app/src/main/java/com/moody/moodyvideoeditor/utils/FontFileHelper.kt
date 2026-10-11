package com.moody.moodyvideoeditor.utils

import android.content.Context
import android.graphics.Typeface
import java.io.File

object FontFileHelper {

    private var cachedFontPath: String? = null

    /**
     * Get FFmpeg-compatible font file path.
     * @param fontName Optional — agar diya, asset font extract karega
     */
    fun getFontPath(context: Context, fontName: String? = null): String? {

        // 1) Agar specific fontName diya, asset se extract karo
        if (!fontName.isNullOrBlank()) {
            FontLibrary.exportAssetFontToCache(fontName)?.let { return it }
        }

        // 2) Cached fallback
        cachedFontPath?.let { if (File(it).exists()) return it }

        val candidates = listOf(
            // Urdu system fonts (Android 7+)
            "/system/fonts/NotoNastaliqUrdu-Regular.ttf",
            "/system/fonts/NotoNastaliqUrdu-VariableFont_wght.ttf",
            "/system/fonts/NotoNaskhArabic-Regular.ttf",
            "/system/fonts/NotoSansArabic-Regular.ttf",
            // Standard Latin
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

        // 3) Bundled default
        return try {
            val outFile = File(context.cacheDir, "default_font.ttf")
            if (!outFile.exists() || outFile.length() < 100) {
                try {
                    context.assets.open("default_font.ttf").use { input ->
                        outFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                } catch (_: Throwable) {
                    // Not bundled — ignore
                }
            }
            if (outFile.exists() && outFile.length() > 100) {
                cachedFontPath = outFile.absolutePath
                outFile.absolutePath
            } else null
        } catch (e: Exception) {
            try {
                Typeface.DEFAULT
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

    /** Backward-compat overload (koi fontName nahi) */
    fun getFontPath(context: Context): String? = getFontPath(context, null)

    fun escapeFontPath(path: String): String =
        path.replace("\\", "/").replace(":", "\\:")
}