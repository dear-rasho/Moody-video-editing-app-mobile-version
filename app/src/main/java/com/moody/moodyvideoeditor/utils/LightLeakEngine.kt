package com.moody.moodyvideoeditor.utils

import androidx.compose.ui.graphics.Color
import com.moody.moodyvideoeditor.data.LightLeakAnimation
import com.moody.moodyvideoeditor.data.LightLeakConfig
import com.moody.moodyvideoeditor.data.LightLeakState
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

object LightLeakEngine {

    data class Frame(
        val color1: Color,
        val color2: Color,
        val posX: Float,
        val posY: Float,
        val radius: Float,
        val alpha: Float,
        val intensityMul: Float
    )

    fun computeFrame(config: LightLeakConfig, timeSec: Float): Frame {
        val t = timeSec * config.speed
        val c1 = Color(config.color1)
        val c2 = Color(config.color2)

        return when (config.animation) {
            LightLeakAnimation.STATIC ->
                Frame(c1, c2, config.positionX, config.positionY, config.radius, 1f, 1f)

            LightLeakAnimation.PULSE -> {
                val s = sin(t * 3f) * 0.5f + 0.5f
                Frame(
                    c1, c2, config.positionX, config.positionY,
                    config.radius * (0.85f + 0.30f * s),
                    0.70f + 0.30f * s, 0.80f + 0.40f * s
                )
            }

            LightLeakAnimation.BREATHE -> {
                val s = sin(t * 1.5f) * 0.5f + 0.5f
                Frame(
                    c1, c2, config.positionX, config.positionY,
                    config.radius * (0.70f + 0.60f * s),
                    0.75f + 0.25f * s, 0.70f + 0.60f * s
                )
            }

            LightLeakAnimation.FLICKER -> {
                val n = hash01((t * 8f).toDouble()).toFloat()
                Frame(
                    c1, c2, config.positionX, config.positionY,
                    config.radius * (0.90f + 0.20f * n),
                    0.35f + 0.65f * n, 0.80f + 0.40f * n
                )
            }

            LightLeakAnimation.DRIFT -> {
                val dx = sin(t * 0.6f) * 0.15f
                val dy = cos(t * 0.5f) * 0.10f
                Frame(
                    c1, c2,
                    (config.positionX + dx).coerceIn(0f, 1f),
                    (config.positionY + dy).coerceIn(0f, 1f),
                    config.radius, 1f, 1f
                )
            }

            LightLeakAnimation.SWEEP -> {
                val phase = (t * 0.3f) % 1f
                val vis = sin(phase * Math.PI.toFloat())
                Frame(
                    c1, c2, phase, config.positionY, config.radius,
                    (0.4f + 0.6f * vis).coerceIn(0f, 1f), 1f
                )
            }
        }
    }

    fun buildFfmpegFilter(state: LightLeakState): String {
        return try {
            val cfg = state.config
            val opacityMul = (state.opacity / 100f).coerceIn(0f, 1f)
            val intensityMul = (cfg.intensity / 100f).coerceIn(0f, 2f)
            val alpha = (opacityMul * intensityMul * 0.35f).coerceIn(0f, 0.5f)

            val r1 = ((cfg.color1 shr 16) and 0xFF).toInt()
            val g1 = ((cfg.color1 shr 8) and 0xFF).toInt()
            val b1 = (cfg.color1 and 0xFF).toInt()
            val r2 = ((cfg.color2 shr 16) and 0xFF).toInt()
            val g2 = ((cfg.color2 shr 8) and 0xFF).toInt()
            val b2 = (cfg.color2 and 0xFF).toInt()

            val rAvg = (r1 + r2) / 2
            val gAvg = (g1 + g2) / 2
            val bAvg = (b1 + b2) / 2

            val rShift = ((rAvg / 255f) - 0.5f) * alpha * 2f
            val gShift = ((gAvg / 255f) - 0.5f) * alpha * 2f
            val bShift = ((bAvg / 255f) - 0.5f) * alpha * 2f

            val loc = java.util.Locale.US
            "colorbalance=" +
                    "rs=${"%.3f".format(loc, rShift)}:" +
                    "gs=${"%.3f".format(loc, gShift)}:" +
                    "bs=${"%.3f".format(loc, bShift)}"
        } catch (_: Throwable) {
            ""
        }
    }

    private fun hash01(n: Double): Double {
        val x = sin(n * 12.9898 + 78.233) * 43758.5453
        return x - floor(x)
    }
}