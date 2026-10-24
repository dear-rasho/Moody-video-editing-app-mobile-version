package com.moody.moodyvideoeditor.utils

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import com.moody.moodyvideoeditor.data.EditorClip

object CropHelper {

    const val MAX_CROP = 1f
    const val MIN_VISIBLE = 0.001f

    data class CropRect(val l: Float, val r: Float, val t: Float, val b: Float)

    /** NO opposite-side clamping — user can set 0..100% freely on every side. */
    fun clampSide(value: Float, oppositeSide: Float): Float =
        value.coerceIn(0f, 1f)

    fun clampAll(
        cropL: Float, cropR: Float, cropT: Float, cropB: Float
    ): CropRect = CropRect(
        cropL.coerceIn(0f, 1f),
        cropR.coerceIn(0f, 1f),
        cropT.coerceIn(0f, 1f),
        cropB.coerceIn(0f, 1f)
    )

    /** If (left+right) OR (top+bottom) fill the entire axis → layer is invisible. */
    fun isFullyCropped(l: Float, r: Float, t: Float, b: Float): Boolean =
        (l + r) >= 1f - MIN_VISIBLE || (t + b) >= 1f - MIN_VISIBLE

    fun isCropped(clip: EditorClip): Boolean =
        clip.cropL > 0.0001f || clip.cropR > 0.0001f ||
                clip.cropT > 0.0001f || clip.cropB > 0.0001f
}

fun Modifier.applyTransparentCrop(
    cropL: Float, cropR: Float, cropT: Float, cropB: Float
): Modifier = this.drawWithContent {
    val safe = CropHelper.clampAll(cropL, cropR, cropT, cropB)
    if (safe.l <= 0.0001f && safe.r <= 0.0001f &&
        safe.t <= 0.0001f && safe.b <= 0.0001f
    ) {
        drawContent()
        return@drawWithContent
    }
    if (CropHelper.isFullyCropped(safe.l, safe.r, safe.t, safe.b)) {
        return@drawWithContent  // fully cropped → draw nothing
    }
    val left = size.width * safe.l
    val top = size.height * safe.t
    val right = size.width * (1f - safe.r)
    val bottom = size.height * (1f - safe.b)
    if (right <= left || bottom <= top) return@drawWithContent
    clipRect(left = left, top = top, right = right, bottom = bottom) {
        this@drawWithContent.drawContent()
    }
}