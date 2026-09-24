package com.moody.moodyvideoeditor.utils

import android.graphics.ColorMatrix
import com.moody.moodyvideoeditor.data.ColorFilterValues
import com.moody.moodyvideoeditor.data.MotionConfig
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Mirrors js/workspace/effectRenderer.js
 * Motion + color filter computation for all preset-based effects.
 */
object EffectsEngine {

    // ═══════════════════════════════════════════════════════════
    //  MOTION FRAME — mirrors effectRenderer.js computeMotion()
    // ═══════════════════════════════════════════════════════════
    data class MotionFrame(
        val translateX: Float = 0f,
        val translateY: Float = 0f,
        val scale: Float = 1f,
        val rotationDeg: Float = 0f
    )

    fun computeMotion(m: MotionConfig?, timeSec: Float): MotionFrame {
        if (m == null) return MotionFrame()
        val speed = m.speed
        val intensity = m.intensity / 100f
        val t = timeSec * speed

        return when (m.type) {
            "shake" -> MotionFrame(
                translateX = (sin(t * 37.0) * 6.0 * intensity).toFloat(),
                translateY = (cos(t * 41.0) * 6.0 * intensity).toFloat()
            )

            "bounce" -> MotionFrame(
                scale = (1.0 + abs(sin(t * 4.0)) * 0.12 * intensity).toFloat()
            )

            "pulse" -> MotionFrame(
                scale = (1.0 + sin(t * 3.0) * 0.08 * intensity).toFloat()
            )

            "zoomPulse" -> MotionFrame(
                scale = (1.0 + (sin(t * 2.0) * 0.5 + 0.5) * 0.35 * intensity).toFloat()
            )

            "rotate" -> MotionFrame(
                rotationDeg = (sin(t * 2.0) * 6.0 * intensity).toFloat()
            )

            "glitch" -> MotionFrame(
                translateX = ((Math.random() - 0.5) * 14.0 * intensity).toFloat(),
                translateY = ((Math.random() - 0.5) * 8.0 * intensity).toFloat(),
                scale = (1.0 + (Math.random() - 0.5) * 0.03 * intensity).toFloat()
            )

            else -> MotionFrame()
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  COLOR FILTER — mirrors effectRenderer.js buildCssFilter()
    // ═══════════════════════════════════════════════════════════
    fun buildColorMatrix(f: ColorFilterValues?): ColorMatrix {
        val cm = ColorMatrix()
        if (f == null) return cm

        if (f.brightness != 100f) {
            val m = f.brightness / 100f
            cm.postConcat(
                ColorMatrix(
                    floatArrayOf(
                        m, 0f, 0f, 0f, 0f,
                        0f, m, 0f, 0f, 0f,
                        0f, 0f, m, 0f, 0f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            )
        }

        if (f.contrast != 100f) {
            val s = f.contrast / 100f
            val t = (1f - s) * 128f
            cm.postConcat(
                ColorMatrix(
                    floatArrayOf(
                        s, 0f, 0f, 0f, t,
                        0f, s, 0f, 0f, t,
                        0f, 0f, s, 0f, t,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            )
        }

        if (f.saturation != 100f) {
            val sCM = ColorMatrix()
            sCM.setSaturation(f.saturation / 100f)
            cm.postConcat(sCM)
        }

        if (f.hue != 0f) {
            val hueCM = ColorMatrix()
            hueCM.setRotate(0, f.hue)
            cm.postConcat(hueCM)
        }

        if (f.grayscale > 0f) {
            val g = f.grayscale / 100f
            val grayCM = ColorMatrix()
            grayCM.setSaturation(1f - g)
            cm.postConcat(grayCM)
        }

        if (f.sepia > 0f) {
            val a = (f.sepia / 100f).coerceIn(0f, 1f)
            val sr = 0.393f
            val sg = 0.769f
            val sb = 0.189f
            val mr = 0.349f
            val mg = 0.686f
            val mb = 0.168f
            val hr = 0.272f
            val hg = 0.534f
            val hb = 0.131f
            cm.postConcat(
                ColorMatrix(
                    floatArrayOf(
                        (1 - a) + a * sr, a * sg, a * sb, 0f, 0f,
                        a * mr, (1 - a) + a * mg, a * mb, 0f, 0f,
                        a * hr, a * hg, (1 - a) + a * hb, 0f, 0f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            )
        }

        if (f.invert > 0f) {
            val a = (f.invert / 100f).coerceIn(0f, 1f)
            val s = 1f - 2f * a
            val t = 255f * a
            cm.postConcat(
                ColorMatrix(
                    floatArrayOf(
                        s, 0f, 0f, 0f, t,
                        0f, s, 0f, 0f, t,
                        0f, 0f, s, 0f, t,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            )
        }

        return cm
    }

    fun hasColorEffect(f: ColorFilterValues?): Boolean {
        if (f == null) return false
        return f.brightness != 100f || f.contrast != 100f || f.saturation != 100f ||
                f.hue != 0f || f.grayscale != 0f || f.sepia != 0f || f.invert != 0f
    }

    fun blurRadiusDp(f: ColorFilterValues?): Float = f?.blur ?: 0f

    fun opacityAlpha(f: ColorFilterValues?): Float =
        ((f?.opacity ?: 100f) / 100f).coerceIn(0f, 1f)

    // ═══════════════════════════════════════════════════════════
    //  OVERLAY DRAWING — mirrors overlayRenderer.js
    //  All deterministic: hash(i) so preview == export
    // ═══════════════════════════════════════════════════════════
    fun hash(n: Double): Double {
        val x = sin(n * 12.9898 + 78.233) * 43758.5453
        return x - kotlin.math.floor(x)
    }

    // ═══════════════════════════════════════════════════════════
    //  ACTIVE EFFECT STATE per effect clip
    // ═══════════════════════════════════════════════════════════
    fun applyPresetFiltersToState(
        currentBrightness: Float = 100f,
        currentContrast: Float = 100f,
        currentSaturation: Float = 100f,
        currentHue: Float = 0f,
        currentGrayscale: Float = 0f,
        currentSepia: Float = 0f,
        currentInvert: Float = 0f,
        currentBlur: Float = 0f,
        currentOpacity: Float = 100f,
        preset: ColorFilterValues
    ): ColorFilterValues {
        // Preset OVERRIDES — mirrors JS replace semantics for preset tap
        return preset
    }
}