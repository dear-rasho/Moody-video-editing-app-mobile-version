package com.moody.moodyvideoeditor.utils

import androidx.compose.ui.graphics.Color

/**
 * Mirrors js/codebase/fontLibrary.js FONT_CATEGORIES
 * + js/features/animations.js ANIMATIONS list.
 */
object TextEngine {

    // ═══════════════════════════════════════════════════════════
    //  FONT LIST — mirrors fontLibrary.js FONT_CATEGORIES
    // ═══════════════════════════════════════════════════════════
    val FONTS: List<String> = listOf(
        // System (always available)
        "Arial", "Helvetica", "Georgia", "Times New Roman", "Courier New",
        "Verdana", "Tahoma", "Trebuchet MS", "Impact", "Comic Sans MS",
        "Palatino Linotype", "Garamond", "Lucida Console", "Arial Black",
        "Segoe UI", "Roboto", "Open Sans", "Montserrat", "Poppins", "Lato",
        "Cambria", "Calibri", "Candara", "Corbel", "Consolas",
        "Menlo", "Monaco", "Optima", "Avenir", "Futura", "Gill Sans",
        "Book Antiqua", "Constantia", "Franklin Gothic Medium", "Rockwell",

        // Google (fallback to system sans if not available)
        "Dancing Script", "Pacifico", "Great Vibes", "Allura",
        "Alex Brush", "Satisfy", "Kaushan Script", "Parisienne",
        "Caveat", "Shadows Into Light", "Indie Flower", "Amatic SC",
        "Playfair Display", "Cormorant Garamond", "EB Garamond",
        "Lora", "Merriweather", "Crimson Text", "Cinzel",
        "Bebas Neue", "Oswald", "Anton", "Archivo Black",
        "Bungee", "Titan One", "Bowlby One SC", "Alfa Slab One",
        "Russo One", "Righteous", "Monoton", "Audiowide", "Orbitron"
    )

    // ═══════════════════════════════════════════════════════════
    //  ANIMATIONS — mirrors animations.js (100+)
    // ═══════════════════════════════════════════════════════════
    val ANIMATIONS: List<String> = listOf(
        // Basic
        "none", "typewriter", "decoder",
        "fadeIn", "fadeUp", "fadeDown",
        "slideLeft", "slideRight", "slideUp", "slideDown",
        "popIn", "bounceIn", "flicker", "cinematicBlur",

        // Reveals
        "wordReveal", "characterRise", "maskVertical", "maskHorizontal",
        "centerOut", "lineDraw", "blurryReveal", "smokeDissolve", "trailFade",

        // Glitch
        "glitch", "rgbSplit", "sliceGlitch", "blockGlitch", "staticNoise",
        "vcrDistort", "shakeJitter", "cyberpunk", "matrixRain", "interlaced",

        // Waves
        "wave", "bounceWave", "sineWave", "liquidMelt", "flagWave",
        "waterRipple", "heatWave", "elasticWave", "pulsingWave",
        "turbulent", "circularWave",

        // Bounces
        "overshootPop", "elasticDrop", "jellyBounce", "microBounce",
        "stompBounce", "squeezeStretch", "float", "diagonalJump",
        "gravityFall", "heavyLanding", "doubleBounce", "bouncySpin",
        "snapBack", "springString", "sideKick",

        // Sliders
        "flyDiagonalTL", "flyDiagonalBR", "crossSlide", "accelSlide",
        "decelSlide", "splitSlide", "zigzagSlide", "smoothGlide",
        "infiniteScroll", "pushSlide",

        // Rotations
        "flip3DX", "flip3DY", "rotate3D", "yAxisFlip", "xAxisFlip",
        "vortexSpin", "zAxisSpin", "spiralIn", "tornado", "skewSpin",
        "pendulum", "propeller", "barrelRoll", "cubeRoll",
        "gentleTilt", "twister",

        // Zooms
        "zoomIn", "zoomOut", "cinematicZoom", "hyperZoomOut", "pulseScale",
        "elasticZoom", "lensFlareZoom", "shrinkReveal", "popScale",
        "depthZoom", "snapZoom",

        // Special
        "scribble", "neonGlow", "gradientShift", "ghostTrail",
        "silhouette", "explosion", "implosion", "pulse", "shake"
    )

    // ═══════════════════════════════════════════════════════════
    //  ANCHORS — mirrors textRenderer anchor options
    // ═══════════════════════════════════════════════════════════
    val ANCHORS: Map<String, Pair<Float, Float>> = mapOf(
        "top-left" to (0f to 0f),
        "top-center" to (50f to 0f),
        "top-right" to (100f to 0f),
        "center-left" to (0f to 50f),
        "center" to (50f to 50f),
        "center-right" to (100f to 50f),
        "bottom-left" to (0f to 100f),
        "bottom-center" to (50f to 100f),
        "bottom-right" to (100f to 100f)
    )

    // ═══════════════════════════════════════════════════════════
    //  EASING — mirrors keyframeStore.js easeFn
    // ═══════════════════════════════════════════════════════════
    fun easeFn(t: Float, type: String): Float {
        val x = t.coerceIn(0f, 1f)
        return when (type) {
            "linear" -> x
            "easeIn" -> x * x
            "easeOut" -> 1f - (1f - x) * (1f - x)
            "easeInOut" -> if (x < 0.5f) 2f * x * x else 1f - Math.pow(
                (-2f * x + 2f).toDouble(),
                2.0
            ).toFloat() / 2f

            "easeInCubic" -> x * x * x
            "easeOutCubic" -> 1f - Math.pow((1f - x).toDouble(), 3.0).toFloat()
            "easeInOutCubic" -> if (x < 0.5f) 4f * x * x * x else 1f - Math.pow(
                (-2f * x + 2f).toDouble(),
                3.0
            ).toFloat() / 2f

            "easeInBack" -> {
                val c1 = 1.70158f
                val c3 = c1 + 1f
                c3 * x * x * x - c1 * x * x
            }

            "easeOutBack" -> {
                val c1 = 1.70158f
                val c3 = c1 + 1f
                1f + c3 * Math.pow((x - 1f).toDouble(), 3.0)
                    .toFloat() + c1 * Math.pow((x - 1f).toDouble(), 2.0).toFloat()
            }

            "easeOutElastic" -> {
                val c4 = (2f * Math.PI / 3f).toFloat()
                if (x == 0f) 0f
                else if (x == 1f) 1f
                else Math.pow(2.0, (-10f * x).toDouble()).toFloat() *
                        Math.sin(((x * 10f - 0.75f) * c4).toDouble()).toFloat() + 1f
            }

            else -> x
        }
    }

    fun colorFromLong(v: Long): Color = Color(v.toULong().toLong())
}