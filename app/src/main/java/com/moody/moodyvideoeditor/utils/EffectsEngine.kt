package com.moody.moodyvideoeditor.utils

import android.graphics.ColorMatrix
import com.moody.moodyvideoeditor.data.ColorFilterValues
import com.moody.moodyvideoeditor.data.EditorClip
import com.moody.moodyvideoeditor.data.MotionConfig
import com.moody.moodyvideoeditor.data.OverlayConfig
import com.moody.moodyvideoeditor.data.OverlayState
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Mirrors js/workspace/effectRenderer.js
 * - Motion computation + combination
 * - Filter ColorMatrix builder
 * - Hierarchy: getEffectsAbove()
 * - Multi-effect stacking
 */
object EffectsEngine {

    // ═══════════════════════════════════════════════════════════
    //  MOTION FRAME — mirrors effectRenderer.js computeMotion()
    // ═══════════════════════════════════════════════════════════
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

    /**
     * Mirrors effectRenderer.js "combineMotions()"
     * tx/ty add, scale multiply, rotation add.
     */
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

    // ═══════════════════════════════════════════════════════════
    //  HIERARCHY — mirrors effectRenderer.js getEffectsAbove()
    //  Sirf woh effect clips jo video ke track se UPAR hain.
    // ═══════════════════════════════════════════════════════════
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

    // ═══════════════════════════════════════════════════════════
    //  FILTER COMBINING — accumulate multiple effect filters
    // ═══════════════════════════════════════════════════════════
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

    // ═══════════════════════════════════════════════════════════
    //  COLOR MATRIX — mirrors effectRenderer.js buildCssFilter()
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
            val hCM = ColorMatrix()
            hCM.setRotate(0, f.hue)
            cm.postConcat(hCM)
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

    fun hasColorEffect(f: ColorFilterValues?): Boolean {
        if (f == null) return false
        return f.brightness != 100f || f.contrast != 100f || f.saturation != 100f ||
                f.hue != 0f || f.grayscale != 0f || f.sepia != 0f || f.invert != 0f
    }

    fun opacityAlpha(f: ColorFilterValues?): Float =
        ((f?.opacity ?: 100f) / 100f).coerceIn(0f, 1f)

    fun blurRadiusDp(f: ColorFilterValues?): Float = f?.blur ?: 0f

    // ═══════════════════════════════════════════════════════════
    //  OVERLAY COLLECTING — returns OverlayState (ready for renderer)
    // ═══════════════════════════════════════════════════════════
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
}