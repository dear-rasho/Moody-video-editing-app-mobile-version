package com.moody.moodyvideoeditor.utils

import android.graphics.ColorMatrix
import com.moody.moodyvideoeditor.data.FilterState

/**
 * Mirrors js/workspace/effectRenderer.js buildCssFilter()
 * Converts FilterState → ColorMatrix for hardware-accelerated rendering.
 */
object FiltersEngine {

    /**
     * Build Android ColorMatrix from FilterState.
     * brightness/contrast/saturation/hue/grayscale/sepia/invert are supported.
     * blur is NOT a ColorMatrix — must be applied separately via BlurEffect.
     * opacity is applied via alpha layer.
     */
    fun buildColorMatrix(f: FilterState): ColorMatrix {
        val cm = ColorMatrix()

        // 1) BRIGHTNESS (0..200, def 100 → multiplier 0..2)
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

        // 2) CONTRAST (0..200, def 100 → scale 0..2)
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

        // 3) SATURATION (0..200, def 100)
        if (f.saturation != 100f) {
            val sCM = ColorMatrix()
            sCM.setSaturation(f.saturation / 100f)
            cm.postConcat(sCM)
        }

        // 4) HUE (0..360, def 0)
        if (f.hue != 0f) {
            val hueCM = ColorMatrix()
            hueCM.setRotate(0, f.hue)
            cm.postConcat(hueCM)
        }

        // 5) GRAYSCALE (0..100, def 0)
        if (f.grayscale > 0f) {
            val amount = f.grayscale / 100f
            val grayCM = ColorMatrix()
            grayCM.setSaturation(1f - amount)
            cm.postConcat(grayCM)
        }

        // 6) SEPIA (0..100, def 0)
        if (f.sepia > 0f) {
            val a = (f.sepia / 100f).coerceIn(0f, 1f)
            // Sepia base matrix (luminance-preserving)
            val sr = 0.393f
            val sg = 0.769f
            val sb = 0.189f
            val mr = 0.349f
            val mg = 0.686f
            val mb = 0.168f
            val hr = 0.272f
            val hg = 0.534f
            val hb = 0.131f
            val sepiaCM = ColorMatrix(
                floatArrayOf(
                    (1 - a) + a * sr, a * sg, a * sb, 0f, 0f,
                    a * mr, (1 - a) + a * mg, a * mb, 0f, 0f,
                    a * hr, a * hg, (1 - a) + a * hb, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            cm.postConcat(sepiaCM)
        }

        // 7) INVERT (0..100, def 0)
        if (f.invert > 0f) {
            val a = (f.invert / 100f).coerceIn(0f, 1f)
            // Blend between identity and full invert
            val s = 1f - 2f * a
            val t = 255f * a
            val invCM = ColorMatrix(
                floatArrayOf(
                    s, 0f, 0f, 0f, t,
                    0f, s, 0f, 0f, t,
                    0f, 0f, s, 0f, t,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            cm.postConcat(invCM)
        }

        return cm
    }

    /**
     * Does this filter set need real-time rendering?
     * Mirrors JS hasAnyChange()
     */
    fun hasRealTimeFilters(f: FilterState): Boolean {
        return f.brightness != 100f || f.contrast != 100f || f.saturation != 100f ||
                f.hue != 0f || f.grayscale != 0f || f.sepia != 0f || f.invert != 0f
    }

    /**
     * Blur radius in dp (Compose BlurEffect).
     * Only used when f.blur > 0.
     */
    fun blurRadiusDp(f: FilterState): Float = f.blur.coerceAtLeast(0f)

    /**
     * Alpha for the layer — from opacity.
     */
    fun opacityAlpha(f: FilterState): Float = (f.opacity / 100f).coerceIn(0f, 1f)
}