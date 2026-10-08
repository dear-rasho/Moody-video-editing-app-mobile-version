package com.moody.moodyvideoeditor.utils

import android.graphics.ColorMatrix
import androidx.compose.ui.graphics.Color
import com.moody.moodyvideoeditor.data.ColorFilterValues
import com.moody.moodyvideoeditor.data.EditorClip
import com.moody.moodyvideoeditor.data.MotionConfig
import com.moody.moodyvideoeditor.data.OverlayConfig
import com.moody.moodyvideoeditor.data.OverlayState
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin


// Mirrors js/workspace/effectRenderer.js
// - Motion computation + combination
// - Filter ColorMatrix builder
// - Hierarchy: getEffectsAbove()
// - Multi-effect stacking
object EffectsEngine {


    //  MOTION FRAME — mirrors effectRenderer.js computeMotion()

    data class MotionFrame(
        val tx: Float = 0f,
        val ty: Float = 0f,
        val scale: Float = 1f,
        val rotation: Float = 0f
    )

    fun computeMotion(m: MotionConfig?, timeSec: Float): MotionFrame {
        if (m == null) return MotionFrame()
        val speed = m.speed
        val I = m.intensity / 100f
        val t = timeSec * speed

        return when (m.type) {
            "shake" -> MotionFrame(
                tx = (sin(t * 37.0) * 6.0 * I).toFloat(),
                ty = (cos(t * 41.0) * 6.0 * I).toFloat()
            )

            "bounce" -> MotionFrame(
                scale = (1.0 + abs(sin(t * 4.0)) * 0.12 * I).toFloat()
            )

            "pulse" -> MotionFrame(
                scale = (1.0 + sin(t * 3.0) * 0.08 * I).toFloat()
            )

            "zoomPulse" -> MotionFrame(
                scale = (1.0 + (sin(t * 2.0) * 0.5 + 0.5) * 0.35 * I).toFloat()
            )

            "rotate" -> MotionFrame(
                rotation = (sin(t * 2.0) * 6.0 * I).toFloat()
            )

            "glitch" -> MotionFrame(
                tx = ((Math.random() - 0.5) * 14.0 * I).toFloat(),
                ty = ((Math.random() - 0.5) * 8.0 * I).toFloat(),
                scale = (1.0 + (Math.random() - 0.5) * 0.03 * I).toFloat()
            )

            else -> MotionFrame()
        }
    }

    // Mirrors effectRenderer.js "combineMotions()"
    // tx/ty add, scale multiply, rotation add.
    fun combineMotions(frames: List<MotionFrame>): MotionFrame {
        if (frames.isEmpty()) return MotionFrame()
        var tx = 0f
        var ty = 0f
        var scale = 1f
        var rot = 0f
        frames.forEach { f ->
            tx += f.tx
            ty += f.ty
            scale *= f.scale
            rot += f.rotation
        }
        return MotionFrame(tx, ty, scale, rot)
    }


    //  HIERARCHY — mirrors effectRenderer.js getEffectsAbove()
    //  Sirf woh effect clips jo video ke track se UPAR hain.

    fun getEffectsAbove(
        clips: List<EditorClip>,
        timeMs: Long,
        videoTrackIdx: Int
    ): List<EditorClip> {
        return clips
            .filter {
                it.isEffectClip &&
                        it.trackIndex > videoTrackIdx &&
                        timeMs >= it.timelineStartMs &&
                        timeMs < it.timelineEndMs
            }
            .sortedBy { it.trackIndex }
    }


    //  FILTER COMBINING — accumulate multiple effect filters

    fun combineFilters(list: List<ColorFilterValues>): ColorFilterValues {
        if (list.isEmpty()) return ColorFilterValues()
        var b = 100f
        var c = 100f
        var s = 100f
        var h = 0f
        var g = 0f
        var sp = 0f
        var inv = 0f
        var bl = 0f
        var op = 100f

        list.forEach {
            b *= (it.brightness / 100f)
            c *= (it.contrast / 100f)
            s *= (it.saturation / 100f)
            h += it.hue
            g = maxOf(g, it.grayscale)
            sp = maxOf(sp, it.sepia)
            inv = maxOf(inv, it.invert)
            bl = maxOf(bl, it.blur)
            op = minOf(op, it.opacity)
        }
        return ColorFilterValues(
            brightness = b.coerceIn(0f, 300f),
            contrast = c.coerceIn(0f, 300f),
            saturation = s.coerceIn(0f, 300f),
            hue = ((h % 360f) + 360f) % 360f,
            grayscale = g,
            sepia = sp,
            invert = inv,
            blur = bl,
            opacity = op
        )
    }


    //  COLOR MATRIX — mirrors effectRenderer.js buildCssFilter()

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
            cm.postConcat(buildHueRotateMatrix(f.hue))
        }
        if (f.grayscale > 0f) {
            val gCM = ColorMatrix()
            gCM.setSaturation(1f - (f.grayscale / 100f))
            cm.postConcat(gCM)
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
                        (1f - a) + a * sr, a * sg, a * sb, 0f, 0f,
                        a * mr, (1f - a) + a * mg, a * mb, 0f, 0f,
                        a * hr, a * hg, (1f - a) + a * hb, 0f, 0f,
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

    // Proper hue rotation (CSS-style) — matches FFmpeg hue filter.
    private fun buildHueRotateMatrix(degrees: Float): ColorMatrix {
        val rad = Math.toRadians(degrees.toDouble())
        val cos = cos(rad).toFloat()
        val sin = sin(rad).toFloat()

        return ColorMatrix(
            floatArrayOf(
                0.213f + cos * 0.787f - sin * 0.213f,
                0.715f - cos * 0.715f - sin * 0.715f,
                0.072f - cos * 0.072f + sin * 0.928f,
                0f, 0f,

                0.213f - cos * 0.213f + sin * 0.143f,
                0.715f + cos * 0.285f + sin * 0.140f,
                0.072f - cos * 0.072f - sin * 0.283f,
                0f, 0f,

                0.213f - cos * 0.213f - sin * 0.787f,
                0.715f - cos * 0.715f + sin * 0.715f,
                0.072f + cos * 0.928f + sin * 0.072f,
                0f, 0f,

                0f, 0f, 0f, 1f, 0f
            )
        )
    }

    fun hasColorEffect(f: ColorFilterValues?): Boolean {
        if (f == null) return false
        return f.brightness != 100f || f.contrast != 100f || f.saturation != 100f ||
                f.hue != 0f || f.grayscale != 0f || f.sepia != 0f || f.invert != 0f
    }

    fun opacityAlpha(f: ColorFilterValues?): Float =
        ((f?.opacity ?: 100f) / 100f).coerceIn(0f, 1f)

    fun blurRadiusDp(f: ColorFilterValues?): Float = f?.blur ?: 0f


    //  OVERLAY COLLECTING — returns OverlayState (ready for renderer)

    fun collectActiveOverlays(
        clips: List<EditorClip>,
        timeMs: Long,
        videoTrackIdx: Int
    ): List<OverlayState> {
        val result = mutableListOf<OverlayState>()

        // 1) Overlays inside effect layers (above video) — convert OverlayConfig → OverlayState
        clips.filter {
            it.isEffectClip &&
                    it.trackIndex > videoTrackIdx &&
                    timeMs >= it.timelineStartMs && timeMs < it.timelineEndMs
        }.sortedBy { it.trackIndex }
            .forEach { clip ->
                clip.effectState?.overlay?.let { cfg: OverlayConfig ->
                    result.add(
                        OverlayState(
                            type = cfg.type,
                            intensity = cfg.intensity,
                            color = cfg.color
                        )
                    )
                }
            }

        // 2) Dedicated overlay clips (also above video) — already OverlayState
        clips.filter {
            it.isOverlayClip &&
                    it.trackIndex > videoTrackIdx &&
                    timeMs >= it.timelineStartMs && timeMs < it.timelineEndMs
        }.sortedBy { it.trackIndex }
            .forEach { clip ->
                clip.overlay?.let { result.add(it) }
            }

        return result
    }
    // ═══════════════════════════════════════════════════════════════
//  EDGE GLOW — Frame computation
// ═══════════════════════════════════════════════════════════════

    data class EdgeGlowFrame(
        val color: Color,
        val radiusMul: Float = 1f,      // multiplier of config.radius
        val alpha: Float = 1f,          // 0..1
        val offsetX: Float = 0f,
        val offsetY: Float = 0f,
        val intensityMul: Float = 1f
    )

    fun computeEdgeGlow(
        config: com.moody.moodyvideoeditor.data.EdgeGlowConfig,
        timeSec: Float
    ): EdgeGlowFrame {
        val t = timeSec * config.speed
        val baseColor = Color(config.color)
        val c2 = Color(config.color2)

        return when (config.animation) {
            com.moody.moodyvideoeditor.data.EdgeGlowAnimation.STATIC ->
                EdgeGlowFrame(color = baseColor)

            com.moody.moodyvideoeditor.data.EdgeGlowAnimation.PULSE -> {
                val s = sin(t * 3.0f) * 0.5f + 0.5f
                EdgeGlowFrame(
                    color = baseColor,
                    radiusMul = 0.85f + 0.35f * s,
                    intensityMul = 0.8f + 0.4f * s
                )
            }

            com.moody.moodyvideoeditor.data.EdgeGlowAnimation.BREATHE -> {
                val s = sin(t * 1.5f) * 0.5f + 0.5f
                EdgeGlowFrame(
                    color = baseColor,
                    radiusMul = 0.7f + 0.7f * s,
                    alpha = 0.75f + 0.25f * s,
                    intensityMul = 0.6f + 0.6f * s
                )
            }

            com.moody.moodyvideoeditor.data.EdgeGlowAnimation.FLICKER -> {
                val n = hash01((t * 8.0f).toDouble()).toFloat()
                EdgeGlowFrame(
                    color = baseColor,
                    alpha = 0.35f + 0.65f * n,
                    radiusMul = 0.9f + 0.3f * n
                )
            }

            com.moody.moodyvideoeditor.data.EdgeGlowAnimation.STROBE -> {
                val on = (kotlin.math.floor((t * 6.0f).toDouble()).toInt() % 2) == 0
                EdgeGlowFrame(
                    color = baseColor,
                    alpha = if (on) 1f else 0f,
                    intensityMul = if (on) 1f else 0f
                )
            }

            com.moody.moodyvideoeditor.data.EdgeGlowAnimation.RAINBOW -> {
                val hue = ((t * 90f) % 360f + 360f) % 360f
                EdgeGlowFrame(
                    color = Color.hsv(hue, 0.9f, 1f),
                    radiusMul = 1f,
                    intensityMul = 1f
                )
            }

            com.moody.moodyvideoeditor.data.EdgeGlowAnimation.WAVE -> {
                val phase = t * 4.0f
                val dx = sin(phase) * 6.0f
                val dy = cos(phase * 0.9f) * 6.0f
                EdgeGlowFrame(
                    color = baseColor,
                    offsetX = dx,
                    offsetY = dy
                )
            }

            com.moody.moodyvideoeditor.data.EdgeGlowAnimation.SPARK -> {
                val n = hash01((t * 14.0f).toDouble()).toFloat()
                val spike = if (n > 0.75f) (n - 0.75f) * 4f else 0f
                EdgeGlowFrame(
                    color = baseColor,
                    radiusMul = 1f + spike * 0.6f,
                    intensityMul = 1f + spike
                )
            }

            com.moody.moodyvideoeditor.data.EdgeGlowAnimation.FIRE -> {
                val n1 = hash01((t * 6.0f).toDouble()).toFloat()
                val n2 = hash01((t * 11.0f).toDouble()).toFloat()
                val flame = n1 * 0.6f + n2 * 0.4f
                val mix = lerpColor(Color(0xFFFF6600), Color(0xFFFFCC00), flame)
                EdgeGlowFrame(
                    color = mix,
                    radiusMul = 0.9f + 0.5f * flame,
                    intensityMul = 0.85f + 0.35f * flame
                )
            }

            com.moody.moodyvideoeditor.data.EdgeGlowAnimation.ELECTRIC -> {
                val n = hash01((t * 20.0f).toDouble()).toFloat()
                val dx = (hash01((t * 40.0f).toDouble()).toFloat() - 0.5f) * 6f
                val dy = (hash01((t * 38.0f).toDouble()).toFloat() - 0.5f) * 6f
                EdgeGlowFrame(
                    color = baseColor,
                    radiusMul = 0.9f + 0.6f * n,
                    intensityMul = 0.7f + 0.8f * n,
                    offsetX = dx,
                    offsetY = dy
                )
            }

            com.moody.moodyvideoeditor.data.EdgeGlowAnimation.NEON -> {
                val subtle = sin(t * 1.2f) * 0.1f + 1.0f
                EdgeGlowFrame(
                    color = baseColor,
                    radiusMul = subtle,
                    intensityMul = 1f
                )
            }

            com.moody.moodyvideoeditor.data.EdgeGlowAnimation.AURORA -> {
                val phase = (t * 0.5f) % 1f
                val mix = lerpColor(baseColor, c2, phase)
                val pulse = sin(t * 1.5f) * 0.15f + 1.0f
                EdgeGlowFrame(
                    color = mix,
                    radiusMul = pulse,
                    intensityMul = 0.9f + 0.2f * pulse
                )
            }

            com.moody.moodyvideoeditor.data.EdgeGlowAnimation.GHOST -> {
                val s = sin(t * 1.2f) * 0.5f + 0.5f
                EdgeGlowFrame(
                    color = baseColor,
                    alpha = 0.35f + 0.45f * s,
                    radiusMul = 0.9f + 0.4f * s,
                    intensityMul = 0.5f + 0.5f * s
                )
            }

            com.moody.moodyvideoeditor.data.EdgeGlowAnimation.GLITCH -> {
                val on = hash01((t * 10.0f).toDouble()) > 0.4
                val dx = (hash01((t * 30.0f).toDouble()).toFloat() - 0.5f) * 10f
                EdgeGlowFrame(
                    color = if (on) baseColor else Color(0xFF00E5FF),
                    radiusMul = 1f,
                    intensityMul = if (on) 1f else 0.6f,
                    offsetX = dx,
                    offsetY = 0f
                )
            }
        }
    }

    private fun hash01(n: Double): Double {
        val x = sin(n * 12.9898 + 78.233) * 43758.5453
        return x - kotlin.math.floor(x)
    }

    private fun lerpColor(a: Color, b: Color, t: Float): Color {
        val tt = t.coerceIn(0f, 1f)
        val r = a.red + (b.red - a.red) * tt
        val g = a.green + (b.green - a.green) * tt
        val bl = a.blue + (b.blue - a.blue) * tt
        val al = a.alpha + (b.alpha - a.alpha) * tt
        return Color(r, g, bl, al)
    }
}