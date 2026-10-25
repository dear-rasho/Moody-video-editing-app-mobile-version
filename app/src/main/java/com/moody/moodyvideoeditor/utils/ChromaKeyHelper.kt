package com.moody.moodyvideoeditor.utils

import android.graphics.Bitmap
import android.graphics.Color
import com.moody.moodyvideoeditor.data.ChromaKeyState

object ChromaKeyHelper {

    fun applyChromaKey(source: Bitmap, state: ChromaKeyState): Bitmap {
        if (!state.isActive) return source

        val width = source.width
        val height = source.height
        val resultBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)

        // Target Color (Jo remove karna hai)
        val tR = ((state.targetColor shr 16) and 0xFF).toInt()
        val tG = ((state.targetColor shr 8) and 0xFF).toInt()
        val tB = (state.targetColor and 0xFF).toInt()

        // UI slider se Tolerance nikal rahe hain
        // Similarity: 0-100. Max distance approx 441 (sqrt(255^2*3))
        val maxDist = 441f
        val tolerance = (state.similarity / 100f) * maxDist
        val smooth = (state.smoothness / 100f) * maxDist

        for (i in pixels.indices) {
            val c = pixels[i]
            val a = Color.alpha(c)
            if (a == 0) continue

            val r = Color.red(c)
            val g = Color.green(c)
            val b = Color.blue(c)

            // Calculate distance between pixel color and target color
            val dist = kotlin.math.sqrt(
                ((r - tR) * (r - tR) + (g - tG) * (g - tG) + (b - tB) * (b - tB)).toDouble()
            ).toFloat()

            if (dist < tolerance) {
                // Pura match -> Remove (Transparent)
                pixels[i] = Color.TRANSPARENT
            } else if (dist < tolerance + smooth) {
                // Edge / Smooth area -> Fade out (spill)
                val alphaFactor = (dist - tolerance) / smooth
                val newAlpha = (a * alphaFactor).toInt().coerceIn(0, 255)
                pixels[i] = Color.argb(newAlpha, r, g, b)
            }
            // Agar dist > tolerance + smooth, toh pixel waisa hi rahega (Kuch nahi hoga)
        }

        resultBitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return resultBitmap
    }
}