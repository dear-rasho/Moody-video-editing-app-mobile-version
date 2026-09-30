package com.moody.moodyvideoeditor.utils

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope

/**
 * Mirrors js/features/crop.js
 * Crop values are normalized 0..1 in EditorClip (cropL, cropR, cropT, cropB).
 */
object CropEngine {

    const val MAX_CROP = 0.45f

    /** Clamp all four values + prevent overlap */
    fun clamp(l: Float, r: Float, t: Float, b: Float): Quad {
        var cl = l.coerceIn(0f, MAX_CROP)
        var cr = r.coerceIn(0f, MAX_CROP)
        var ct = t.coerceIn(0f, MAX_CROP)
        var cb = b.coerceIn(0f, MAX_CROP)
        if (cl + cr > 0.9f) {
            cl = 0.45f; cr = 0.45f
        }
        if (ct + cb > 0.9f) {
            ct = 0.45f; cb = 0.45f
        }
        return Quad(cl, cr, ct, cb)
    }

    data class Quad(val l: Float, val r: Float, val t: Float, val b: Float)

    /** Draw black masks outside the crop rect — used in preview overlay */
    fun drawMasks(scope: DrawScope, W: Float, H: Float, q: Quad) {
        // Top
        scope.drawRect(Color.Black.copy(alpha = 0.6f), Offset.Zero, Size(W, H * q.t))
        // Bottom
        scope.drawRect(Color.Black.copy(alpha = 0.6f), Offset(0f, H * (1f - q.b)), Size(W, H * q.b))
        // Left
        scope.drawRect(
            Color.Black.copy(alpha = 0.6f),
            Offset(0f, H * q.t),
            Size(W * q.l, H * (1f - q.t - q.b))
        )
        // Right
        scope.drawRect(
            Color.Black.copy(alpha = 0.6f),
            Offset(W * (1f - q.r), H * q.t),
            Size(W * q.r, H * (1f - q.t - q.b))
        )
    }

    /** Aspect preset crop values (16:9, 1:1 etc.) */
    fun presetFor(aspectKey: String, frameW: Float, frameH: Float): Quad {
        val frameAR = if (frameH > 0f) frameW / frameH else 16f / 9f
        val targetAR = when (aspectKey) {
            "1:1" -> 1f
            "16:9" -> 16f / 9f
            "9:16" -> 9f / 16f
            "4:3" -> 4f / 3f
            "3:4" -> 3f / 4f
            else -> return Quad(0f, 0f, 0f, 0f)
        }
        var wPct = 0.9f
        var hPct = 0.9f
        if (targetAR >= frameAR) {
            hPct = (frameAR / targetAR) * wPct
        } else {
            wPct = (targetAR / frameAR) * hPct
        }
        wPct = wPct.coerceIn(0.1f, 1f)
        hPct = hPct.coerceIn(0.1f, 1f)
        val sideEach = (1f - wPct) / 2f
        val tbEach = (1f - hPct) / 2f
        return Quad(sideEach, sideEach, tbEach, tbEach)
    }
}