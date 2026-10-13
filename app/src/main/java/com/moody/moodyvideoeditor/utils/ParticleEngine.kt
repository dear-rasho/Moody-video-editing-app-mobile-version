package com.moody.moodyvideoeditor.utils

import android.graphics.Paint
import com.moody.moodyvideoeditor.data.ParticleConfig
import com.moody.moodyvideoeditor.data.ParticleStyle
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

object ParticleEngine {

    data class Particle(
        val birthMs: Long,
        val angleRad: Float,
        val initialSpeed: Float,
        val size: Float,
        val colorMix: Float,
        val useColor1: Boolean,
        val emoji: String = ""
    )

    private fun hash01(n: Double): Double {
        val x = sin(n * 12.9898 + 78.233) * 43758.5453
        return x - floor(x)
    }

    // ═══════════════════════════════════════════════════════════
    //  GENERATE PARTICLES for a given burst cycle
    // ═══════════════════════════════════════════════════════════
    fun generateParticles(
        cfg: ParticleConfig,
        cycleIndex: Int
    ): List<Particle> {
        val seed = cycleIndex.toLong() * 7919L
        val n = cfg.count.coerceIn(1, 20000)

        val spreadRad = Math.toRadians(cfg.spread.toDouble()).toFloat()
        val dirRad = Math.toRadians(cfg.direction.toDouble()).toFloat()
        val emojiList = cfg.emojis.split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }

        return List(n) { i ->
            val r1 = hash01(seed + i * 1.13).toFloat()
            val r2 = hash01(seed + i * 2.71).toFloat()
            val r3 = hash01(seed + i * 3.37).toFloat()
            val r4 = hash01(seed + i * 5.11).toFloat()

            val angle = dirRad + (r1 - 0.5f) * spreadRad
            val initialSpeed = cfg.speed * (0.5f + r2 * 1.0f)
            val size = cfg.sizeMin + r3 * (cfg.sizeMax - cfg.sizeMin)
            val colorMix = r4
            val useColor1 = r4 > 0.5f
            val emoji = if (emojiList.isNotEmpty()) {
                emojiList[(r1 * emojiList.size).toInt().coerceIn(0, emojiList.size - 1)]
            } else ""

            // Distribute birth times across burstDurationMs
            val birthFrac = i.toFloat() / n
            val birthMs = (birthFrac * cfg.burstDurationMs).toLong()

            Particle(
                birthMs = birthMs,
                angleRad = angle,
                initialSpeed = initialSpeed,
                size = size,
                colorMix = colorMix,
                useColor1 = useColor1,
                emoji = emoji
            )
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  COMPUTE particle position at age (ms since birth)
    //  Returns null if particle is dead/invisible
    // ═══════════════════════════════════════════════════════════
    data class ParticleState(
        val x: Float,   // 0..1 relative
        val y: Float,
        val size: Float,
        val alpha: Float
    )

    fun computeState(
        cfg: ParticleConfig,
        particle: Particle,
        ageMs: Long,
        W: Float, H: Float
    ): ParticleState? {
        if (ageMs < 0L) return null
        if (ageMs > cfg.lifetimeMs) return null

        val t = ageMs / 1000f
        val vx = cos(particle.angleRad) * particle.initialSpeed
        val vy = sin(particle.angleRad) * particle.initialSpeed
        val g = cfg.gravity

        val px = cfg.triggerX * W + vx * t
        val py = cfg.triggerY * H + vy * t + 0.5f * g * t * t

        val lifeFrac = ageMs.toFloat() / cfg.lifetimeMs
        val alpha = if (cfg.fadeOut) (1f - lifeFrac).coerceIn(0f, 1f) else 1f

        return ParticleState(px, py, particle.size, alpha)
    }

    // ═══════════════════════════════════════════════════════════
    //  DRAW on Android Canvas (used by bitmap renderer)
    // ═══════════════════════════════════════════════════════════
    fun drawOnCanvas(
        canvas: android.graphics.Canvas,
        cfg: ParticleConfig,
        localTimeSec: Float,
        W: Int, H: Int
    ) {
        if (localTimeSec < 0f) return

        // Determine which cycle we're in
        val cycleSec = cfg.triggerEverySec
        val cycleIdx: Int
        val localT: Float
        if (cycleSec <= 0.01f) {
            // Single burst — plays once at effect start
            if (localTimeSec * 1000f > cfg.burstDurationMs + cfg.lifetimeMs) return
            cycleIdx = 0
            localT = localTimeSec
        } else {
            cycleIdx = floor(localTimeSec / cycleSec).toInt()
            localT = localTimeSec - cycleIdx * cycleSec
        }

        val localMs = (localT * 1000f).toLong()

        // Draw shockwave ring
        if (cfg.shockwave) {
            val waveFrac = (localMs.toFloat() / 800f).coerceIn(0f, 1f)
            if (waveFrac < 1f) {
                val ringRadius = waveFrac * W * 0.7f
                val ringAlpha = ((1f - waveFrac) * 200f).toInt().coerceIn(0, 255)
                val r = ((cfg.color1 shr 16) and 0xFF).toInt()
                val g = ((cfg.color1 shr 8) and 0xFF).toInt()
                val b = (cfg.color1 and 0xFF).toInt()
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = W * cfg.shockwaveThickness
                    color = android.graphics.Color.argb(ringAlpha, r, g, b)
                    maskFilter = android.graphics.BlurMaskFilter(
                        (W * 0.02f).coerceAtLeast(4f),
                        android.graphics.BlurMaskFilter.Blur.NORMAL
                    )
                }
                canvas.drawCircle(cfg.triggerX * W, cfg.triggerY * H, ringRadius, paint)
            }
        }

        // Draw flash
        if (cfg.flashOnTrigger) {
            val flashFrac = (localMs / 150f).coerceIn(0f, 1f)
            if (flashFrac < 1f) {
                val a = ((1f - flashFrac) * 180f).toInt().coerceIn(0, 255)
                val paint = Paint().apply {
                    color = android.graphics.Color.argb(
                        a,
                        ((cfg.color1 shr 16) and 0xFF).toInt(),
                        ((cfg.color1 shr 8) and 0xFF).toInt(),
                        (cfg.color1 and 0xFF).toInt()
                    )
                }
                canvas.drawRect(0f, 0f, W.toFloat(), H.toFloat(), paint)
            }
        }

        // Draw particles
        val particles = generateParticles(cfg, cycleIdx)
        val intensityMul = (cfg.masterIntensity / 100f).coerceIn(0f, 2f)

        particles.forEach { p ->
            val age = localMs - p.birthMs
            if (age < 0L || age > cfg.lifetimeMs) return@forEach

            val st = computeState(cfg, p, age, W.toFloat(), H.toFloat()) ?: return@forEach

            val c1r = ((cfg.color1 shr 16) and 0xFF).toInt()
            val c1g = ((cfg.color1 shr 8) and 0xFF).toInt()
            val c1b = (cfg.color1 and 0xFF).toInt()
            val c2r = ((cfg.color2 shr 16) and 0xFF).toInt()
            val c2g = ((cfg.color2 shr 8) and 0xFF).toInt()
            val c2b = (cfg.color2 and 0xFF).toInt()

            val mix = p.colorMix
            val r = (c1r * (1f - mix) + c2r * mix).toInt().coerceIn(0, 255)
            val g = (c1g * (1f - mix) + c2g * mix).toInt().coerceIn(0, 255)
            val b = (c1b * (1f - mix) + c2b * mix).toInt().coerceIn(0, 255)

            val alpha = (st.alpha * 255f * intensityMul).toInt().coerceIn(0, 255)
            if (alpha <= 0) return@forEach

            val particlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.argb(alpha, r, g, b)
                if (cfg.glow) {
                    maskFilter = android.graphics.BlurMaskFilter(
                        (st.size * 1.5f).coerceAtLeast(3f),
                        android.graphics.BlurMaskFilter.Blur.NORMAL
                    )
                }
            }
            canvas.drawCircle(st.x, st.y, st.size, particlePaint)
        }
    }
}