package com.moody.moodyvideoeditor.utils

import android.graphics.Paint
import com.moody.moodyvideoeditor.data.DustConfig
import com.moody.moodyvideoeditor.data.DustStyle
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

object DustEngine {

    private fun hash01(n: Double): Double {
        val x = sin(n * 12.9898 + 78.233) * 43758.5453
        return x - floor(x)
    }

    // ═══════════════════════════════════════════════════════════
    //  DRAW
    // ═══════════════════════════════════════════════════════════
    fun drawOnCanvas(
        canvas: android.graphics.Canvas,
        cfg: DustConfig,
        localTimeSec: Float,
        W: Int, H: Int
    ) {
        val intensity = (cfg.masterIntensity / 100f) *
                (cfg.intensity / 100f) *
                cfg.opacity
        if (intensity <= 0.01f) return

        when (cfg.style) {
            DustStyle.DUST_STORM -> drawWindParticles(
                canvas, cfg, localTimeSec, W, H, intensity, storm = true
            )
            DustStyle.SAND_STORM -> drawWindParticles(
                canvas, cfg, localTimeSec, W, H, intensity, storm = true
            )
            DustStyle.DUST_TRAIL -> drawBurstParticles(
                canvas, cfg, localTimeSec, W, H, intensity, trail = true
            )
            DustStyle.IMPACT_DUST -> drawBurstParticles(
                canvas, cfg, localTimeSec, W, H, intensity, trail = false
            )
            DustStyle.CHALK_DUST -> drawBurstParticles(
                canvas, cfg, localTimeSec, W, H, intensity, trail = false
            )
            DustStyle.FLOOR_DUST -> drawFloorDust(
                canvas, cfg, localTimeSec, W, H, intensity
            )
            else -> drawFloatingParticles(
                canvas, cfg, localTimeSec, W, H, intensity
            )
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  FLOATING — motes, golden, ash, cosmic
    // ═══════════════════════════════════════════════════════════
    private fun drawFloatingParticles(
        canvas: android.graphics.Canvas,
        cfg: DustConfig,
        timeSec: Float,
        W: Int, H: Int,
        intensity: Float
    ) {
        val t = timeSec
        val count = (cfg.count * intensity).toInt().coerceIn(5, 3000)
        val spreadRad = Math.toRadians(cfg.spread.toDouble()).toFloat()
        val dirRad = Math.toRadians(cfg.direction.toDouble()).toFloat()

        for (i in 0 until count) {
            val seed = i.toDouble()
            val seedF = i.toFloat()

            // Continuous looping particles (no burst)
            val periodSec = (cfg.lifetimeMs / 1000f) *
                    (0.8f + hash01(seed * 0.71).toFloat() * 0.4f)
            val phase = ((t / periodSec) +
                    hash01(seed * 1.31).toFloat()) % 1f

            val r1 = hash01(seed * 1.13).toFloat()
            val r2 = hash01(seed * 2.71).toFloat()
            val r3 = hash01(seed * 3.37).toFloat()

            val angle = dirRad + (r1 - 0.5f) * spreadRad
            val speed = cfg.speed * (0.4f + r2 * 1.2f)
            val lifeMs = (phase * periodSec * 1000f)
            val lifeSec = lifeMs / 1000f

            val vx = cos(angle) * speed
            val vy = sin(angle) * speed
            val g = cfg.gravity

            val startX = cfg.triggerX * W + (r3 - 0.5f) * W * 0.3f
            val startY = cfg.triggerY * H + (r2 - 0.5f) * H * 0.3f

            val px = startX + vx * lifeSec
            val py = startY + vy * lifeSec + 0.5f * g * lifeSec * lifeSec

            // Wrap to screen
            val finalX = ((px % W) + W) % W
            val finalY = ((py % H) + H) % H

            val size = cfg.sizeMin + r3 * (cfg.sizeMax - cfg.sizeMin)
            val lifeFrac = phase
            val alpha = if (cfg.fadeOut) {
                (1f - lifeFrac).coerceIn(0f, 1f) * intensity
            } else intensity

            drawParticle(
                canvas, cfg, finalX, finalY, size, alpha, r1
            )
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  WIND — storms (directional flow)
    // ═══════════════════════════════════════════════════════════
    private fun drawWindParticles(
        canvas: android.graphics.Canvas,
        cfg: DustConfig,
        timeSec: Float,
        W: Int, H: Int,
        intensity: Float,
        storm: Boolean
    ) {
        val t = timeSec
        val count = (cfg.count * intensity).toInt().coerceIn(5, 3000)

        for (i in 0 until count) {
            val seed = i.toDouble()
            val r1 = hash01(seed * 1.13).toFloat()
            val r2 = hash01(seed * 2.71).toFloat()
            val r3 = hash01(seed * 3.37).toFloat()

            val speed = cfg.speed * (0.5f + r2 * 0.8f)
            val windOffset = t * speed + seed.toFloat() * 100f

            val yBase = r1 * H
            val yJitter = sin((t * 2f + i * 0.5f).toDouble()).toFloat() * 20f * (cfg.spread / 360f)

            val x = ((windOffset + r3 * 200f) % (W + 200f)) - 100f
            val y = yBase + yJitter

            val size = cfg.sizeMin + r3 * (cfg.sizeMax - cfg.sizeMin)
            val alpha = intensity * (0.3f + r2 * 0.5f)

            // Motion blur streaks for storms
            if (storm) {
                val trailX = x - (cfg.windX * 0.15f) * (0.5f + r2 * 0.5f)
                drawStreak(
                    canvas, cfg, x, y, trailX, y, size, alpha, r1
                )
            } else {
                drawParticle(canvas, cfg, x, y, size, alpha, r1)
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  BURST — impact / chalk
    // ═══════════════════════════════════════════════════════════
    private fun drawBurstParticles(
        canvas: android.graphics.Canvas,
        cfg: DustConfig,
        timeSec: Float,
        W: Int, H: Int,
        intensity: Float,
        trail: Boolean
    ) {
        val cycleSec = cfg.triggerEverySec
        val cycleIdx: Int
        val localT: Float
        if (cycleSec <= 0.01f) {
            if (timeSec * 1000f > cfg.burstDurationMs + cfg.lifetimeMs) return
            cycleIdx = 0
            localT = timeSec
        } else {
            cycleIdx = floor(timeSec / cycleSec).toInt()
            localT = timeSec - cycleIdx * cycleSec
        }

        val localMs = (localT * 1000f).toLong()
        val count = (cfg.count * intensity).toInt().coerceIn(5, 3000)
        val spreadRad = Math.toRadians(cfg.spread.toDouble()).toFloat()
        val dirRad = Math.toRadians(cfg.direction.toDouble()).toFloat()

        for (i in 0 until count) {
            val seed = i.toDouble() + cycleIdx * 9999.0
            val r1 = hash01(seed * 1.13).toFloat()
            val r2 = hash01(seed * 2.71).toFloat()
            val r3 = hash01(seed * 3.37).toFloat()

            val birthMs = (i.toFloat() / count) * cfg.burstDurationMs
            val ageMs = localMs - birthMs.toLong()
            if (ageMs < 0L || ageMs > cfg.lifetimeMs) continue

            val ageSec = ageMs / 1000f
            val angle = dirRad + (r1 - 0.5f) * spreadRad
            val speed = cfg.speed * (0.4f + r2 * 1.2f)
            val g = cfg.gravity

            val vx = cos(angle) * speed
            val vy = sin(angle) * speed

            val startX = if (trail) 0.05f * W else cfg.triggerX * W
            val startY = if (trail) cfg.triggerY * H else cfg.triggerY * H

            val px = startX + vx * ageSec
            val py = startY + vy * ageSec + 0.5f * g * ageSec * ageSec

            val size = cfg.sizeMin + r3 * (cfg.sizeMax - cfg.sizeMin)
            val lifeFrac = ageMs.toFloat() / cfg.lifetimeMs
            val alpha = if (cfg.fadeOut) {
                (1f - lifeFrac).coerceIn(0f, 1f) * intensity
            } else intensity

            drawParticle(canvas, cfg, px, py, size, alpha, r1)
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  FLOOR DUST — low line
    // ═══════════════════════════════════════════════════════════
    private fun drawFloorDust(
        canvas: android.graphics.Canvas,
        cfg: DustConfig,
        timeSec: Float,
        W: Int, H: Int,
        intensity: Float
    ) {
        val t = timeSec
        val count = (cfg.count * intensity).toInt().coerceIn(5, 3000)
        val floorY = H.toFloat() * (1f - cfg.floorHeight)
        val bandH = H.toFloat() * cfg.floorHeight

        for (i in 0 until count) {
            val seed = i.toDouble()
            val r1 = hash01(seed * 1.13).toFloat()
            val r2 = hash01(seed * 2.71).toFloat()
            val r3 = hash01(seed * 3.37).toFloat()

            val phase = ((t / 4f) + r1) % 1f
            val x = ((r2 * W + t * cfg.speed) % W)
            val yBase = floorY + r3 * bandH
            val yJitter = sin((t + i).toDouble()).toFloat() * 8f

            val y = yBase + yJitter
            val size = cfg.sizeMin + r3 * (cfg.sizeMax - cfg.sizeMin)
            val alpha = intensity * (0.4f + r2 * 0.4f) * (1f - phase * 0.3f)

            drawParticle(canvas, cfg, x, y, size, alpha, r1)
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  PARTICLE DRAWER
    // ═══════════════════════════════════════════════════════════
    private fun drawParticle(
        canvas: android.graphics.Canvas,
        cfg: DustConfig,
        x: Float, y: Float,
        size: Float,
        alpha: Float,
        colorMix: Float
    ) {
        // Dust = very soft, so lower alpha and much bigger soft radius
        val a = (alpha * 255f * 0.45f).toInt().coerceIn(0, 255)
        if (a <= 0) return

        val c1 = cfg.color1
        val c2 = cfg.color2

        val r1 = ((c1 shr 16) and 0xFF).toInt()
        val g1 = ((c1 shr 8) and 0xFF).toInt()
        val b1 = (c1 and 0xFF).toInt()
        val r2 = ((c2 shr 16) and 0xFF).toInt()
        val g2 = ((c2 shr 8) and 0xFF).toInt()
        val b2 = (c2 and 0xFF).toInt()

        val r = (r1 * (1f - colorMix) + r2 * colorMix).toInt().coerceIn(0, 255)
        val g = (g1 * (1f - colorMix) + g2 * colorMix).toInt().coerceIn(0, 255)
        val b = (b1 * (1f - colorMix) + b2 * colorMix).toInt().coerceIn(0, 255)

        // Dust clouds are MUCH bigger and softer than particles
        val softRadius = size * (1.8f + cfg.softness * 1.4f)

        // Radial gradient — soft cloud falloff (not sharp dot)
        val shader = android.graphics.RadialGradient(
            x, y, softRadius,
            intArrayOf(
                android.graphics.Color.argb(a, r, g, b),
                android.graphics.Color.argb((a * 0.35f).toInt(), r, g, b),
                android.graphics.Color.argb((a * 0.10f).toInt(), r, g, b),
                android.graphics.Color.argb(0, r, g, b)
            ),
            floatArrayOf(0f, 0.4f, 0.7f, 1f),
            android.graphics.Shader.TileMode.CLAMP
        )

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.shader = shader
            // Additional blur for extra softness
            if (cfg.softness > 0.4f) {
                maskFilter = android.graphics.BlurMaskFilter(
                    softRadius * 0.35f,
                    android.graphics.BlurMaskFilter.Blur.NORMAL
                )
            }
        }
        canvas.drawCircle(x, y, softRadius, paint)
    }

    private fun drawStreak(
        canvas: android.graphics.Canvas,
        cfg: DustConfig,
        x1: Float, y1: Float,
        x2: Float, y2: Float,
        size: Float,
        alpha: Float,
        colorMix: Float
    ) {
        val a = (alpha * 255f).toInt().coerceIn(0, 255)
        if (a <= 0) return

        val c1 = cfg.color1
        val c2 = cfg.color2

        val r1 = ((c1 shr 16) and 0xFF).toInt()
        val g1 = ((c1 shr 8) and 0xFF).toInt()
        val b1 = (c1 and 0xFF).toInt()
        val r2 = ((c2 shr 16) and 0xFF).toInt()
        val g2 = ((c2 shr 8) and 0xFF).toInt()
        val b2 = (c2 and 0xFF).toInt()

        val r = (r1 * (1f - colorMix) + r2 * colorMix).toInt().coerceIn(0, 255)
        val g = (g1 * (1f - colorMix) + g2 * colorMix).toInt().coerceIn(0, 255)
        val b = (b1 * (1f - colorMix) + b2 * colorMix).toInt().coerceIn(0, 255)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.argb(a, r, g, b)
            strokeCap = Paint.Cap.ROUND
            strokeWidth = size * 0.7f
            if (cfg.softness > 0.8f) {
                maskFilter = android.graphics.BlurMaskFilter(
                    size * cfg.softness,
                    android.graphics.BlurMaskFilter.Blur.NORMAL
                )
            }
        }
        canvas.drawLine(x1, y1, x2, y2, paint)
    }
}