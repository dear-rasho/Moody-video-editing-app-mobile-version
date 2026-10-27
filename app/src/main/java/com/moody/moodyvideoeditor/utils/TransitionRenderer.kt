package com.moody.moodyvideoeditor.utils

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.Canvas
import com.moody.moodyvideoeditor.data.EditorClip
import androidx.compose.ui.unit.dp
import kotlin.math.sqrt
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

        val eased = p * p * (3f - 2f * p)
        val outgoing = when {
            k.startsWith("slideleft") || k.startsWith("smoothleft") ->
                OutgoingMotion(translationX = -eased * w)
            k.startsWith("slideright") || k.startsWith("smoothright") ->
                OutgoingMotion(translationX = eased * w)
            k.startsWith("slideup") || k.startsWith("smoothup") ->
                OutgoingMotion(translationY = -eased * h)
            k.startsWith("slidedown") || k.startsWith("smoothdown") ->
                OutgoingMotion(translationY = eased * h)
            k == "diagtl" -> OutgoingMotion(translationX = -eased * w, translationY = -eased * h)
            k == "diagtr" -> OutgoingMotion(translationX = eased * w, translationY = -eased * h)
            k == "diagbl" -> OutgoingMotion(translationX = -eased * w, translationY = eased * h)
            k == "diagbr" -> OutgoingMotion(translationX = eased * w, translationY = eased * h)
            k == "zoomin" -> OutgoingMotion(
                scaleX = 1f + eased * 0.5f,
                scaleY = 1f + eased * 0.5f,
                alpha = 1f - eased
            )
            k == "circleopen" || k == "circleclose" ||
                    k.startsWith("wipe") || k.startsWith("diag") ->
                OutgoingMotion(alpha = 1f, clipKey = k, progress = eased)
            else -> OutgoingMotion(alpha = 1f - eased)
        }

        Box(Modifier.fillMaxSize()) {
            SimpleImage(
                imageBitmap = imageBitmap,
                clip = clip,
                w = w,
                h = h,
                translationX = outgoing.translationX,
                translationY = outgoing.translationY,
                scaleX = outgoing.scaleX,
                scaleY = outgoing.scaleY,
                alpha = outgoing.alpha,
                clipKey = outgoing.clipKey,
                clipProgress = outgoing.progress,
                blurRadius = if (k == "blur") 32f * p else 0f
            )

            val flashColor = when (k) {
                "fadewhite", "whiteflash" -> Color.White
                "fadeblack", "blinkfade" -> Color.Black
                "dreamybloom", "glowdissolve", "softglaze" -> Color(0xFFFFE6B0)
                else -> null
            }
            if (flashColor != null) {
                val pulse = (1f - kotlin.math.abs(2f * p - 1f)).coerceIn(0f, 1f)
                val strength = when (k) {
                    "fadewhite", "fadeblack" -> 0.78f
                    "blinkfade" -> if ((p * 8f).toInt() % 2 == 0) 0.75f else 0.05f
                    else -> 0.45f
                }
                Canvas(Modifier.fillMaxSize()) {
                    drawRect(flashColor.copy(alpha = pulse * strength))
                }
            }
        }
    }

    private data class OutgoingMotion(
        val translationX: Float = 0f,
        val translationY: Float = 0f,
        val scaleX: Float = 1f,
        val scaleY: Float = 1f,
        val alpha: Float = 1f,
        val clipKey: String? = null,
        val progress: Float = 0f
    )

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
        alpha: Float = 1f,
        clipKey: String? = null,
        clipProgress: Float = 0f,
        blurRadius: Float = 0f
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
                .then(if (blurRadius > 0f) Modifier.blur(blurRadius.dp) else Modifier)
                .drawWithContent {
                    val key = clipKey
                    if (key == null) {
                        drawContent()
                    } else {
                        val progress = clipProgress.coerceIn(0f, 1f)
                        when {
                            key == "circleopen" -> {
                                val radius = sqrt(size.width * size.width + size.height * size.height) *
                                        0.5f * progress
                                val path = Path().apply {
                                    fillType = PathFillType.EvenOdd
                                    addRect(androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.height))
                                    addOval(
                                        androidx.compose.ui.geometry.Rect(
                                            size.width / 2f - radius,
                                            size.height / 2f - radius,
                                            size.width / 2f + radius,
                                            size.height / 2f + radius
                                        )
                                    )
                                }
                                clipPath(path) { this@drawWithContent.drawContent() }
                            }
                            key == "circleclose" -> {
                                val radius = sqrt(size.width * size.width + size.height * size.height) *
                                        0.5f * (1f - progress)
                                val path = Path().apply {
                                    addOval(
                                        androidx.compose.ui.geometry.Rect(
                                            size.width / 2f - radius,
                                            size.height / 2f - radius,
                                            size.width / 2f + radius,
                                            size.height / 2f + radius
                                        )
                                    )
                                }
                                clipPath(path) { this@drawWithContent.drawContent() }
                            }
                            key == "wipeleft" ->
                                clipRect(left = size.width * progress) {
                                    this@drawWithContent.drawContent()
                                }
                            key == "wiperight" ->
                                clipRect(right = size.width * (1f - progress)) {
                                    this@drawWithContent.drawContent()
                                }
                            key == "wipeup" ->
                                clipRect(top = size.height * progress) {
                                    this@drawWithContent.drawContent()
                                }
                            key == "wipedown" ->
                                clipRect(bottom = size.height * (1f - progress)) {
                                    this@drawWithContent.drawContent()
                                }
                            key.startsWith("diag") -> {
                                val path = diagonalWipePath(key, size.width, size.height, progress)
                                clipPath(path) { this@drawWithContent.drawContent() }
                            }
                            else -> drawContent()
                        }
                    }
                }
        )
    }

    private fun diagonalWipePath(key: String, width: Float, height: Float, progress: Float): Path {
        val p = progress.coerceIn(0f, 1f)
        val path = Path()
        when (key) {
            "diagtl" -> {
                path.moveTo(width * p, 0f)
                path.lineTo(width, 0f)
                path.lineTo(width, height)
                path.lineTo(0f, height * (1f - p))
            }
            "diagtr" -> {
                path.moveTo(0f, 0f)
                path.lineTo(width * (1f - p), 0f)
                path.lineTo(width, height * p)
                path.lineTo(width, height)
                path.lineTo(0f, height)
            }
            "diagbl" -> {
                path.moveTo(0f, 0f)
                path.lineTo(width, 0f)
                path.lineTo(width * (1f - p), height)
                path.lineTo(0f, height * p)
            }
            else -> {
                path.moveTo(0f, 0f)
                path.lineTo(width, 0f)
                path.lineTo(width, height * (1f - p))
                path.lineTo(width * (1f - p), height)
                path.lineTo(0f, height)
            }
        }
        path.close()
        return path
    }

    fun getIncomingTransform(key: String, p: Float, w: Float, h: Float): Transform2D {
        val k = key.lowercase()
        val rawProgress = p.coerceIn(0f, 1f)
        val pr = if (k.startsWith("smooth")) {
            rawProgress * rawProgress * (3f - 2f * rawProgress)
        } else {
            rawProgress
        }

        return when {
            // Fades, Light Leaks, Glitches -> Clip static rehti hai, overlay kaam karta hai
            k.contains("fade") || k.contains("dissolve") || k.contains("flash") ||
                    k.contains("leak") || k.contains("glitch") || k.contains("burn") ||
                    k == "blur" || k.contains("bloom") || k.contains("glow") ||
                    k == "blinkfade" || k == "softglaze" ->
                Transform2D()

            // Slides
            k.startsWith("slideleft") || k.startsWith("smoothleft") ->
                Transform2D(tx = (1f - pr) * w)
            k.startsWith("slideright") || k.startsWith("smoothright") ->
                Transform2D(tx = -(1f - pr) * w)
            k.startsWith("slideup") || k.startsWith("smoothup") ->
                Transform2D(ty = (1f - pr) * h)
            k.startsWith("slidedown") || k.startsWith("smoothdown") ->
                Transform2D(ty = -(1f - pr) * h)

            // Diagonal
            k == "diagtl" -> Transform2D(tx = (1f - pr) * w, ty = (1f - pr) * h)
            k == "diagtr" -> Transform2D(tx = -(1f - pr) * w, ty = (1f - pr) * h)
            k == "diagbl" -> Transform2D(tx = (1f - pr) * w, ty = -(1f - pr) * h)
            k == "diagbr" -> Transform2D(tx = -(1f - pr) * w, ty = -(1f - pr) * h)
            k.startsWith("wipe") -> Transform2D()

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