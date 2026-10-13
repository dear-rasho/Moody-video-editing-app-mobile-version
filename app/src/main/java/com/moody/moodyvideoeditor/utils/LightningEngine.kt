package com.moody.moodyvideoeditor.utils

import android.graphics.BlurMaskFilter
import android.graphics.Paint
import com.moody.moodyvideoeditor.data.LightningConfig
import java.util.Locale
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.random.Random

// ═══════════════════════════════════════════════════════════════
//  ⚡ LIGHTNING ENGINE
//  - Preview + Export: procedural branching bolt generator
//  - Flash strength via flashAt()
//  - FFmpeg filter for screen flash (optional)
// ═══════════════════════════════════════════════════════════════

data class LightningSegment(
    val x1: Float, val y1: Float,
    val x2: Float, val y2: Float,
    val thickness: Float,   // relative 0..1
    val alpha: Float,       // 0..1
    val depth: Int          // 0 = trunk, 1 = primary branch, 2+ = smaller
)

object LightningEngine {

    // Deterministic pseudo-random
    private fun hash01(n: Double): Double {
        val x = sin(n * 12.9898 + 78.233) * 43758.5453
        return x - floor(x)
    }

    // ═══════════════════════════════════════════════════════════
    //  FLASH STRENGTH (0..1)
    // ═══════════════════════════════════════════════════════════
    fun flashAt(cfg: LightningConfig, timeSec: Float): Float {
        if (timeSec < 0f) return 0f

        val freq = cfg.frequency.coerceIn(0.2f, 5f)
        val periodSec = 1f / freq
        val durSec = (cfg.durationMs / 1000f).coerceIn(0.02f, 1f)

        val cycleIdx = floor(timeSec / periodSec).toInt()
        val localTime = timeSec - cycleIdx * periodSec

        val rand = hash01(cycleIdx.toDouble()).toFloat()
        val strength = 0.65f + rand * 0.35f

        var flash = 0f
        if (localTime < durSec) {
            val t = localTime / durSec
            flash = if (t < 0.15f) t / 0.15f
            else 1f - (t - 0.15f) / 0.85f
            flash *= strength
        }

        if (cfg.doubleStrike) {
            val echoStart = durSec * 1.3f
            val echoEnd = durSec * 2.6f
            if (localTime > echoStart && localTime < echoEnd) {
                val t = (localTime - echoStart) / (echoEnd - echoStart)
                val echo = (1f - t) * 0.45f * strength
                if (echo > flash) flash = echo
            }
        }

        val peak = (cfg.intensity / 100f).coerceIn(0f, 2f)
        return (flash * peak).coerceIn(0f, 1f)
    }

    // ═══════════════════════════════════════════════════════════
    //  BOLT GENERATOR
    //  Creates branching lightning bolt for a given flash cycle
    // ═══════════════════════════════════════════════════════════
    fun generateBolt(
        cfg: LightningConfig,
        cycleIndex: Int,
        W: Float,
        H: Float
    ): List<LightningSegment> {
        val rnd = Random(cycleIndex.toLong() * 7919L)

        // Start from top: slight random X near center
        val startX = W * (0.4f + rnd.nextFloat() * 0.2f)   // 0.4..0.6
        val startY = -H * 0.05f                             // slightly above

        // End: random somewhere lower area
        val endX = W * (0.25f + rnd.nextFloat() * 0.5f)     // 0.25..0.75
        val endY = H * (0.7f + rnd.nextFloat() * 0.35f)     // 0.7..1.05

        val segments = mutableListOf<LightningSegment>()

        // Main trunk with zigzag
        generateTrunk(
            segments = segments,
            x1 = startX, y1 = startY,
            x2 = endX, y2 = endY,
            rnd = rnd,
            depth = 0,
            thickness = 1f,
            alpha = 1f,
            maxDepth = 3
        )

        return segments
    }

    private fun generateTrunk(
        segments: MutableList<LightningSegment>,
        x1: Float, y1: Float,
        x2: Float, y2: Float,
        rnd: Random,
        depth: Int,
        thickness: Float,
        alpha: Float,
        maxDepth: Int
    ) {
        if (depth > maxDepth) return

        val dx = x2 - x1
        val dy = y2 - y1
        val len = kotlin.math.sqrt(dx * dx + dy * dy)
        if (len < 4f) return

        // Sub-segment count scales with length
        val subCount = (len / 40f).toInt().coerceIn(3, 8)
        val perpX = -dy / len
        val perpY = dx / len

        var prevX = x1
        var prevY = y1

        for (i in 1..subCount) {
            val t = i.toFloat() / subCount
            var px = x1 + dx * t
            var py = y1 + dy * t

            // Zigzag: only jitter intermediate points
            if (i < subCount) {
                val jitter = (rnd.nextFloat() - 0.5f) * (len * 0.15f) / subCount * 3f
                px += perpX * jitter
                py += perpY * jitter
            }

            // Add this segment
            segments.add(
                LightningSegment(
                    x1 = prevX, y1 = prevY,
                    x2 = px, y2 = py,
                    thickness = thickness,
                    alpha = alpha,
                    depth = depth
                )
            )

            // Spawn a branch from this node (probabilistic, more on trunk)
            val branchChance = when (depth) {
                0 -> 0.45f
                1 -> 0.25f
                2 -> 0.10f
                else -> 0f
            }
            if (i < subCount && depth < maxDepth && rnd.nextFloat() < branchChance) {
                // Branch direction: perpendicular-ish, biased sideways
                val side = if (rnd.nextBoolean()) 1f else -1f
                val angleJitter = (rnd.nextFloat() - 0.5f) * 1.2f
                val branchAngle = side * (0.5f + angleJitter)
                val branchLen = len * (0.25f + rnd.nextFloat() * 0.30f)

                val bdx = cos(branchAngle) * branchLen * 0.7f + dx * 0.2f
                val bdy = kotlin.math.abs(sin(branchAngle)) * branchLen + dy * 0.3f

                generateTrunk(
                    segments = segments,
                    x1 = px, y1 = py,
                    x2 = (px + bdx), y2 = (py + bdy),
                    rnd = rnd,
                    depth = depth + 1,
                    thickness = thickness * 0.55f,
                    alpha = alpha * 0.8f,
                    maxDepth = maxDepth
                )
            }

            prevX = px
            prevY = py
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  DRAW BOLT (Android Canvas — used by export renderer)
    // ═══════════════════════════════════════════════════════════
    fun drawBoltAndroid(
        canvas: android.graphics.Canvas,
        cfg: LightningConfig,
        flashStrength: Float,
        cycleIndex: Int,
        W: Int,
        H: Int
    ) {
        if (flashStrength <= 0.01f) return

        val segments = generateBolt(cfg, cycleIndex, W.toFloat(), H.toFloat())
        val baseColor = cfg.color
        val r = ((baseColor shr 16) and 0xFF).toInt()
        val g = ((baseColor shr 8) and 0xFF).toInt()
        val b = (baseColor and 0xFF).toInt()

        val trunkWidth = (W / 100f) * (1f + flashStrength * 1.5f)
        val maxBlur = (W / 60f).coerceAtLeast(4f)

        // ── Pass 1: outer glow (thick, blurred, colored) ──
        val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            maskFilter = BlurMaskFilter(maxBlur, BlurMaskFilter.Blur.NORMAL)
        }
        segments.forEach { seg ->
            glowPaint.color = android.graphics.Color.argb(
                (seg.alpha * flashStrength * 90).toInt().coerceIn(0, 255),
                r, g, b
            )
            glowPaint.strokeWidth = trunkWidth * seg.thickness * 3.2f
            canvas.drawLine(seg.x1, seg.y1, seg.x2, seg.y2, glowPaint)
        }

        // ── Pass 2: mid glow (thinner, brighter) ──
        val midPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            maskFilter = BlurMaskFilter(maxBlur * 0.5f, BlurMaskFilter.Blur.NORMAL)
        }
        segments.forEach { seg ->
            midPaint.color = android.graphics.Color.argb(
                (seg.alpha * flashStrength * 200).toInt().coerceIn(0, 255),
                r, g, b
            )
            midPaint.strokeWidth = trunkWidth * seg.thickness * 1.8f
            canvas.drawLine(seg.x1, seg.y1, seg.x2, seg.y2, midPaint)
        }

        // ── Pass 3: bright core (thin, white) ──
        val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            color = android.graphics.Color.argb(
                (flashStrength * 255).toInt().coerceIn(0, 255),
                255, 255, 255
            )
        }
        segments.forEach { seg ->
            corePaint.strokeWidth = trunkWidth * seg.thickness * 0.7f
            canvas.drawLine(seg.x1, seg.y1, seg.x2, seg.y2, corePaint)
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  FFmpeg FILTER (screen flash — kept as bonus)
    // ═══════════════════════════════════════════════════════════
    fun buildFfmpegFilters(
        cfg: LightningConfig,
        effectRelStartSec: Double,
        effectRelEndSec: Double
    ): List<String> {
        if (effectRelEndSec <= effectRelStartSec) return emptyList()

        val freq = cfg.frequency.coerceIn(0.2f, 5f)
        val periodSec = 1.0 / freq
        val durSec = (cfg.durationMs / 1000.0).coerceIn(0.02, 1.0)
        val intensity = cfg.intensity.coerceIn(0f, 200f) / 100f

        val r = ((cfg.color shr 16) and 0xFF).toInt() / 255f
        val g = ((cfg.color shr 8) and 0xFF).toInt() / 255f
        val b = (cfg.color and 0xFF).toInt() / 255f

        val rBoostBase = (r * 180f * intensity).coerceIn(0f, 220f)
        val gBoostBase = (g * 180f * intensity).coerceIn(0f, 220f)
        val bBoostBase = (b * 180f * intensity).coerceIn(0f, 220f)

        val loc = Locale.US
        fun f(v: Float): String = String.format(loc, "%.4f", v)

        val effectDurSec = effectRelEndSec - effectRelStartSec
        val filters = mutableListOf<String>()

        var t = 0.0
        var cycleIdx = 0
        val maxIterations = 500

        while (t < effectDurSec && cycleIdx < maxIterations) {
            val rand = hash01(cycleIdx.toDouble())
            val strength = (0.65 + rand * 0.35).toFloat()

            val mainStart = effectRelStartSec + t
            val mainEnd = (effectRelStartSec + t + durSec)
                .coerceAtMost(effectRelEndSec)

            if (mainEnd > mainStart) {
                val rB = (rBoostBase * strength).toInt().coerceIn(0, 220)
                val gB = (gBoostBase * strength).toInt().coerceIn(0, 220)
                val bB = (bBoostBase * strength).toInt().coerceIn(0, 220)

                filters.add(
                    "lutrgb=" +
                            "r='val+$rB':" +
                            "g='val+$gB':" +
                            "b='val+$bB':" +
                            "enable='between(t,${f(mainStart.toFloat())},${f(mainEnd.toFloat())})'"
                )
            }

            if (cfg.doubleStrike) {
                val echoStart = effectRelStartSec + t + durSec * 1.3
                val echoEnd = (echoStart + durSec * 1.3)
                    .coerceAtMost(effectRelEndSec)

                if (echoEnd > echoStart && echoStart < effectRelEndSec) {
                    val echoStrength = strength * 0.45f
                    val rB = (rBoostBase * echoStrength).toInt().coerceIn(0, 220)
                    val gB = (gBoostBase * echoStrength).toInt().coerceIn(0, 220)
                    val bB = (bBoostBase * echoStrength).toInt().coerceIn(0, 220)

                    filters.add(
                        "lutrgb=" +
                                "r='val+$rB':" +
                                "g='val+$gB':" +
                                "b='val+$bB':" +
                                "enable='between(t,${f(echoStart.toFloat())},${f(echoEnd.toFloat())})'"
                    )
                }
            }

            t += periodSec
            cycleIdx++
        }

        return filters
    }
}