package com.moody.moodyvideoeditor.utils

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import android.view.PixelCopy
import android.view.View
import android.view.Window
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

object ChromaKeyEyedropper {

    private const val TAG = "Eyedropper"

    // ═══════════════════════════════════════════════════════════
    //  METHOD 1 — Pick from a Bitmap (used when we already have frame)
    // ═══════════════════════════════════════════════════════════
    fun pickFromBitmap(
        bitmap: Bitmap,
        x: Float,
        y: Float
    ): Long {
        val px = x.toInt().coerceIn(0, bitmap.width - 1)
        val py = y.toInt().coerceIn(0, bitmap.height - 1)
        val c = bitmap.getPixel(px, py)
        return 0xFF000000L or
                ((Color.red(c).toLong() and 0xFF) shl 16) or
                ((Color.green(c).toLong() and 0xFF) shl 8) or
                (Color.blue(c).toLong() and 0xFF)
    }

    // ═══════════════════════════════════════════════════════════
    //  METHOD 2 — Pick from window (screen) at given coords
    //  Used when user taps "Pick from Screen" button
    // ═══════════════════════════════════════════════════════════
    suspend fun pickFromWindow(
        window: Window,
        screenX: Int,
        screenY: Int
    ): Long? = suspendCancellableCoroutine { cont ->
        val view = window.decorView
        if (view.width <= 0 || view.height <= 0) {
            cont.resume(null); return@suspendCancellableCoroutine
        }

        val bmp = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        try {
            PixelCopy.request(
                window,
                android.graphics.Rect(screenX, screenY, screenX + 1, screenY + 1),
                bmp,
                { result ->
                    if (result == PixelCopy.SUCCESS) {
                        val c = bmp.getPixel(0, 0)
                        val long =
                            0xFF000000L or
                                    ((Color.red(c).toLong() and 0xFF) shl 16) or
                                    ((Color.green(c).toLong() and 0xFF) shl 8) or
                                    (Color.blue(c).toLong() and 0xFF)
                        bmp.recycle()
                        cont.resume(long)
                    } else {
                        Log.w(TAG, "PixelCopy failed: $result")
                        bmp.recycle()
                        cont.resume(null)
                    }
                },
                android.os.Handler(android.os.Looper.getMainLooper())
            )
        } catch (t: Throwable) {
            Log.e(TAG, "PixelCopy exception", t)
            bmp.recycle()
            cont.resume(null)
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  METHOD 3 — Average color in a small area (better for
    //  noisy images / video — avoids picking a single stray pixel)
    // ═══════════════════════════════════════════════════════════
    fun pickAverageFromBitmap(
        bitmap: Bitmap,
        x: Float,
        y: Float,
        radiusPx: Int = 4
    ): Long {
        val cx = x.toInt().coerceIn(0, bitmap.width - 1)
        val cy = y.toInt().coerceIn(0, bitmap.height - 1)

        var rSum = 0L
        var gSum = 0L
        var bSum = 0L
        var count = 0

        for (dy in -radiusPx..radiusPx) {
            for (dx in -radiusPx..radiusPx) {
                val px = (cx + dx).coerceIn(0, bitmap.width - 1)
                val py = (cy + dy).coerceIn(0, bitmap.height - 1)
                val c = bitmap.getPixel(px, py)
                if (Color.alpha(c) < 20) continue
                rSum += Color.red(c)
                gSum += Color.green(c)
                bSum += Color.blue(c)
                count++
            }
        }

        if (count == 0) return 0xFF00FF00L
        val r = (rSum / count).coerceIn(0, 255)
        val g = (gSum / count).coerceIn(0, 255)
        val b = (bSum / count).coerceIn(0, 255)
        return 0xFF000000L or (r shl 16) or (g shl 8) or b
    }
}