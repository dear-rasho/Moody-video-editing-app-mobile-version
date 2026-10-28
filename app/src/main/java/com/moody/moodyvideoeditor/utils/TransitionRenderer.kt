package com.moody.moodyvideoeditor.utils

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
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
        val k = if (transitionKey.startsWith("xfade-")) {
            transitionKey.removePrefix("xfade-").substringBeforeLast("-")
        } else {
            transitionKey.lowercase()
        }

        val eased = p * p * (3f - 2f * p)
        val outgoing = when {
            k.startsWith("style-cube-left") || k.startsWith("style-card-flip-left") ||
                    k.startsWith("style-orbit-left") ->
                OutgoingMotion(
                    translationX = -w * 0.18f * eased,
                    rotationY = -72f * eased,
                    scaleX = 1f - 0.18f * eased,
                    alpha = 1f - 0.25f * eased
                )
            k.startsWith("style-cube-right") || k.startsWith("style-card-flip-right") ||
                    k.startsWith("style-orbit-right") ->
                OutgoingMotion(
                    translationX = w * 0.18f * eased,
                    rotationY = 72f * eased,
                    scaleX = 1f - 0.18f * eased,
                    alpha = 1f - 0.25f * eased
                )
            k.startsWith("style-cube-up") || k.startsWith("style-card-flip-up") ->
                OutgoingMotion(
                    translationY = -h * 0.16f * eased,
                    rotationX = 68f * eased,
                    scaleY = 1f - 0.16f * eased,
                    alpha = 1f - 0.2f * eased
                )
            k.startsWith("style-cube-down") || k.startsWith("style-card-flip-down") ->
                OutgoingMotion(
                    translationY = h * 0.16f * eased,
                    rotationX = -68f * eased,
                    scaleY = 1f - 0.16f * eased,
                    alpha = 1f - 0.2f * eased
                )
            k.startsWith("style-flip-x") ->
                OutgoingMotion(rotationX = 85f * eased, scaleY = 1f - 0.28f * eased, alpha = 1f - 0.35f * eased)
            k.startsWith("style-flip-y") ->
                OutgoingMotion(rotationY = -85f * eased, scaleX = 1f - 0.28f * eased, alpha = 1f - 0.35f * eased)
            k.startsWith("style-tilt-left") ->
                OutgoingMotion(rotationY = -26f * eased, rotationZ = -8f * eased, translationX = -w * 0.12f * eased)
            k.startsWith("style-tilt-right") ->
                OutgoingMotion(rotationY = 26f * eased, rotationZ = 8f * eased, translationX = w * 0.12f * eased)
            k.startsWith("style-hinge-left") ->
                OutgoingMotion(rotationZ = -55f * eased, translationX = -w * 0.28f * eased, alpha = 1f - eased)
            k.startsWith("style-hinge-right") ->
                OutgoingMotion(rotationZ = 55f * eased, translationX = w * 0.28f * eased, alpha = 1f - eased)
            k.startsWith("style-whip-left") || k.startsWith("style-snap-left") ->
                OutgoingMotion(translationX = -w * eased, alpha = 1f - eased)
            k.startsWith("style-whip-right") || k.startsWith("style-snap-right") ->
                OutgoingMotion(translationX = w * eased, alpha = 1f - eased)
            k.startsWith("style-whip-up") ->
                OutgoingMotion(translationY = -h * eased, alpha = 1f - eased)
            k.startsWith("style-whip-down") ->
                OutgoingMotion(translationY = h * eased, alpha = 1f - eased)
            k.startsWith("style-shake") || k.startsWith("style-camera-shake") ||
                    k.startsWith("style-impact-shake") || k.startsWith("style-micro-jitter") ||
                    k.startsWith("style-heavy-jitter") || k.startsWith("style-roll-shake") ||
                    k.startsWith("style-pulse-shake") || k.startsWith("style-bounce-shake") ||
                    k.startsWith("style-vibrate") -> {
                val decay = 1f - eased
                val amplitude = when {
                    k.contains("micro") || k.contains("vibrate") -> 0.012f
                    k.contains("heavy") || k.contains("impact") -> 0.075f
                    else -> 0.04f
                }
                val wave = kotlin.math.sin(p * 42f) * decay * amplitude
                val verticalWave = kotlin.math.cos(p * 34f) * decay * amplitude
                OutgoingMotion(
                    translationX = if (k.contains("shake-y")) 0f else wave * w,
                    translationY = if (k.contains("shake-x")) 0f else verticalWave * h,
                    rotationZ = if (k.contains("roll")) wave * 80f else wave * 28f,
                    scaleX = if (k.contains("pulse") || k.contains("bounce")) 1f + amplitude * decay else 1f,
                    scaleY = if (k.contains("pulse") || k.contains("bounce")) 1f + amplitude * decay else 1f,
                    alpha = 1f - eased * 0.35f
                )
            }
            k.startsWith("style-blur") || k.startsWith("style-soft-blur") ||
                    k.startsWith("style-strong-blur") || k.startsWith("style-radial-blur") ||
                    k.startsWith("style-zoom-blur") || k.startsWith("style-motion-blur") ||
                    k.startsWith("style-dream-blur") || k.startsWith("style-focus-pull") ||
                    k.startsWith("style-spin-blur") || k.startsWith("style-bloom-blur") ||
                    k.startsWith("style-soft-focus") || k.startsWith("style-defocus") ->
                OutgoingMotion(
                    scaleX = if (k.contains("zoom")) 1f + eased * 0.14f else 1f,
                    scaleY = if (k.contains("zoom")) 1f + eased * 0.14f else 1f,
                    alpha = 1f - eased,
                    blurRadius = when {
                        k.contains("strong") || k.contains("radial") -> 42f * p
                        k.contains("soft") || k.contains("dream") -> 14f * p
                        else -> 28f * p
                    }
                )
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
            k == "circleopen" || k == "circleclose" || k == "circlecrop" ||
                    k == "rectcrop" || k == "radial" ||
                    k.startsWith("vert") || k.startsWith("horz") ||
                    k.startsWith("wipe") || k.startsWith("diag") ||
                    k.startsWith("cover") || k.startsWith("reveal") ->
                OutgoingMotion(alpha = 1f, clipKey = k, progress = eased)
            k == "pixelize" ->
                OutgoingMotion(scaleX = 1f + eased * 0.08f, scaleY = 1f + eased * 0.08f, alpha = 1f - eased)
            k == "squeezeh" ->
                OutgoingMotion(scaleX = 1f + eased * 0.4f, alpha = 1f - eased)
            k == "squeezev" ->
                OutgoingMotion(scaleY = 1f + eased * 0.4f, alpha = 1f - eased)
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
                rotationZ = outgoing.rotationZ,
                rotationX = outgoing.rotationX,
                rotationY = outgoing.rotationY,
                alpha = outgoing.alpha,
                clipKey = outgoing.clipKey,
                clipProgress = outgoing.progress,
                blurRadius = outgoing.blurRadius.takeIf { it > 0f }
                    ?: if (k == "blur") 32f * p else 0f
            )

            if (k.startsWith("style-leak-") || k.startsWith("style-flare-") ||
                k.startsWith("style-film-burn") || k.startsWith("style-light-flash") ||
                k.startsWith("style-prism-flare") || k.startsWith("style-sun-flare") ||
                k.startsWith("style-golden-hour") || k.startsWith("style-neon-leak") ||
                k.startsWith("style-soft-leak")
            ) {
                LightLeakOverlay(k, p)
            }

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
        val rotationZ: Float = 0f,
        val rotationX: Float = 0f,
        val rotationY: Float = 0f,
        val blurRadius: Float = 0f,
        val clipKey: String? = null,
        val progress: Float = 0f
    )

    @Composable
    private fun LightLeakOverlay(key: String, progress: Float) {
        val color = when {
            key.contains("blue") || key.contains("teal") -> Color(0xFF42D9FF)
            key.contains("pink") || key.contains("neon") -> Color(0xFFFF3F9B)
            key.contains("violet") || key.contains("prism") -> Color(0xFFB36BFF)
            key.contains("orange") || key.contains("amber") ||
                    key.contains("golden") || key.contains("sun") ||
                    key.contains("burn") -> Color(0xFFFFA23A)
            else -> Color(0xFFFFE76A)
        }
        val pulse = (kotlin.math.sin(progress * Math.PI).toFloat() * 0.72f)
            .coerceIn(0f, 0.72f)
        val sweep = when {
            key.contains("left") -> progress
            key.contains("right") -> 1f - progress
            else -> 0.25f + progress * 0.5f
        }

        Canvas(Modifier.fillMaxSize()) {
            val center = Offset(size.width * sweep, size.height * (0.25f + 0.5f * progress))
            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(Color.Transparent, color.copy(alpha = pulse), Color.Transparent),
                    start = Offset(size.width * (sweep - 0.55f), 0f),
                    end = Offset(size.width * (sweep + 0.55f), size.height)
                )
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(color.copy(alpha = pulse), Color.Transparent),
                    center = center,
                    radius = maxOf(size.width, size.height) * 0.72f
                ),
                radius = maxOf(size.width, size.height) * 0.72f,
                center = center
            )
            if (key.contains("prism") || key.contains("rainbow")) {
                drawRect(
                    brush = Brush.linearGradient(
                        listOf(
                            Color.Red.copy(alpha = pulse * 0.25f),
                            Color.Yellow.copy(alpha = pulse * 0.25f),
                            Color.Cyan.copy(alpha = pulse * 0.25f),
                            Color.Magenta.copy(alpha = pulse * 0.25f)
                        )
                    )
                )
            }
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
        rotationX: Float = 0f,
        rotationY: Float = 0f,
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
                    this.rotationX = rotationX
                    this.rotationY = rotationY
                    this.cameraDistance = maxOf(w, h) * 2f
                    this.alpha = alpha.coerceIn(0f, 1f)
                }
                .then(if (blurRadius > 0f) Modifier.blur(blurRadius.dp) else Modifier)
                .drawWithContent {
                    val key = clipKey
                    if (key == null) {
                        drawContent()
                    } else {
                        val progress = clipProgress.coerceIn(0f, 1f)
                        val shapeMask = shapeTransitionMask(
                            key,
                            size.width,
                            size.height,
                            progress
                        )
                        when {
                            shapeMask != null ->
                                clipPath(shapeMask) { this@drawWithContent.drawContent() }
                            key == "wipeleft" || key == "coverleft" || key == "revealleft" ->
                                clipRect(right = size.width * (1f - progress)) {
                                    this@drawWithContent.drawContent()
                                }
                            key == "wiperight" || key == "coverright" || key == "revealright" ->
                                clipRect(left = size.width * progress) {
                                    this@drawWithContent.drawContent()
                                }
                            key == "wipeup" || key == "coverup" || key == "revealup" ->
                                clipRect(bottom = size.height * (1f - progress)) {
                                    this@drawWithContent.drawContent()
                                }
                            key == "wipedown" || key == "coverdown" || key == "revealdown" ->
                                clipRect(top = size.height * progress) {
                                    this@drawWithContent.drawContent()
                                }
                            key.startsWith("diag") || key.startsWith("wipe") -> {
                                val path = diagonalWipePath(key, size.width, size.height, progress)
                                clipPath(path) { this@drawWithContent.drawContent() }
                            }
                            else -> drawContent()
                        }
                    }
                }
        )
    }

    private fun shapeTransitionMask(
        key: String,
        width: Float,
        height: Float,
        progress: Float
    ): Path? {
        val full = androidx.compose.ui.geometry.Rect(0f, 0f, width, height)
        val centerX = width / 2f
        val centerY = height / 2f
        return when (key) {
            "circleopen", "circlecrop", "radial" -> {
                val radius = sqrt(width * width + height * height) * 0.5f * progress
                Path().apply {
                    fillType = PathFillType.EvenOdd
                    addRect(full)
                    addOval(
                        androidx.compose.ui.geometry.Rect(
                            centerX - radius, centerY - radius,
                            centerX + radius, centerY + radius
                        )
                    )
                }
            }
            "circleclose" -> {
                val radius = sqrt(width * width + height * height) * 0.5f * (1f - progress)
                Path().apply {
                    addOval(
                        androidx.compose.ui.geometry.Rect(
                            centerX - radius, centerY - radius,
                            centerX + radius, centerY + radius
                        )
                    )
                }
            }
            "rectcrop", "vertopen", "vertclose", "horzopen", "horzclose" -> {
                val openingWidth = when (key) {
                    "vertopen" -> width * progress
                    "vertclose" -> width * (1f - progress)
                    "horzopen" -> width
                    "horzclose" -> width
                    else -> maxOf(width, height) * progress
                }
                val openingHeight = when (key) {
                    "vertopen", "vertclose" -> height
                    "horzopen" -> height * progress
                    "horzclose" -> height * (1f - progress)
                    else -> maxOf(width, height) * progress
                }
                val opening = androidx.compose.ui.geometry.Rect(
                    centerX - openingWidth / 2f,
                    centerY - openingHeight / 2f,
                    centerX + openingWidth / 2f,
                    centerY + openingHeight / 2f
                )
                Path().apply {
                    if (key == "vertopen" || key == "horzopen" || key == "rectcrop") {
                        fillType = PathFillType.EvenOdd
                        addRect(full)
                        addRect(opening)
                    } else {
                        addRect(opening)
                    }
                }
            }
            else -> null
        }
    }

    private fun diagonalWipePath(key: String, width: Float, height: Float, progress: Float): Path {
        val p = progress.coerceIn(0f, 1f)
        val path = Path()
        when (key) {
            "diagtl", "wipetl" -> {
                path.moveTo(width * p, 0f)
                path.lineTo(width, 0f)
                path.lineTo(width, height)
                path.lineTo(0f, height * (1f - p))
            }
            "diagtr", "wipetr" -> {
                path.moveTo(0f, 0f)
                path.lineTo(width * (1f - p), 0f)
                path.lineTo(width, height * p)
                path.lineTo(width, height)
                path.lineTo(0f, height)
            }
            "diagbl", "wipebl" -> {
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