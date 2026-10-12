package com.moody.moodyvideoeditor.utils

import com.moody.moodyvideoeditor.data.ColorFilterValues
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

// ═══════════════════════════════════════════════════════════════
//  ANIMATED COLOR ENGINE
//  Time-varying ColorFilterValues for dynamic color effects.
// ═══════════════════════════════════════════════════════════════

data class AnimatedColorConfig(
    val type: String,
    val speed: Float = 1.0f,
    val intensity: Float = 100f
)

object AnimatedColorEngine {

    fun compute(cfg: AnimatedColorConfig, timeSec: Float): ColorFilterValues {
        val t = timeSec * cfg.speed.coerceIn(0.2f, 5f)
        val I = (cfg.intensity / 100f).coerceIn(0f, 2f)
        if (I <= 0f) return ColorFilterValues()

        return when (cfg.type) {
            // Hue rotation
            "hueCycle" -> ColorFilterValues(
                hue = ((t * 60f) % 360f) * I
            )

            "hueFast" -> ColorFilterValues(
                hue = ((t * 200f) % 360f) * I
            )

            "hueReverse" -> ColorFilterValues(
                hue = ((360f - (t * 60f) % 360f)) * I
            )

            // Rainbow
            "rainbowFlow" -> {
                val s = 100f + sin(t * 2f) * 40f * I
                ColorFilterValues(
                    hue = ((t * 90f) % 360f),
                    saturation = s.coerceIn(50f, 200f)
                )
            }

            // Saturation
            "satPulse" -> {
                val s = 100f + sin(t * 3f) * 60f * I
                ColorFilterValues(saturation = s.coerceIn(0f, 200f))
            }

            "satFade" -> {
                val s = 100f - abs(sin(t * 2f)) * 70f * I
                ColorFilterValues(saturation = s.coerceIn(0f, 200f))
            }

            // Brightness
            "brightPulse" -> {
                val b = 100f + sin(t * 3f) * 30f * I
                ColorFilterValues(brightness = b.coerceIn(20f, 200f))
            }

            "brightFlicker" -> {
                val b = 100f +
                        (sin(t * 20f) + sin(t * 37f) * 0.5f) * 30f * I
                ColorFilterValues(brightness = b.coerceIn(20f, 200f))
            }

            // Warm ↔ Cool
            "warmCool" -> {
                val s = sin(t * 1.5f)
                ColorFilterValues(
                    hue = (s * 45f * I).coerceIn(-180f, 180f),
                    saturation = (100f + abs(s) * 30f * I)
                        .coerceIn(50f, 200f)
                )
            }

            // Sunset shift
            "sunsetShift" -> {
                val phase = (t * 0.5f) % 1f
                ColorFilterValues(
                    hue = (phase * 300f) % 360f,
                    brightness = 100f + sin(t * 0.5f) * 15f * I,
                    saturation = 120f + phase * 30f
                )
            }

            // Color strobe
            "colorStrobe" -> {
                val on = (floor(t * 8f).toInt() % 2) == 0
                if (on) {
                    ColorFilterValues(
                        saturation = 180f * I.coerceAtMost(1f) + 20f,
                        contrast = 130f
                    )
                } else {
                    ColorFilterValues(
                        saturation = 40f,
                        brightness = 80f
                    )
                }
            }

            // Disco
            "disco" -> {
                val seed = floor(t * 4f).toInt()
                val hue = (hash(seed.toDouble()) * 360f).toFloat()
                ColorFilterValues(
                    hue = hue,
                    saturation = 140f
                )
            }

            // Neon pulse
            "neonPulse" -> {
                val s = sin(t * 4f) * 0.5f + 0.5f
                ColorFilterValues(
                    saturation = 100f + s * 100f * I,
                    brightness = 100f + s * 20f * I
                )
            }

            // Chroma wave
            "chromaWave" -> ColorFilterValues(
                hue = sin(t * 2f) * 90f * I,
                brightness = 100f + cos(t * 3f) * 15f * I,
                saturation = 120f
            )

            // Fire flicker
            "fireFlicker" -> {
                val flick = hash((t * 15f).toDouble()).toFloat()
                ColorFilterValues(
                    hue = -15f - flick * 20f * I,
                    brightness = 100f + flick * 20f * I,
                    saturation = 130f + flick * 40f * I
                )
            }

            // Ice pulse
            "icePulse" -> {
                val s = sin(t * 2f) * 0.5f + 0.5f
                ColorFilterValues(
                    hue = 180f + s * 40f,
                    brightness = 95f + s * 20f * I,
                    saturation = 90f + s * 50f * I
                )
            }

            // Cyber shift
            "cyberShift" -> {
                val s = sin(t * 1.5f) * 0.5f + 0.5f
                ColorFilterValues(
                    hue = (300f + s * 120f) % 360f,
                    saturation = 150f,
                    contrast = 120f
                )
            }

            // Ocean wave
            "oceanWave" -> {
                val s = sin(t * 0.8f) * 0.5f + 0.5f
                ColorFilterValues(
                    hue = 180f + s * 40f,
                    saturation = 100f + s * 40f,
                    brightness = 95f + s * 15f
                )
            }

            // Heartbeat
            "heartbeatColor" -> {
                val beat = floor(t * 1.5f).toInt() % 2
                val pulse = if (beat == 0) {
                    t * 1.5f - floor(t * 1.5f)
                } else 0f
                val intensityP = (1f - pulse.coerceIn(0f, 1f))
                    .pow(2f)
                ColorFilterValues(
                    saturation = 100f + intensityP * 80f * I,
                    brightness = 100f + intensityP * 15f * I,
                    contrast = 110f
                )
            }

            // Sepia pulse
            "sepiaPulse" -> {
                val s = sin(t * 2f) * 0.5f + 0.5f
                ColorFilterValues(
                    sepia = s * 80f * I,
                    saturation = 100f - s * 30f
                )
            }

            // Invert flash
            "invertFlash" -> {
                val on = (floor(t * 2f).toInt() % 2) == 0
                ColorFilterValues(invert = if (on) 100f else 0f)
            }

            // Vivid breathing
            "vividBreath" -> {
                val s = sin(t * 1.2f) * 0.5f + 0.5f
                ColorFilterValues(
                    saturation = 90f + s * 80f,
                    brightness = 100f + s * 10f,
                    contrast = 100f + s * 20f
                )
            }

            // Gold shimmer
            "goldShimmer" -> {
                val s = sin(t * 3f) * 0.5f + 0.5f
                ColorFilterValues(
                    hue = 30f + s * 20f,
                    brightness = 100f + s * 15f,
                    saturation = 120f + s * 40f,
                    sepia = s * 15f
                )
            }

            // Blood pulse
            "bloodPulse" -> {
                val s = sin(t * 2f) * 0.5f + 0.5f
                ColorFilterValues(
                    hue = 0f - s * 15f,
                    saturation = 130f + s * 60f * I,
                    contrast = 110f + s * 20f
                )
            }

            // Purple haze
            "purpleHaze" -> {
                val s = sin(t * 0.7f) * 0.5f + 0.5f
                ColorFilterValues(
                    hue = 280f + s * 40f,
                    saturation = 120f + s * 30f,
                    brightness = 95f + s * 10f
                )
            }

            // Toxic green
            "toxicGreen" -> {
                val s = sin(t * 2.5f) * 0.5f + 0.5f
                ColorFilterValues(
                    hue = 90f + s * 30f,
                    saturation = 140f + s * 40f,
                    contrast = 115f
                )
            }

            else -> ColorFilterValues()
        }
    }

    private fun hash(n: Double): Double {
        val x = sin(n * 12.9898 + 78.233) * 43758.5453
        return x - floor(x)
    }
}