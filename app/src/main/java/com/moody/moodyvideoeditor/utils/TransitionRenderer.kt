package com.moody.moodyvideoeditor.utils

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import com.moody.moodyvideoeditor.data.EditorClip
import kotlin.math.sin
data class Transform2D(
    val tx: Float = 0f,
    val ty: Float = 0f,
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
    val rotZ: Float = 0f,
    val alpha: Float = 1f
)

object TransitionRenderer {

    @Composable
    fun Render(
        clip: EditorClip,
        bitmap: android.graphics.Bitmap,
        transitionKey: String,
        progress: Float,
        w: Float, // Pixels
        h: Float  // Pixels
    ) {
        val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }
        val p = progress.coerceIn(0f, 1f)
        val k = transitionKey.lowercase()

        // Outgoing Clip Motion logic
        when {
            // Slide Out
            k.contains("slideleft") || k.contains("pushleft") || k.contains("panleft") || k.contains("wipeleft") || k.contains("vector") ->
                SimpleImage(imageBitmap, clip, w, h, translationX = -p * w)

            k.contains("slideright") || k.contains("pushright") || k.contains("panright") || k.contains("wiperight") ->
                SimpleImage(imageBitmap, clip, w, h, translationX = p * w)

            k.contains("slideup") || k.contains("pushup") || k.contains("tiltup") || k.contains("wipeup") || k.contains("rise") ->
                SimpleImage(imageBitmap, clip, w, h, translationY = -p * h)

            k.contains("slidedown") || k.contains("pushdown") || k.contains("tiltdown") || k.contains("wipedown") || k.contains("drop") ->
                SimpleImage(imageBitmap, clip, w, h, translationY = p * h)

            // Diagonal
            k.contains("diagtl") -> SimpleImage(imageBitmap, clip, w, h, translationX = -p * w, translationY = -p * h)
            k.contains("diagtr") || k.contains("diagonal") -> SimpleImage(imageBitmap, clip, w, h, translationX = p * w, translationY = -p * h)

            // Zoom Out / In
            k.contains("zoomin") || k.contains("zoomimpact") || k.contains("snapback") ->
                SimpleImage(imageBitmap, clip, w, h, scaleX = 1f + p * 1.5f, scaleY = 1f + p * 1.5f, alpha = 1f - p)

            k.contains("zoomout") || k.contains("pushoutbounce") || k.contains("boxzoom") ->
                SimpleImage(imageBitmap, clip, w, h, scaleX = 1f - p * 0.5f, scaleY = 1f - p * 0.5f, alpha = 1f - p)

            k.contains("crosszoom") || k.contains("liquidblob") ->
                SimpleImage(imageBitmap, clip, w, h, scaleX = 1f + p * 1.5f, scaleY = 1f + p * 1.5f, alpha = 1f - p)

            // Spin / Rotate
            k.contains("spincw") || k.contains("spinin") || k.contains("twirlcw") || k.contains("clockwise") ->
                SimpleImage(imageBitmap, clip, w, h, rotationZ = -p * 360f, scaleX = 1f - p * 0.5f, scaleY = 1f - p * 0.5f, alpha = 1f - p)

            k.contains("spinccw") || k.contains("spinout") || k.contains("twirlccw") || k.contains("counter") || k.contains("swirl") ->
                SimpleImage(imageBitmap, clip, w, h, rotationZ = p * 360f, scaleX = 1f - p * 0.5f, scaleY = 1f - p * 0.5f, alpha = 1f - p)

            // Glitch / Shake
            k.contains("glitch") || k.contains("rgb") || k.contains("shake") || k.contains("jiggle") -> {
                val offset = sin((p * 60f).toDouble()).toFloat() * 30f
                SimpleImage(imageBitmap, clip, w, h, translationX = offset, alpha = 1f - p)
            }

            // Shapes / Matte (Outgoing clip fades out)
            k.contains("circle") || k.contains("star") || k.contains("clock") || k.contains("wipe") || k.contains("iris") || k.contains("matrix") ->
                SimpleImage(imageBitmap, clip, w, h, alpha = 1f - p)

            // Light Leaks / Burns (Outgoing fades to white/black)
            k.contains("flash") || k.contains("leak") || k.contains("burn") ->
                SimpleImage(imageBitmap, clip, w, h, alpha = 1f - p)

            // Default
            else -> SimpleImage(imageBitmap, clip, w, h, alpha = 1f - p)
        }
    }

    @Composable
    private fun SimpleImage(
        imageBitmap: androidx.compose.ui.graphics.ImageBitmap,
        clip: EditorClip,
        w: Float,
        h: Float,
        translationX: Float = 0f,
        translationY: Float = 0f,
        scaleX: Float = 1f,
        scaleY: Float = 1f,
        rotationZ: Float = 0f,
        alpha: Float = 1f
    ) {
        val base = remember(clip) { TransformApplier.baseOf(clip) }

        Image(
            bitmap = imageBitmap,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val cropSx = 1f / (1f - base.cropL - base.cropR).coerceAtLeast(0.05f)
                    val cropSy = 1f / (1f - base.cropT - base.cropB).coerceAtLeast(0.05f)
                    val cropTx = -(base.cropL - base.cropR) / 2f * w
                    val cropTy = -(base.cropT - base.cropB) / 2f * h
                    val posTx = (base.x - 50f) / 100f * w
                    val posTy = (base.y - 50f) / 100f * h

                    this.translationX = posTx + cropTx + translationX
                    this.translationY = posTy + cropTy + translationY
                    this.scaleX = (base.scale / 100f) * cropSx * scaleX
                    this.scaleY = (base.scale / 100f) * cropSy * scaleY
                    this.rotationZ = base.rotation + rotationZ
                    this.alpha = alpha.coerceIn(0f, 1f)
                }
        )
    }

    fun getIncomingTransform(key: String, p: Float, w: Float, h: Float): Transform2D {
        val pr = p.coerceIn(0f, 1f)
        val k = key.lowercase()

        return when {
            // Fades, Light Leaks, Glitches -> Clip static rehti hai, overlay kaam karta hai
            k.contains("fade") || k.contains("dissolve") || k.contains("flash") || k.contains("leak") || k.contains("glitch") || k.contains("burn") ->
                Transform2D()

            // Slides
            k.contains("slideleft") || k.contains("pushleft") || k.contains("panleft") || k.contains("wipeleft") ->
                Transform2D(tx = (1f - pr) * w)
            k.contains("slideright") || k.contains("pushright") || k.contains("panright") || k.contains("wiperight") ->
                Transform2D(tx = -(1f - pr) * w)
            k.contains("slideup") || k.contains("pushup") || k.contains("tiltup") || k.contains("wipeup") ->
                Transform2D(ty = (1f - pr) * h)
            k.contains("slidedown") || k.contains("pushdown") || k.contains("tiltdown") || k.contains("wipedown") ->
                Transform2D(ty = -(1f - pr) * h)

            // Diagonal
            k.contains("diagtl") -> Transform2D(tx = (1f - pr) * w, ty = (1f - pr) * h)
            k.contains("diagtr") || k.contains("diagonal") -> Transform2D(tx = -(1f - pr) * w, ty = (1f - pr) * h)

            // Zooms
            k.contains("zoomin") || k.contains("zoomimpact") || k.contains("snapback") || k.contains("crosszoom") ->
                Transform2D(scaleX = 0.5f + pr * 0.5f, scaleY = 0.5f + pr * 0.5f)
            k.contains("zoomout") || k.contains("pushoutbounce") ->
                Transform2D(scaleX = 1.5f - pr * 0.5f, scaleY = 1.5f - pr * 0.5f)

            // Circles & Shapes (Incoming scales up inside the mask)
            k.contains("circle") || k.contains("star") || k.contains("heart") || k.contains("diamond") ->
                Transform2D(scaleX = 0.3f + pr * 0.7f, scaleY = 0.3f + pr * 0.7f)

            // Spin
            k.contains("spincw") || k.contains("spinin") || k.contains("twirlcw") ->
                Transform2D(rotZ = -360f * (1f - pr), scaleX = 0.5f + pr * 0.5f, scaleY = 0.5f + pr * 0.5f)
            k.contains("spinccw") || k.contains("spinout") || k.contains("swirl") ->
                Transform2D(rotZ = 360f * (1f - pr), scaleX = 0.5f + pr * 0.5f, scaleY = 0.5f + pr * 0.5f)

            // Default
            else -> Transform2D()
        }
    }
}