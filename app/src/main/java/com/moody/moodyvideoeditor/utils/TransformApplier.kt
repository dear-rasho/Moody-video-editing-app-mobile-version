package com.moody.moodyvideoeditor.utils

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import com.moody.moodyvideoeditor.data.EditorClip
import com.moody.moodyvideoeditor.data.KeyframeMap
import kotlin.math.abs

/**
 * Mirrors js/workspace/transformApplier.js + previewCanvas.js
 * Applies transform to Compose DrawScope + provides identity check.
 */

/**
 * Snapshot of all 10 transform props (post-keyframe sampling).
 */
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
     * Extract base transform from clip (works for video/image/text/sticker).
     */
    fun baseOf(clip: EditorClip): TransformValues {
        // Text clip: pull from textState
        if (clip.isTextClip && clip.textState != null) {
            val st = clip.textState
            return TransformValues(
                x = st.positionX, y = st.positionY,
                scale = st.scale, rotation = st.rotation,
                anchorX = st.anchorX, anchorY = st.anchorY
            )
        }
        // Sticker clip: pull from stickerState
        if (clip.isStickerClip && clip.stickerState != null) {
            val ss = clip.stickerState
            return TransformValues(
                x = ss.x, y = ss.y,
                scale = ss.scale, rotation = ss.rotation
            )
        }
        // Normal clip: from scale/rotation/offset/crop fields
        return TransformValues(
            x = 50f, y = 50f,  // offsetX/offsetY are deltas
            scale = clip.scale,
            rotation = clip.rotation,
            anchorX = 50f, anchorY = 50f,
            cropL = clip.cropL, cropR = clip.cropR,
            cropT = clip.cropT, cropB = clip.cropB
        )
    }

    /**
     * Live sample — for preview: given clip at time, return actual transform.
     * Mirrors previewCanvas.js resolveLiveTransform()
     */
    fun resolveLive(clip: EditorClip, timeSec: Float): TransformValues {
        val base = baseOf(clip)
        val kfs: KeyframeMap = clip.keyframes
        return if (KeyframeStore.hasAnyKeyframes(kfs)) {
            KeyframeStore.sampleAll(kfs, timeSec, base)
        } else {
            base
        }
    }

    /**
     * Apply transform to Compose DrawScope (Preview + Canvas drawing).
     */
    fun DrawScope.applyTransform(W: Float, H: Float, t: TransformValues) {
        if (t.isIdentity()) return

        withTransform({
            // 1) Pivot point (anchor)
            val originX = W * t.anchorX / 100f
            val originY = H * t.anchorY / 100f

            // 2) Position offset
            val offsetX = (t.x - 50f) / 100f * W
            val offsetY = (t.y - 50f) / 100f * H

            // 3) Apply order: translate → rotate → scale → translate back
            translate(originX + offsetX, originY + offsetY)
            if (t.rotation != 0f) rotate(t.rotation, Offset(originX, originY))
            if (t.scale != 100f) {
                val sc = t.scale / 100f
                scale(sc, sc, Offset(originX, originY))
            }
        }) {
            // children draw here (DrawScope.withTransform block)
        }
    }

    /**
     * Apply transform via translate/rotate/scale list — for graphicsLayer use.
     * Returns translation, scale, rotation to feed into Modifier.graphicsLayer.
     */
    data class GraphicsValues(
        val translationX: Float = 0f,
        val translationY: Float = 0f,
        val scaleX: Float = 1f,
        val scaleY: Float = 1f,
        val rotationZ: Float = 0f
    )

    fun asGraphics(W: Float, H: Float, t: TransformValues): GraphicsValues {
        if (t.isIdentity()) return GraphicsValues()
        val dx = (t.x - 50f) / 100f * W
        val dy = (t.y - 50f) / 100f * H
        return GraphicsValues(
            translationX = dx,
            translationY = dy,
            scaleX = t.scale / 100f,
            scaleY = t.scale / 100f,
            rotationZ = t.rotation
        )
    }
}