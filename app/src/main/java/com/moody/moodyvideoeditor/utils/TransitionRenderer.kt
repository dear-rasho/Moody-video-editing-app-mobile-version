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
        clip: EditorClip, // <-- Naya parameter (asli transform ke liye)
        bitmap: android.graphics.Bitmap,
        transitionKey: String,
        progress: Float,
        w: Float,
        h: Float
    ) {
        val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }
        val p = progress.coerceIn(0f, 1f)

        when (val k = transitionKey.lowercase()) {

            //  FADES
            "fade", "dissolve", "blur",
            "softglaze", "dreamybloom", "glowdissolve",
            "watercolorbleed", "oilpainting", "datamoshing" -> {
                SimpleImage(imageBitmap, clip, w, h, alpha = 1f - p)
            }

            "fadeblack", "blinkfade", "vintageslide" -> {
                SimpleImage(imageBitmap, clip, w, h, alpha = if (p < 0.5f) 1f - (p * 2f) else 0f)
            }

            "fadewhite", "whiteflash", "chromaticflash", "flashjolt",
            "lightleakburst", "neonflare", "lensflarecut", "vignetteburn",
            "filmburn", "chalksketch" -> {
                SimpleImage(imageBitmap, clip, w, h, alpha = when {
                    p < 0.15f -> 1f
                    p < 0.30f -> 1f - (p - 0.15f) / 0.15f
                    else -> 0f
                })
            }

            //  SLIDE FAMILY
            "slideleft", "pushleft", "panleft", "wipeleft",
            "whip pan", "wippan", "velocityshake", "smoothleft",
            "vectorShift".lowercase(), "vector shift", "vectorshift" -> {
                SimpleImage(imageBitmap, clip, w, h, translationX = -p * w)
            }

            "slideright", "pushright", "panright", "wiperight",
            "smoothright", "sunbeamsweep" -> {
                SimpleImage(imageBitmap, clip, w, h, translationX = p * w)
            }

            "slideup", "pushup", "tiltup", "wipeup",
            "riseup", "smoothup", "verticalbounce" -> {
                SimpleImage(imageBitmap, clip, w, h, translationY = -p * h)
            }

            "slidedown", "pushdown", "tiltdown", "wipedown",
            "dropdown", "smoothdown", "epicstrike", "staticmelt" -> {
                SimpleImage(imageBitmap, clip, w, h, translationY = p * h)
            }

            // Diagonal
            "diagonalswift", "diagtl", "diagtr", "diagbl", "diagbr",
            "diagonalslice", "trianglesweep", "burningpaper" -> {
                SimpleImage(imageBitmap, clip, w, h, translationX = p * w, translationY = p * h)
            }

            //  ZOOM FAMILY
            "zoomin", "zoominx", "zoomimpact", "snapback",
            "gaussianzoom", "pullinshake" -> {
                // Image 1 ab scale up hoga aur fade out hoga
                SimpleImage(imageBitmap, clip, w, h, scaleX = 1f + p * 1.5f, scaleY = 1f + p * 1.5f, alpha = 1f - p)
            }

            "zoomout", "pushoutbounce", "boxzoom" -> {
                // Image 1 ab scale down hoga aur fade out hoga
                SimpleImage(imageBitmap, clip, w, h, scaleX = 1f - p * 0.5f, scaleY = 1f - p * 0.5f, alpha = 1f - p)
            }

            "crosszoom", "liquidblob", "jigsawmask" -> {
                // Image 1 bahar ki taraf zoom karega
                SimpleImage(imageBitmap, clip, w, h, scaleX = 1f + p * 1.5f, scaleY = 1f + p * 1.5f, alpha = 1f - p)
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
                SimpleImage(imageBitmap, clip, w, h, rotationZ = rot, scaleX = 1f - p * 0.5f, scaleY = 1f - p * 0.5f, alpha = 1f - p)
            }

            //  GLITCH / DISTORTION
            "glitch", "rgbsplit", "rgbshift", "glitchshake", "vcrDistortion".lowercase(),
            "vcrdistortion", "glitchblur", "glitchpaint",
            "hblur", "anaglyphslide", "bitcrushed", "prismblur",
            "gaussianBlur".lowercase() -> {
                val offset = kotlin.math.sin((p * 40f).toDouble()).toFloat() * 15f
                SimpleImage(imageBitmap, clip, w, h, translationX = offset, alpha = 1f - p)
            }

            "horizontajiggle", "horizontaljiggle", "verticaljiggle",
            "tremorcut", "glitchShake".lowercase(),
            "wobbleslide", "chaosdrift", "rumbleDissolve".lowercase(),
            "rumbledissolve", "shake" -> {
                val offset = kotlin.math.sin((p * 60f).toDouble()).toFloat() * 20f
                SimpleImage(imageBitmap, clip, w, h, translationX = offset, translationY = -offset, alpha = 1f - p * 0.9f)
            }

            //  ARTISTIC / SPECIAL
            "glassthatter", "smokescreen", "smokedissolve",
            "comicflip", "papertear" -> {
                SimpleImage(imageBitmap, clip, w, h, scaleX = 1f - p * 0.3f, scaleY = 1f - p * 0.3f, rotationZ = p * 15f, alpha = 1f - p)
            }

            else -> {
                SimpleImage(imageBitmap, clip, w, h, alpha = 1f - p)
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
                    // Purani clip ka asli offset, crop aur scale nikalna
                    val cropSx = 1f / (1f - base.cropL - base.cropR).coerceAtLeast(0.05f)
                    val cropSy = 1f / (1f - base.cropT - base.cropB).coerceAtLeast(0.05f)
                    val cropTx = -(base.cropL - base.cropR) / 2f * w
                    val cropTy = -(base.cropT - base.cropB) / 2f * h
                    val posTx = (base.x - 50f) / 100f * w
                    val posTy = (base.y - 50f) / 100f * h

                    // Transition wale motion ke sath base position add karna
                    this.translationX = posTx + cropTx + translationX
                    this.translationY = posTy + cropTy + translationY
                    this.scaleX = (base.scale / 100f) * cropSx * scaleX
                    this.scaleY = (base.scale / 100f) * cropSy * scaleY
                    this.rotationZ = base.rotation + rotationZ
                    this.alpha = alpha.coerceIn(0f, 1f)
                }
        )
    }

    //  INCOMING CLIP TRANSFORM (Make sure this matches FFmpeg xfade)
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
            "diagonalswift", "diagtl" -> Transform2D(tx = (1f - pr) * w, ty = (1f - pr) * h)
            "diagtr", "diagonalslice", "trianglesweep", "burningpaper" -> Transform2D(tx = -(1f - pr) * w, ty = (1f - pr) * h)
            "diagbl" -> Transform2D(tx = (1f - pr) * w, ty = -(1f - pr) * h)
            "diagbr" -> Transform2D(tx = -(1f - pr) * w, ty = -(1f - pr) * h)

            // Zooms
            // Zooms
            "zoomin", "zoomimpact", "snapback", "pullinshake", "gaussianzoom", "crosszoom", "liquidblob", "jigsawmask" ->
                Transform2D(scaleX = 0.5f + pr * 0.5f, scaleY = 0.5f + pr * 0.5f) // Incoming 0.5x se shuru hoga

            "zoomout", "pushoutbounce", "boxzoom" -> Transform2D() // Incoming static, outgoing zooms out

            // Shapes
            "circleopen", "circlemask", "starburst", "halopulse",
            "inksplash", "spiralwipe", "hearts" -> Transform2D(scaleX = 0.3f + pr * 0.7f, scaleY = 0.3f + pr * 0.7f)

            "irisbox", "clockwipe", "radial", "halo", "radialzoomblur", "basswave", "turbulentswivel" ->
                Transform2D(scaleX = 0.4f + pr * 0.6f, scaleY = 0.4f + pr * 0.6f, rotZ = (1f - pr) * -45f)

            "horzopen", "horzclose", "mirrorsplit", "mirror" -> Transform2D(scaleY = 0.1f + pr * 0.9f)
            "vertopen", "vertclose", "matrixcode", "venetianblinds" -> Transform2D(scaleX = 0.1f + pr * 0.9f)

            "wipetl", "crosshatch", "zigzagwipe", "mosaicswitch" -> Transform2D(tx = (1f - pr) * 400f, ty = (1f - pr) * 400f)
            "wipetr" -> Transform2D(tx = -(1f - pr) * 400f, ty = (1f - pr) * 400f)
            "wipebl" -> Transform2D(tx = (1f - pr) * 400f, ty = -(1f - pr) * 400f)
            "wipebr" -> Transform2D(tx = -(1f - pr) * 400f, ty = -(1f - pr) * 400f)

            "pixelize", "halftonedissolve", "griddissolve", "hexagonmatrix", "mosaic" ->
                Transform2D(scaleX = 0.85f + pr * 0.15f, scaleY = 0.85f + pr * 0.15f)

            "rectcrop", "coverleft", "coverright", "slidedoor" -> Transform2D()

            // Spin/Rotate
            "spincw", "spinin", "twirlcw", "cameraroll", "clockwise twirl" ->
                Transform2D(rotZ = -360f * (1f - pr), scaleX = 0.5f + pr * 0.5f, scaleY = 0.5f + pr * 0.5f)

            "spinccw", "spinout", "twirlccw", "counter twirl", "swirl" ->
                Transform2D(rotZ = 360f * (1f - pr), scaleX = 0.5f + pr * 0.5f, scaleY = 0.5f + pr * 0.5f)

            // Glitch
            "glitch", "rgbsplit", "rgbshift", "glitchshake", "vcrdistortion", "glitchblur", "glitchpaint", "hblur",
            "anaglyphslide", "bitcrushed", "prismblur" ->
                Transform2D(tx = kotlin.math.sin((pr * 40f).toDouble()).toFloat() * 15f)

            "horizontajiggle", "horizontaljiggle", "verticaljiggle", "tremorcut", "wobbleslide", "chaosdrift",
            "rumbledissolve", "shake" -> Transform2D(tx = kotlin.math.sin((pr * 60f).toDouble()).toFloat() * 20f)

            // Artistic
            "glassthatter", "smokescreen", "smokedissolve", "comicflip", "papertear" ->
                Transform2D(scaleX = 0.5f + pr * 0.5f, scaleY = 0.5f + pr * 0.5f, rotZ = (1f - pr) * 15f)

            // Default
            else -> Transform2D()
        }
    }
}