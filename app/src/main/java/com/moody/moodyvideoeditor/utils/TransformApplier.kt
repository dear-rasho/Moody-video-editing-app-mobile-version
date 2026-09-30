package com.moody.moodyvideoeditor.utils

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import com.moody.moodyvideoeditor.data.EditorClip
import com.moody.moodyvideoeditor.data.KeyframeMap
import kotlin.math.abs

data class TransformValues(
    val x: Float = 50f,
    val y: Float = 50f,
    val scale: Float = 100f,
    val rotation: Float = 0f,
    val anchorX: Float = 50f,
    val anchorY: Float = 50f,
    val cropL: Float = 0f,
    val cropR: Float = 0f,
    val cropT: Float = 0f,
    val cropB: Float = 0f
) {
    fun isIdentity(): Boolean {
        val EPS = 0.01f
        return abs(x - 50f) < EPS && abs(y - 50f) < EPS &&
                abs(scale - 100f) < EPS && abs(rotation) < EPS &&
                abs(anchorX - 50f) < EPS && abs(anchorY - 50f) < EPS &&
                abs(cropL) < EPS && abs(cropR) < EPS &&
                abs(cropT) < EPS && abs(cropB) < EPS
    }
}

object TransformApplier {

    /**
     * Extract base transform from clip.
     * Different clip types store values differently.
     */
    fun baseOf(clip: EditorClip): TransformValues {
        // Text clip
        if (clip.isTextClip && clip.textState != null) {
            val st = clip.textState
            return TransformValues(
                x = st.positionX, y = st.positionY,
                scale = st.scale, rotation = st.rotation,
                anchorX = st.anchorX, anchorY = st.anchorY,
                cropL = clip.cropL, cropR = clip.cropR,
                cropT = clip.cropT, cropB = clip.cropB
            )
        }
        // Sticker clip
        if (clip.isStickerClip && clip.stickerState != null) {
            val ss = clip.stickerState
            return TransformValues(
                x = ss.x, y = ss.y,
                scale = ss.scale, rotation = ss.rotation,
                anchorX = 50f, anchorY = 50f,
                cropL = clip.cropL, cropR = clip.cropR,
                cropT = clip.cropT, cropB = clip.cropB
            )
        }
        // Normal visual clip — offsetX/offsetY are normalized delta (-1..1)
        return TransformValues(
            x = 50f + clip.offsetX * 100f,
            y = 50f + clip.offsetY * 100f,
            scale = clip.scale * 100f,
            rotation = clip.rotation,
            anchorX = 50f, anchorY = 50f,
            cropL = clip.cropL, cropR = clip.cropR,
            cropT = clip.cropT, cropB = clip.cropB
        )
    }

    /**
     * Sample keyframes at time — returns live transform.
     */
    fun resolveLive(clip: EditorClip, timeSec: Float): TransformValues {
        val base = baseOf(clip)
        val kfs: KeyframeMap = clip.keyframes
        return if (KeyframeStore.hasAnyKeyframes(kfs)) {
            KeyframeStore.sampleAll(kfs, timeSec, base)
        } else base
    }

    /**
     * Draw with transform applied — for Canvas-only rendering (export).
     */
    fun DrawScope.applyTransform(W: Float, H: Float, t: TransformValues) {
        if (t.isIdentity()) return
        withTransform({
            val originX = W * t.anchorX / 100f
            val originY = H * t.anchorY / 100f
            val offsetX = (t.x - 50f) / 100f * W
            val offsetY = (t.y - 50f) / 100f * H
            translate(originX + offsetX, originY + offsetY)
            if (t.rotation != 0f) rotate(t.rotation, Offset(originX, originY))
            if (t.scale != 100f) {
                val sc = t.scale / 100f
                scale(sc, sc, Offset(originX, originY))
            }
        }) { }
    }
}