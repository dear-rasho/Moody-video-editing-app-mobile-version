package com.moody.moodyvideoeditor.utils

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale

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
        bitmap: android.graphics.Bitmap,
        transitionKey: String,
        progress: Float
    ) {
        val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }
        val p = progress.coerceIn(0f, 1f)
        when (val k = transitionKey.lowercase()) {


            //  FADES

            "fade", "dissolve", "blur",
            "softglaze", "dreamybloom", "glowdissolve",
            "watercolorbleed", "oilpainting", "datamoshing" -> {
                SimpleImage(imageBitmap, alpha = 1f - p)
            }

            "fadeblack", "blinkfade", "vintageslide" -> {
                SimpleImage(
                    imageBitmap,
                    alpha = if (p < 0.5f) 1f - (p * 2f) else 0f
                )
            }

            "fadewhite", "whiteflash", "chromaticflash", "flashjolt",
            "lightleakburst", "neonflare", "lensflarecut", "vignetteburn",
            "filmburn", "chalksketch" -> {
                SimpleImage(
                    imageBitmap,
                    alpha = when {
                        p < 0.15f -> 1f
                        p < 0.30f -> 1f - (p - 0.15f) / 0.15f
                        else -> 0f
                    }
                )
            }

            "fadegrays", "signalloss", "noiseintercept" -> {
                SimpleImage(imageBitmap, alpha = 1f - p)
            }


            //  SLIDE FAMILY

            "slideleft", "pushleft", "panleft", "wipeleft",
            "whip pan", "wippan", "velocityshake", "smoothleft",
            "vectorShift".lowercase(), "vector shift", "vectorshift" -> {
                SimpleImage(
                    imageBitmap,
                    translationX = -p * 1200f
                )
            }

            "slideright", "pushright", "panright", "wiperight",
            "smoothright", "sunbeamsweep" -> {
                SimpleImage(
                    imageBitmap,
                    translationX = p * 1200f
                )
            }

            "slideup", "pushup", "tiltup", "wipeup",
            "riseup", "smoothup", "verticalbounce" -> {
                SimpleImage(
                    imageBitmap,
                    translationY = -p * 1200f
                )
            }

            "slidedown", "pushdown", "tiltdown", "wipedown",
            "dropdown", "smoothdown", "epicstrike", "staticmelt" -> {
                SimpleImage(
                    imageBitmap,
                    translationY = p * 1200f
                )
            }

            // Diagonal
            "diagonalswift", "diagtl", "diagtr", "diagbl", "diagbr",
            "diagonalslice", "trianglesweep", "burningpaper" -> {
                SimpleImage(
                    imageBitmap,
                    translationX = p * 800f,
                    translationY = p * 800f
                )
            }


            //  ZOOM FAMILY

            "zoomin", "zoominx", "zoomimpact", "snapback",
            "gaussianzoom", "pullinshake" -> {
                SimpleImage(
                    imageBitmap,
                    scaleX = 1f + p * 1.5f,
                    scaleY = 1f + p * 1.5f,
                    alpha = 1f - p
                )
            }

            "zoomout", "pushoutbounce", "boxzoom" -> {
                SimpleImage(
                    imageBitmap,
                    scaleX = 1f - p * 0.7f,
                    scaleY = 1f - p * 0.7f,
                    alpha = 1f - p
                )
            }

            "crosszoom", "liquidblob", "jigsawmask" -> {
                SimpleImage(
                    imageBitmap,
                    scaleX = 1f + p * 1.2f,
                    scaleY = 1f + p * 1.2f,
                    alpha = 1f - p
                )
            }


            //  WIPE / SHAPES

            "circleopen", "circleclose", "circleMask".lowercase(),
            "circlemask", "starburst", "halopulse", "inksplash",
            "spiralwipe", "hearts" -> {
                val scale = if (k == "circleopen") 1f + p * 0.8f
                else 1.2f - p * 0.2f
                SimpleImage(
                    imageBitmap,
                    scaleX = scale,
                    scaleY = scale,
                    alpha = 1f - p
                )
            }

            "irisbox", "clockwipe", "radial", "halo",
            "radialzoomblur", "basswave", "turbulentswivel",
            "clockwipe".lowercase() -> {
                SimpleImage(
                    imageBitmap,
                    scaleX = 1f + p * 0.6f,
                    scaleY = 1f + p * 0.6f,
                    rotationZ = p * 45f,
                    alpha = 1f - p
                )
            }

            "wipetl", "wipetr", "wipebl", "wipebr",
            "crosshatch", "zigzagwipe", "mosaicSwitch".lowercase(),
            "mosaicswitch" -> {
                SimpleImage(
                    imageBitmap,
                    translationX = -p * 400f,
                    translationY = -p * 400f,
                    alpha = 1f - p
                )
            }

            "horzopen", "horzclose", "mirrorsplit", "mirror" -> {
                SimpleImage(
                    imageBitmap,
                    scaleY = 1f - p * 0.9f,
                    alpha = 1f - p
                )
            }

            "vertopen", "vertclose", "matrixcode", "venetianblinds" -> {
                SimpleImage(
                    imageBitmap,
                    scaleX = 1f - p * 0.9f,
                    alpha = 1f - p
                )
            }

            "hlslice", "hrslice", "vuslice", "vdslice",
            "horizontalscan", "digitalwave", "interlacedcut",
            "paperturn", "pageturn", "lineardraw", "linearwipe" -> {
                SimpleImage(
                    imageBitmap,
                    translationX = if (k.contains("l")) -p * 600f else p * 600f,
                    alpha = 1f - p
                )
            }

            "pixelize", "halftonedissolve", "griddissolve",
            "hexagonmatrix", "mosaic" -> {
                // Pixelate → simple zoom + fade
                SimpleImage(
                    imageBitmap,
                    scaleX = 1f + p * 0.15f,
                    scaleY = 1f + p * 0.15f,
                    alpha = 1f - p
                )
            }

            "rectcrop", "coverleft", "coverright",
            "slidedoor", "liquidblob" -> {
                SimpleImage(
                    imageBitmap,
                    alpha = 1f - p
                )
            }


            //  SPIN / ROTATE

            "spincw", "spinccw", "swirl",
            "spinIn".lowercase(), "spinin",
            "spinOut".lowercase(), "spinout",
            "twirlcw", "twirlccw", "cameraroll",
            "clockwise twirl", "counter twirl" -> {
                val rot = when (k) {
                    "spinccw", "twirlccw", "counter twirl" -> -p * 360f
                    else -> p * 360f
                }
                SimpleImage(
                    imageBitmap,
                    rotationZ = rot,
                    scaleX = 1f - p * 0.5f,
                    scaleY = 1f - p * 0.5f,
                    alpha = 1f - p
                )
            }


            //  GLITCH / DISTORTION

            "glitch", "rgbsplit", "rgbshift", "glitchshake", "vcrDistortion".lowercase(),
            "vcrdistortion", "glitchblur", "glitchpaint",
            "hblur", "anaglyphslide", "bitcrushed", "prismblur",
            "gaussianBlur".lowercase() -> {
                val offset = kotlin.math.sin((p * 40f).toDouble()).toFloat() * 15f
                SimpleImage(
                    imageBitmap,
                    translationX = offset,
                    alpha = 1f - p
                )
            }

            // Shake / jitter family (action)
            "horizontajiggle", "horizontaljiggle", "verticaljiggle",
            "tremorcut", "glitchShake".lowercase(),
            "wobbleslide", "chaosdrift", "rumbleDissolve".lowercase(),
            "rumbledissolve", "shake" -> {
                val offset = kotlin.math.sin((p * 60f).toDouble()).toFloat() * 20f
                SimpleImage(
                    imageBitmap,
                    translationX = offset,
                    translationY = -offset,
                    alpha = 1f - p * 0.9f
                )
            }


            //  ARTISTIC / SPECIAL

            "glassthatter", "smokescreen", "smokedissolve",
            "comicflip", "papertear" -> {
                SimpleImage(
                    imageBitmap,
                    scaleX = 1f - p * 0.3f,
                    scaleY = 1f - p * 0.3f,
                    rotationZ = p * 15f,
                    alpha = 1f - p
                )
            }


            //  DEFAULT — fade out

            else -> {
                SimpleImage(imageBitmap, alpha = 1f - p)
            }
        }
    }

    @Composable
    private fun SimpleImage(
        imageBitmap: androidx.compose.ui.graphics.ImageBitmap,
        translationX: Float = 0f,
        translationY: Float = 0f,
        scaleX: Float = 1f,
        scaleY: Float = 1f,
        rotationZ: Float = 0f,
        alpha: Float = 1f
    ) {
        Image(
            bitmap = imageBitmap,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    this.translationX = translationX
                    this.translationY = translationY
                    this.scaleX = scaleX
                    this.scaleY = scaleY
                    this.rotationZ = rotationZ
                    this.alpha = alpha.coerceIn(0f, 1f)
                }
        )
    }


    //  INCOMING CLIP TRANSFORM

    fun getIncomingTransform(
        key: String,
        p: Float,
        w: Float,
        h: Float
    ): Transform2D {
        val pr = p.coerceIn(0f, 1f)
        val k = key.lowercase()

        return when (k) {

            // Fades
            "fade", "dissolve", "blur", "softglaze", "dreamybloom",
            "glowdissolve", "watercolorbleed", "oilpainting",
            "datamoshing", "fadegrays", "signalloss", "noiseintercept",
            "fadewhite", "whiteflash", "chromaticflash", "flashjolt",
            "lightleakburst", "neonflare", "lensflarecut",
            "fadeblack", "blinkfade", "vintageslide" -> Transform2D()

            // Slides
            "slideleft", "pushleft", "panleft", "wipeleft",
            "whip pan", "wippan", "velocityshake", "smoothleft",
            "vector shift", "vectorshift",
            "hlslice", "vuslice", "lineardraw", "linearwipe" ->
                Transform2D(tx = (1f - pr) * w)

            "slideright", "pushright", "panright", "wiperight",
            "smoothright", "sunbeamsweep",
            "hrslice", "vdslice", "horizontalscan", "digitalwave",
            "interlacedcut", "paperturn", "pageturn" ->
                Transform2D(tx = -(1f - pr) * w)

            "slideup", "pushup", "tiltup", "wipeup",
            "riseup", "smoothup", "verticalbounce" ->
                Transform2D(ty = (1f - pr) * h)

            "slidedown", "pushdown", "tiltdown", "wipedown",
            "dropdown", "smoothdown", "epicstrike", "staticmelt" ->
                Transform2D(ty = -(1f - pr) * h)

            // Diagonal
            "diagonalswift", "diagtl" -> Transform2D(
                tx = (1f - pr) * w,
                ty = (1f - pr) * h
            )

            "diagtr", "diagonalslice", "trianglesweep",
            "burningpaper" -> Transform2D(
                tx = -(1f - pr) * w,
                ty = (1f - pr) * h
            )

            "diagbl" -> Transform2D(
                tx = (1f - pr) * w,
                ty = -(1f - pr) * h
            )

            "diagbr" -> Transform2D(
                tx = -(1f - pr) * w,
                ty = -(1f - pr) * h
            )

            // Zooms
            "zoomin", "zoomimpact", "snapback", "pullinshake" ->
                Transform2D(
                    scaleX = 0.5f + pr * 0.5f,
                    scaleY = 0.5f + pr * 0.5f
                )

            "zoomout", "pushoutbounce", "boxzoom" ->
                Transform2D(
                    scaleX = 1.5f - pr * 0.5f,
                    scaleY = 1.5f - pr * 0.5f
                )

            "crosszoom", "liquidblob", "jigsawmask",
            "gaussianzoom" -> Transform2D(
                scaleX = 0.5f + pr * 0.5f,
                scaleY = 0.5f + pr * 0.5f
            )

            // Shapes
            "circleopen", "circlemask", "starburst", "halopulse",
            "inksplash", "spiralwipe", "hearts" -> Transform2D(
                scaleX = 0.3f + pr * 0.7f,
                scaleY = 0.3f + pr * 0.7f
            )

            "irisbox", "clockwipe", "radial", "halo",
            "radialzoomblur", "basswave", "turbulentswivel" ->
                Transform2D(
                    scaleX = 0.4f + pr * 0.6f,
                    scaleY = 0.4f + pr * 0.6f,
                    rotZ = (1f - pr) * -45f
                )

            "horzopen", "horzclose", "mirrorsplit", "mirror" ->
                Transform2D(
                    scaleY = 0.1f + pr * 0.9f
                )

            "vertopen", "vertclose", "matrixcode",
            "venetianblinds" -> Transform2D(
                scaleX = 0.1f + pr * 0.9f
            )

            "wipetl", "crosshatch", "zigzagwipe",
            "mosaicswitch" -> Transform2D(
                tx = (1f - pr) * 400f,
                ty = (1f - pr) * 400f
            )

            "wipetr" -> Transform2D(
                tx = -(1f - pr) * 400f,
                ty = (1f - pr) * 400f
            )

            "wipebl" -> Transform2D(
                tx = (1f - pr) * 400f,
                ty = -(1f - pr) * 400f
            )

            "wipebr" -> Transform2D(
                tx = -(1f - pr) * 400f,
                ty = -(1f - pr) * 400f
            )

            "pixelize", "halftonedissolve", "griddissolve",
            "hexagonmatrix", "mosaic" -> Transform2D(
                scaleX = 0.85f + pr * 0.15f,
                scaleY = 0.85f + pr * 0.15f
            )

            "rectcrop", "coverleft", "coverright",
            "slidedoor" -> Transform2D()

            // Spin/Rotate
            "spincw", "spinin", "twirlcw", "cameraroll",
            "clockwise twirl" -> Transform2D(
                rotZ = -360f * (1f - pr),
                scaleX = 0.5f + pr * 0.5f,
                scaleY = 0.5f + pr * 0.5f
            )

            "spinccw", "spinout", "twirlccw", "counter twirl",
            "swirl" -> Transform2D(
                rotZ = 360f * (1f - pr),
                scaleX = 0.5f + pr * 0.5f,
                scaleY = 0.5f + pr * 0.5f
            )

            // Glitch
            "glitch", "rgbsplit", "rgbshift", "glitchshake", "vcrdistortion",
            "glitchblur", "glitchpaint", "hblur",
            "anaglyphslide", "bitcrushed", "prismblur" ->
                Transform2D(
                    tx = kotlin.math.sin((pr * 40f).toDouble()).toFloat() * 15f
                )

            "horizontajiggle", "horizontaljiggle", "verticaljiggle",
            "tremorcut", "wobbleslide", "chaosdrift",
            "rumbledissolve", "shake" -> Transform2D(
                tx = kotlin.math.sin((pr * 60f).toDouble()).toFloat() * 20f
            )

            // Artistic
            "glassthatter", "smokescreen", "smokedissolve",
            "comicflip", "papertear" -> Transform2D(
                scaleX = 0.5f + pr * 0.5f,
                scaleY = 0.5f + pr * 0.5f,
                rotZ = (1f - pr) * 15f
            )

            // Default
            else -> Transform2D()
        }
    }
}