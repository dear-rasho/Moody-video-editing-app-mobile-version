package com.moody.moodyvideoeditor.utils

import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalInspectionMode

/**
 * Mirrors js/features/animations.js
 * - ANIMATIONS list (100+ animations, categorized)
 * - applyAnimation modifier extension
 * - Easing functions
 */
object AnimationsEngine {

    // ═══════════════════════════════════════════════════════════
    //  CATEGORIES — mirrors animations.js grouped structure
    // ═══════════════════════════════════════════════════════════
    data class Category(val key: String, val label: String, val animations: List<Anim>)
    data class Anim(val key: String, val label: String)

    val CATEGORIES: List<Category> = listOf(
        Category(
            "basic", "Basic", listOf(
                Anim("none", "None"),
                Anim("typewriter", "Typewriter"),
                Anim("decoder", "Decoder"),
                Anim("fadeIn", "Fade In"),
                Anim("fadeUp", "Fade Up"),
                Anim("fadeDown", "Fade Down"),
                Anim("slideLeft", "Slide Left"),
                Anim("slideRight", "Slide Right"),
                Anim("slideUp", "Slide Up"),
                Anim("slideDown", "Slide Down"),
                Anim("popIn", "Pop In"),
                Anim("bounceIn", "Bounce In"),
                Anim("flicker", "Flicker"),
                Anim("cinematicBlur", "Cinematic Blur")
            )
        ),

        Category(
            "reveals", "Reveals", listOf(
                Anim("wordReveal", "Word Reveal"),
                Anim("characterRise", "Character Rise"),
                Anim("maskVertical", "Mask Vertical"),
                Anim("maskHorizontal", "Mask Horizontal"),
                Anim("centerOut", "Center Out"),
                Anim("lineDraw", "Line Draw"),
                Anim("blurryReveal", "Blurry Reveal"),
                Anim("smokeDissolve", "Smoke Dissolve"),
                Anim("trailFade", "Trail Fade")
            )
        ),

        Category(
            "glitch", "Glitch", listOf(
                Anim("glitch", "Glitch"),
                Anim("rgbSplit", "RGB Split"),
                Anim("sliceGlitch", "Slice Glitch"),
                Anim("blockGlitch", "Block Glitch"),
                Anim("staticNoise", "Static Noise"),
                Anim("vcrDistort", "VCR Distort"),
                Anim("shakeJitter", "Shake Jitter"),
                Anim("cyberpunk", "Cyberpunk"),
                Anim("matrixRain", "Matrix Rain"),
                Anim("interlaced", "Interlaced")
            )
        ),

        Category(
            "waves", "Waves", listOf(
                Anim("wave", "Wave"),
                Anim("bounceWave", "Bounce Wave"),
                Anim("sineWave", "Sine Wave"),
                Anim("liquidMelt", "Liquid Melt"),
                Anim("flagWave", "Flag Wave"),
                Anim("waterRipple", "Water Ripple"),
                Anim("heatWave", "Heat Wave"),
                Anim("elasticWave", "Elastic Wave"),
                Anim("pulsingWave", "Pulsing Wave"),
                Anim("turbulent", "Turbulent"),
                Anim("circularWave", "Circular Wave")
            )
        ),

        Category(
            "bounces", "Bounces", listOf(
                Anim("overshootPop", "Overshoot Pop"),
                Anim("elasticDrop", "Elastic Drop"),
                Anim("jellyBounce", "Jelly Bounce"),
                Anim("microBounce", "Micro Bounce"),
                Anim("stompBounce", "Stomp Bounce"),
                Anim("squeezeStretch", "Squeeze Stretch"),
                Anim("float", "Float"),
                Anim("diagonalJump", "Diagonal Jump"),
                Anim("gravityFall", "Gravity Fall"),
                Anim("heavyLanding", "Heavy Landing"),
                Anim("doubleBounce", "Double Bounce"),
                Anim("bouncySpin", "Bouncy Spin"),
                Anim("snapBack", "Snap Back"),
                Anim("springString", "Spring String"),
                Anim("sideKick", "Side Kick")
            )
        ),

        Category(
            "sliders", "Sliders", listOf(
                Anim("flyDiagonalTL", "Fly In Top-Left"),
                Anim("flyDiagonalBR", "Fly In Bottom-Right"),
                Anim("crossSlide", "Cross Slide"),
                Anim("accelSlide", "Accel Slide"),
                Anim("decelSlide", "Decel Slide"),
                Anim("splitSlide", "Split Slide"),
                Anim("zigzagSlide", "Zig-Zag Slide"),
                Anim("smoothGlide", "Smooth Glide"),
                Anim("infiniteScroll", "Infinite Scroll"),
                Anim("pushSlide", "Push Slide")
            )
        ),

        Category(
            "rotations", "Rotations", listOf(
                Anim("flip3DX", "3D Flip X"),
                Anim("flip3DY", "3D Flip Y"),
                Anim("rotate3D", "3D Rotate"),
                Anim("yAxisFlip", "Y-Axis Flip"),
                Anim("xAxisFlip", "X-Axis Flip"),
                Anim("vortexSpin", "Vortex Spin"),
                Anim("zAxisSpin", "Z-Axis Spin"),
                Anim("spiralIn", "Spiral In"),
                Anim("tornado", "Tornado"),
                Anim("skewSpin", "Skew Spin"),
                Anim("pendulum", "Pendulum"),
                Anim("propeller", "Propeller"),
                Anim("barrelRoll", "Barrel Roll"),
                Anim("cubeRoll", "Cube Roll"),
                Anim("gentleTilt", "Gentle Tilt"),
                Anim("twister", "Twister")
            )
        ),

        Category(
            "zooms", "Zooms", listOf(
                Anim("zoomIn", "Zoom In"),
                Anim("zoomOut", "Zoom Out"),
                Anim("cinematicZoom", "Cinematic Zoom"),
                Anim("hyperZoomOut", "Hyper Zoom Out"),
                Anim("pulseScale", "Pulse Scale"),
                Anim("elasticZoom", "Elastic Zoom"),
                Anim("lensFlareZoom", "Lens Flare Zoom"),
                Anim("shrinkReveal", "Shrink Reveal"),
                Anim("popScale", "Pop Scale"),
                Anim("depthZoom", "Depth Zoom"),
                Anim("snapZoom", "Snap Zoom")
            )
        ),

        Category(
            "special", "Special", listOf(
                Anim("scribble", "Scribble"),
                Anim("neonGlow", "Neon Glow"),
                Anim("gradientShift", "Gradient Shift"),
                Anim("ghostTrail", "Ghost Trail"),
                Anim("silhouette", "Silhouette"),
                Anim("explosion", "Explosion"),
                Anim("implosion", "Implosion"),
                Anim("pulse", "Pulse"),
                Anim("shake", "Shake")
            )
        )
    )

    val ALL_ANIMATIONS: List<Anim> = CATEGORIES.flatMap { it.animations }

    // ═══════════════════════════════════════════════════════════
    //  EASING — mirrors keyframeStore.js easeFn
    // ═══════════════════════════════════════════════════════════
    fun ease(t: Float, type: String): Float {
        val x = t.coerceIn(0f, 1f)
        return when (type) {
            "linear" -> x
            "easeIn" -> x * x
            "easeOut" -> 1f - (1f - x) * (1f - x)
            "easeInOut" -> if (x < 0.5f) 2f * x * x
            else 1f - Math.pow((-2f * x + 2f).toDouble(), 2.0).toFloat() / 2f

            "easeInCubic" -> x * x * x
            "easeOutCubic" -> 1f - Math.pow((1f - x).toDouble(), 3.0).toFloat()
            "easeInOutCubic" -> if (x < 0.5f) 4f * x * x * x
            else 1f - Math.pow((-2f * x + 2f).toDouble(), 3.0).toFloat() / 2f

            "easeInBack" -> {
                val c1 = 1.70158f
                val c3 = c1 + 1f
                c3 * x * x * x - c1 * x * x
            }

            "easeOutBack" -> {
                val c1 = 1.70158f
                val c3 = c1 + 1f
                1f + c3 * Math.pow((x - 1f).toDouble(), 3.0).toFloat() +
                        c1 * Math.pow((x - 1f).toDouble(), 2.0).toFloat()
            }

            "easeInOutBack" -> {
                val c1 = 1.70158f
                val c2 = c1 * 1.525f
                if (x < 0.5f)
                    (Math.pow((2f * x).toDouble(), 2.0).toFloat() *
                            ((c2 + 1f) * 2f * x - c2)) / 2f
                else
                    (Math.pow((2f * x - 2f).toDouble(), 2.0).toFloat() *
                            ((c2 + 1f) * (x * 2f - 2f) + c2) + 2f) / 2f
            }

            "easeOutBounce" -> {
                val n1 = 7.5625f
                val d1 = 2.75f
                var y = x
                when {
                    y < 1f / d1 -> n1 * y * y
                    y < 2f / d1 -> {
                        y -= 1.5f / d1; n1 * y * y + 0.75f
                    }

                    y < 2.5f / d1 -> {
                        y -= 2.25f / d1; n1 * y * y + 0.9375f
                    }

                    else -> {
                        y -= 2.625f / d1; n1 * y * y + 0.984375f
                    }
                }
            }

            "easeOutElastic" -> {
                val c4 = (2f * Math.PI / 3f).toFloat()
                when (x) {
                    0f -> 0f
                    1f -> 1f
                    else -> Math.pow(2.0, (-10f * x).toDouble()).toFloat() *
                            Math.sin(((x * 10f - 0.75f) * c4).toDouble()).toFloat() + 1f
                }
            }

            else -> x
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  ANIMATION FRAME — computed properties for a given progress
    //  Mirrors the JS applyAnimation → CSS keyframe effects
    // ═══════════════════════════════════════════════════════════
    data class Frame(
        val alpha: Float = 1f,
        val scaleX: Float = 1f,
        val scaleY: Float = 1f,
        val translateX: Float = 0f,
        val translateY: Float = 0f,
        val rotationZ: Float = 0f,
        val rotationX: Float = 0f,
        val rotationY: Float = 0f,
        val blurRadiusPx: Float = 0f
    )

    /**
     * Compute current animation frame.
     * progress: 0..1 (0 = animation start, 1 = animation complete)
     * elapsedSec: time since clip start (for looping animations like wave/pulse)
     */
    fun computeFrame(
        animation: String,
        progress: Float,
        elapsedSec: Float = 0f
    ): Frame {
        val p = progress.coerceIn(0f, 1f)
        val eOB = ease(p, "easeOutBack")
        val eOC = ease(p, "easeOutCubic")
        val eIC = ease(p, "easeInCubic")
        val eOEl = ease(p, "easeOutElastic")
        val eOBounce = ease(p, "easeOutBounce")

        return when (animation) {
            "none" -> Frame()

            // ═══ BASIC ═══
            "fadeIn" -> Frame(alpha = p)
            "fadeUp" -> Frame(alpha = p, translateY = (1f - p) * 24f)
            "fadeDown" -> Frame(alpha = p, translateY = (1f - p) * -24f)
            "slideLeft" -> Frame(alpha = p, translateX = (1f - p) * -80f)
            "slideRight" -> Frame(alpha = p, translateX = (1f - p) * 80f)
            "slideUp" -> Frame(alpha = p, translateY = (1f - p) * 80f)
            "slideDown" -> Frame(alpha = p, translateY = (1f - p) * -80f)
            "popIn" -> Frame(alpha = (p * 2.5f).coerceAtMost(1f), scaleX = eOB, scaleY = eOB)
            "bounceIn" -> Frame(
                alpha = (p * 2.5f).coerceAtMost(1f),
                scaleX = eOBounce,
                scaleY = eOBounce
            )

            "flicker" -> {
                val vals = listOf(1f, 0.25f, 1f, 0.5f, 1f, 0.15f, 1f, 0.4f, 1f, 0.2f, 1f)
                val idx = (p * (vals.size - 1)).toInt().coerceIn(0, vals.size - 1)
                Frame(alpha = vals[idx])
            }

            "cinematicBlur" -> {
                val e = (p / 0.6f).coerceAtMost(1f)
                Frame(alpha = (p * 1.5f).coerceAtMost(1f), blurRadiusPx = (1f - e) * 18f)
            }

            // ═══ REVEALS ═══
            "wordReveal" -> Frame(
                scaleX = 0.6f + 0.4f * eOB,
                scaleY = 0.6f + 0.4f * eOB,
                alpha = (p * 2f).coerceAtMost(1f),
                blurRadiusPx = (1f - p) * 6f,
                translateY = (1f - p) * 10f
            )

            "characterRise" -> Frame(alpha = p, translateY = (1f - p) * 40f)
            "maskVertical", "maskHorizontal", "lineDraw" -> Frame(alpha = (p * 3f).coerceAtMost(1f))
            "centerOut" -> Frame(alpha = (p * 3f).coerceAtMost(1f))
            "blurryReveal" -> Frame(
                blurRadiusPx = (1f - p) * 20f,
                alpha = p,
                scaleX = 1.2f - 0.2f * p,
                scaleY = 1.2f - 0.2f * p
            )

            "smokeDissolve" -> Frame(
                blurRadiusPx = (1f - p) * 30f,
                alpha = p,
                scaleX = 1.4f - 0.4f * p,
                scaleY = 1.4f - 0.4f * p
            )

            "trailFade" -> Frame(alpha = p)

            // ═══ GLITCH ═══
            "glitch" -> Frame(
                translateX = Math.sin((p * 30f).toDouble()).toFloat() * 4f,
                translateY = Math.cos((p * 27f).toDouble()).toFloat() * 4f
            )

            "rgbSplit" -> Frame(
                translateX = Math.sin((p * 30f).toDouble()).toFloat() * 6f * (1f - p)
            )

            "sliceGlitch" -> Frame(
                translateX = Math.sin((p * 40f).toDouble()).toFloat() * 10f * (1f - p)
            )

            "blockGlitch" -> Frame(alpha = (p * 2f).coerceAtMost(1f))
            "staticNoise" -> Frame(
                translateX = (Math.random().toFloat() - 0.5f) * 4f,
                translateY = (Math.random().toFloat() - 0.5f) * 4f,
                alpha = (p * 2f).coerceAtMost(1f)
            )

            "vcrDistort" -> Frame(
                translateX = Math.sin((p * 20f).toDouble()).toFloat() * 4f,
                alpha = (p * 2f).coerceAtMost(1f)
            )

            "shakeJitter" -> Frame(
                translateX = Math.sin((elapsedSec * 40f).toDouble()).toFloat() * 1.5f,
                translateY = Math.cos((elapsedSec * 38f).toDouble()).toFloat() * 1.5f
            )

            "cyberpunk" -> Frame(
                alpha = 0.8f + Math.sin((elapsedSec * 15f).toDouble()).toFloat() * 0.2f
            )

            "matrixRain" -> Frame(
                translateY = (1f - p) * -30f,
                alpha = p,
                blurRadiusPx = (1f - p) * 8f
            )

            "interlaced" -> Frame(alpha = (p * 2f).coerceAtMost(1f))

            // ═══ WAVES ═══
            "wave" -> Frame(translateY = Math.sin((elapsedSec * 6f).toDouble()).toFloat() * 8f)
            "bounceWave" -> Frame(
                translateY = -Math.abs(
                    Math.sin((elapsedSec * 4f).toDouble()).toFloat()
                ) * 18f
            )

            "sineWave" -> Frame(
                translateY = Math.sin((elapsedSec * 6f).toDouble()).toFloat() * 10f,
                rotationZ = Math.sin((elapsedSec * 6f).toDouble()).toFloat() * 3f
            )

            "liquidMelt" -> Frame(
                translateY = Math.sin((elapsedSec * 3f).toDouble()).toFloat() * 10f,
                scaleX = 1f + Math.sin((elapsedSec * 3f).toDouble()).toFloat() * 0.1f,
                scaleY = 1f + Math.sin((elapsedSec * 3f).toDouble()).toFloat() * 0.1f
            )

            "flagWave" -> Frame(
                scaleX = 1f + Math.sin((elapsedSec * 5f).toDouble()).toFloat() * 0.05f
            )

            "waterRipple" -> Frame(
                scaleX = 1f + Math.sin((elapsedSec * 8f).toDouble()).toFloat() * 0.05f,
                scaleY = 1f + Math.sin((elapsedSec * 8f).toDouble()).toFloat() * 0.05f
            )

            "heatWave" -> Frame(
                translateX = Math.sin((elapsedSec * 12f).toDouble()).toFloat() * 2f,
                blurRadiusPx = Math.abs(Math.sin((elapsedSec * 6f).toDouble()).toFloat()) * 1f
            )

            "elasticWave" -> Frame(translateY = (1f - eOB) * 30f, alpha = (p * 2f).coerceAtMost(1f))
            "pulsingWave" -> Frame(
                scaleX = 1f + Math.sin((elapsedSec * 4f).toDouble()).toFloat() * 0.06f,
                scaleY = 1f + Math.sin((elapsedSec * 4f).toDouble()).toFloat() * 0.06f,
                translateY = Math.sin((elapsedSec * 4f).toDouble()).toFloat() * 4f
            )

            "turbulent" -> Frame(
                translateX = Math.sin((elapsedSec * 8f).toDouble()).toFloat() * 3f,
                translateY = Math.cos((elapsedSec * 10f).toDouble()).toFloat() * 3f,
                rotationZ = Math.sin((elapsedSec * 6f).toDouble()).toFloat() * 2f
            )

            "circularWave" -> Frame(rotationZ = p * 360f, alpha = p)

            // ═══ BOUNCES ═══
            "overshootPop" -> Frame(alpha = (p * 2.5f).coerceAtMost(1f), scaleX = eOB, scaleY = eOB)
            "elasticDrop" -> Frame(
                alpha = (p * 3f).coerceAtMost(1f),
                translateY = (1f - eOBounce) * -200f
            )

            "jellyBounce" -> {
                val sq = 1f - eOB
                Frame(alpha = (p * 3f).coerceAtMost(1f), scaleY = 1f - sq * 0.3f)
            }

            "microBounce" -> Frame(
                alpha = (p * 3f).coerceAtMost(1f),
                scaleX = 0.85f + 0.15f * eOB,
                scaleY = 0.85f + 0.15f * eOB
            )

            "stompBounce" -> Frame(
                alpha = (p * 3f).coerceAtMost(1f),
                scaleX = 2.5f - 1.5f * eOB,
                scaleY = 2.5f - 1.5f * eOB,
                blurRadiusPx = (1f - p) * 8f
            )

            "squeezeStretch" -> Frame(
                scaleX = 1f + Math.sin((elapsedSec * 8f).toDouble()).toFloat() * 0.2f * (1f - p),
                scaleY = 1f - Math.sin((elapsedSec * 8f).toDouble()).toFloat() * 0.2f * (1f - p),
                alpha = (p * 2f).coerceAtMost(1f)
            )

            "float" -> Frame(translateY = Math.sin((elapsedSec * 3f).toDouble()).toFloat() * 8f)
            "diagonalJump" -> Frame(
                translateX = -(1f - eOB) * 100f,
                translateY = (1f - eOB) * 100f,
                rotationZ = -(1f - p) * 15f,
                alpha = (p * 2f).coerceAtMost(1f)
            )

            "gravityFall" -> Frame(translateY = p * 300f, rotationZ = p * 45f, alpha = 1f - p)
            "heavyLanding" -> Frame(
                translateY = -(1f - eOBounce) * 150f,
                alpha = (p * 2f).coerceAtMost(1f)
            )

            "doubleBounce" -> Frame(
                translateY = -(1f - eOBounce) * 100f,
                alpha = (p * 2f).coerceAtMost(1f)
            )

            "bouncySpin" -> Frame(
                translateY = -(1f - eOBounce) * 100f,
                rotationZ = p * 360f,
                alpha = (p * 2f).coerceAtMost(1f)
            )

            "snapBack" -> Frame(
                translateX = when {
                    p < 0.4f -> (p / 0.4f) * 80f
                    p < 0.7f -> 80f - ((p - 0.4f) / 0.3f) * 40f
                    else -> 40f - ((p - 0.7f) / 0.3f) * 40f
                }
            )

            "springString" -> Frame(
                translateY = (1f - eOB) * 30f,
                rotationZ = (1f - eOB) * -8f,
                alpha = (p * 2f).coerceAtMost(1f)
            )

            "sideKick" -> Frame(
                translateX = -(1f - eOB) * 150f,
                rotationZ = -(1f - p) * 25f,
                alpha = (p * 2f).coerceAtMost(1f)
            )

            // ═══ SLIDERS ═══
            "flyDiagonalTL" -> Frame(
                translateX = -(1f - eOC) * 200f,
                translateY = -(1f - eOC) * 200f,
                rotationZ = -(1f - p) * 30f,
                scaleX = 0.5f + 0.5f * p,
                scaleY = 0.5f + 0.5f * p,
                alpha = (p * 2f).coerceAtMost(1f)
            )

            "flyDiagonalBR" -> Frame(
                translateX = (1f - eOC) * 200f,
                translateY = (1f - eOC) * 200f,
                rotationZ = (1f - p) * 30f,
                scaleX = 0.5f + 0.5f * p,
                scaleY = 0.5f + 0.5f * p,
                alpha = (p * 2f).coerceAtMost(1f)
            )

            "crossSlide" -> Frame(translateX = -(1f - eOC) * 20f, alpha = (p * 2f).coerceAtMost(1f))
            "accelSlide" -> Frame(
                translateX = -(1f - eIC) * 200f,
                alpha = (p * 3f).coerceAtMost(1f)
            )

            "decelSlide" -> Frame(
                translateX = -(1f - eOC) * 200f,
                alpha = (p * 3f).coerceAtMost(1f)
            )

            "splitSlide" -> Frame(alpha = (p * 2f).coerceAtMost(1f))
            "zigzagSlide" -> Frame(
                translateX = -(1f - eOC) * 150f,
                translateY = Math.sin((p * Math.PI * 2f).toDouble()).toFloat() * 25f * (1f - p),
                rotationZ = -(1f - p) * 10f,
                alpha = (p * 2f).coerceAtMost(1f)
            )

            "smoothGlide" -> Frame(
                translateX = -(1f - eOC) * 60f,
                alpha = p,
                blurRadiusPx = (1f - p) * 4f
            )

            "infiniteScroll" -> Frame(translateX = -((elapsedSec * 30f) % 50f))
            "pushSlide" -> Frame(
                translateX = (1f - eOC) * 30f,
                scaleX = 1.1f - 0.1f * p,
                scaleY = 1.1f - 0.1f * p,
                alpha = (p * 2f).coerceAtMost(1f)
            )

            // ═══ ROTATIONS ═══
            "flip3DX" -> {
                val s = 0.01f + 0.99f * Math.abs(Math.cos(((1f - p) * Math.PI / 2f).toDouble()))
                    .toFloat()
                Frame(scaleX = s, scaleY = s, alpha = (p * 2f).coerceAtMost(1f))
            }

            "flip3DY" -> {
                val s = 0.01f + 0.99f * Math.abs(Math.cos(((1f - p) * Math.PI / 2f).toDouble()))
                    .toFloat()
                Frame(scaleX = s, scaleY = s, alpha = (p * 2f).coerceAtMost(1f))
            }

            "rotate3D" -> Frame(rotationZ = p * 360f)
            "yAxisFlip" -> Frame(rotationY = (1f - p) * 180f, alpha = p)
            "xAxisFlip" -> Frame(rotationX = (1f - p) * 180f, alpha = p)
            "vortexSpin" -> Frame(
                scaleX = eOB.coerceAtLeast(0.01f),
                scaleY = eOB.coerceAtLeast(0.01f),
                rotationZ = p * 720f,
                alpha = (p * 2.5f).coerceAtMost(1f)
            )

            "zAxisSpin" -> Frame(rotationZ = p * 360f)
            "spiralIn" -> Frame(
                scaleX = eOC.coerceAtLeast(0.01f),
                scaleY = eOC.coerceAtLeast(0.01f),
                rotationZ = (1f - p) * 720f,
                translateY = (1f - eOC) * 100f,
                alpha = eOC
            )

            "tornado" -> Frame(
                scaleX = 0.3f + 0.7f * eOB,
                scaleY = 0.3f + 0.7f * eOB,
                rotationZ = p * 720f,
                alpha = (p * 2f).coerceAtMost(1f)
            )

            "skewSpin" -> Frame(
                scaleX = 0.5f + 0.5f * eOC,
                scaleY = 0.5f + 0.5f * eOC,
                rotationZ = -(1f - eOC) * 90f,
                alpha = eOC
            )

            "pendulum" -> Frame(rotationZ = Math.sin((elapsedSec * 3f).toDouble()).toFloat() * 8f)
            "propeller" -> Frame(
                rotationZ = eOC * 1440f,
                scaleX = 0.3f + 0.7f * eOC,
                scaleY = 0.3f + 0.7f * eOC,
                alpha = (p * 2f).coerceAtMost(1f)
            )

            "barrelRoll" -> Frame(
                rotationZ = eOC * 720f,
                translateX = Math.sin((eOC * Math.PI).toDouble()).toFloat() * 100f,
                alpha = (p * 2f).coerceAtMost(1f)
            )

            "cubeRoll" -> {
                val s = 0.01f + 0.99f * Math.abs(Math.cos(((1f - p) * Math.PI / 2f).toDouble()))
                    .toFloat()
                Frame(scaleX = s, scaleY = s, alpha = (p * 2f).coerceAtMost(1f))
            }

            "gentleTilt" -> Frame(rotationZ = Math.sin((elapsedSec * 3f).toDouble()).toFloat() * 5f)
            "twister" -> Frame(
                scaleX = eOC.coerceAtLeast(0.01f),
                scaleY = eOC.coerceAtLeast(0.01f),
                alpha = (p * 2f).coerceAtMost(1f)
            )

            // ═══ ZOOMS ═══
            "zoomIn" -> Frame(alpha = p, scaleX = 0.3f + 0.7f * p, scaleY = 0.3f + 0.7f * p)
            "zoomOut" -> Frame(alpha = p, scaleX = 2f - p, scaleY = 2f - p)
            "cinematicZoom" -> Frame(
                scaleX = 1.6f - 0.6f * eOC,
                scaleY = 1.6f - 0.6f * eOC,
                alpha = (p * 2f).coerceAtMost(1f),
                blurRadiusPx = (1f - eOC) * 10f
            )

            "hyperZoomOut" -> {
                val e = ease((p / 0.7f).coerceAtMost(1f), "easeOutCubic")
                Frame(
                    scaleX = 8f - 7f * e,
                    scaleY = 8f - 7f * e,
                    alpha = (p * 2f).coerceAtMost(1f),
                    blurRadiusPx = (1f - e) * 20f
                )
            }

            "pulseScale" -> Frame(
                scaleX = 0.7f + 0.3f * eOB + (if (p > 0.4f) Math.sin((elapsedSec * 6f).toDouble())
                    .toFloat() * 0.03f else 0f),
                scaleY = 0.7f + 0.3f * eOB + (if (p > 0.4f) Math.sin((elapsedSec * 6f).toDouble())
                    .toFloat() * 0.03f else 0f),
                alpha = (p * 2f).coerceAtMost(1f)
            )

            "elasticZoom" -> Frame(
                scaleX = 0.1f + 0.9f * eOB,
                scaleY = 0.1f + 0.9f * eOB,
                alpha = (p * 2.5f).coerceAtMost(1f)
            )

            "lensFlareZoom" -> Frame(
                scaleX = 0.2f + 0.8f * eOC,
                scaleY = 0.2f + 0.8f * eOC,
                alpha = (p * 2f).coerceAtMost(1f)
            )

            "shrinkReveal" -> Frame(
                scaleX = 6f - 5f * eOC,
                scaleY = 6f - 5f * eOC,
                alpha = eOC,
                blurRadiusPx = (1f - eOC) * 12f
            )

            "popScale" -> Frame(
                scaleX = eOB.coerceAtLeast(0.01f),
                scaleY = eOB.coerceAtLeast(0.01f),
                alpha = (p * 2.5f).coerceAtMost(1f)
            )

            "depthZoom" -> Frame(
                scaleX = 0.5f + 0.5f * eOC,
                scaleY = 0.5f + 0.5f * eOC,
                alpha = (p * 2f).coerceAtMost(1f)
            )

            "snapZoom" -> {
                val e = ease((p / 0.5f).coerceAtMost(1f), "easeOutCubic")
                Frame(
                    scaleX = 3f - 2f * e,
                    scaleY = 3f - 2f * e,
                    alpha = (p * 3f).coerceAtMost(1f),
                    blurRadiusPx = (1f - e) * 8f
                )
            }

            // ═══ SPECIAL ═══
            "scribble" -> Frame(alpha = 0.2f + 0.8f * p)
            "neonGlow" -> Frame(
                alpha = 0.85f + Math.sin((elapsedSec * 6f).toDouble()).toFloat() * 0.15f
            )

            "gradientShift" -> Frame(alpha = 1f)
            "ghostTrail" -> Frame(alpha = p, translateX = -(1f - p) * 40f)
            "silhouette" -> Frame(alpha = 0.3f + 0.7f * p)
            "explosion" -> Frame(
                scaleX = 1f + 1.5f * eIC,
                scaleY = 1f + 1.5f * eIC,
                rotationZ = eIC * 20f,
                alpha = 1f - eIC,
                blurRadiusPx = eIC * 15f
            )

            "implosion" -> Frame(
                scaleX = 3f - 2f * eOC,
                scaleY = 3f - 2f * eOC,
                rotationZ = -(1f - eOC) * 20f,
                alpha = eOC,
                blurRadiusPx = (1f - eOC) * 15f
            )

            "pulse" -> Frame(
                scaleX = 1f + Math.sin((elapsedSec * 3f).toDouble()).toFloat() * 0.1f,
                scaleY = 1f + Math.sin((elapsedSec * 3f).toDouble()).toFloat() * 0.1f
            )

            "shake" -> Frame(translateX = Math.sin((elapsedSec * 40f).toDouble()).toFloat() * 6f)

            else -> Frame(alpha = p)
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  MODIFIER EXTENSION — apply animation to any Composable
    //  Mirrors JS applyAnimation(el, key, duration)
    // ═══════════════════════════════════════════════════════════
    fun Modifier.applyAnimation(
        animation: String,
        progress: Float,
        elapsedSec: Float = 0f
    ): Modifier = composed {
        if (LocalInspectionMode.current) return@composed this
        val frame = remember(animation, progress, elapsedSec) {
            computeFrame(animation, progress, elapsedSec)
        }
        this.graphicsLayer {
            alpha = frame.alpha
            scaleX = frame.scaleX
            scaleY = frame.scaleY
            translationX = frame.translateX
            translationY = frame.translateY
            rotationZ = frame.rotationZ
            rotationX = frame.rotationX
            rotationY = frame.rotationY
            transformOrigin = TransformOrigin.Center
        }
    }
}