package com.moody.moodyvideoeditor.advanced.preview

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import com.moody.moodyvideoeditor.advanced.base.AdvancedEffectRenderer
import com.moody.moodyvideoeditor.data.EditorClip
import com.moody.moodyvideoeditor.data.advanced.AdvancedEffectState
import com.moody.moodyvideoeditor.data.advanced.AdvancedEffectType
import com.moody.moodyvideoeditor.utils.KeyframeStore
import kotlin.math.floor

class RoughenEdgesRenderer : AdvancedEffectRenderer {

    override val type = AdvancedEffectType.ROUGHEN_EDGES

    @Composable
    override fun RenderPreview(
        clip: EditorClip,
        effect: AdvancedEffectState,
        localTimeSec: Float,
        content: @Composable () -> Unit
    ) {
        val r = effect.roughenEdges ?: run { content(); return }

        val bw = KeyframeStore.sample(r.keyframes, "borderWidth", localTimeSec, r.borderWidth)
        val evo = KeyframeStore.sample(r.keyframes, "evolution", localTimeSec, r.evolution)
        val scaleV = KeyframeStore.sample(r.keyframes, "fractalScale", localTimeSec, r.fractalScale)
        val comp = KeyframeStore.sample(r.keyframes, "complexity", localTimeSec, r.complexity)
        val sharp =
            KeyframeStore.sample(r.keyframes, "edgeSharpness", localTimeSec, r.edgeSharpness)
        val seed = KeyframeStore.sample(r.keyframes, "randomSeed", localTimeSec, r.randomSeed)

        if (bw < 0.5f) {
            content(); return
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawWithContent {
                    val path = buildFractalRoughPath(
                        w = size.width,
                        h = size.height,
                        borderWidth = bw,
                        evolution = evo,
                        fractalScale = scaleV,
                        complexity = comp.toInt().coerceIn(1, 10),
                        sharpness = sharp,
                        seed = seed.toInt()
                    )
                    clipPath(path) {
                        this@drawWithContent.drawContent()
                    }
                }
        ) { content() }
    }

    override fun buildFFmpegFilters(
        clip: EditorClip,
        effect: AdvancedEffectState,
        clipDurationSec: Float
    ): List<String> {
        val r = effect.roughenEdges ?: return emptyList()
        val bw = r.borderWidth.coerceIn(0f, 500f)
        if (bw < 0.5f) return emptyList()

        val scale = r.fractalScale.coerceIn(20f, 1000f)
        val evo = r.evolution.coerceIn(0f, 360f)
        val seed = r.randomSeed.toInt().coerceIn(0, 9999)
        val freq = 1f / scale
        val phase = evo * 0.1f

        // Fractal-style noise: sin(X*freq + phase + seed) * cos(Y*freq + phase*1.3 + seed)
        // Alpha = 255 if distance from edge + noise*BW > BW
        // Result: smooth jagged edges, not blocky
        val noiseExpr = "(sin(X*$freq+$phase+$seed)*cos(Y*$freq+${phase * 1.3f}+$seed)*$bw*0.5)"
        val distExpr = "min(min(X\\,W-X)\\,min(Y\\,H-Y))"

        return listOf(
            "format=rgba",
            "geq=r='r(X,Y)':g='g(X,Y)':b='b(X,Y)':" +
                    "a='if(gt($distExpr+$noiseExpr\\,$bw),255,0)'"
        )
    }
}

// ═══════════════════════════════════════════════════════════════
//  FRACTAL PATH BUILDER — smooth noise, not blocky
// ═══════════════════════════════════════════════════════════════

private fun buildFractalRoughPath(
    w: Float,
    h: Float,
    borderWidth: Float,
    evolution: Float,
    fractalScale: Float,
    complexity: Int,
    sharpness: Float,
    seed: Int
): Path {
    val path = Path()
    val steps = (80 + complexity * 25).coerceIn(80, 320)
    val amp = borderWidth.coerceIn(0.5f, 500f)
    val baseFreq = (fractalScale.coerceIn(20f, 1000f) / 180f).coerceAtLeast(0.05f)
    val phase = evolution / 30f
    val sharpFactor = (sharpness / 100f).coerceIn(0f, 1f)

    fun noise2D(x: Float, y: Float): Float {
        var sum = 0f
        var ampO = 1f
        var totalAmp = 0f
        var freq = baseFreq
        for (o in 0 until complexity) {
            val n = valueNoise(
                x * freq + phase + o * 13.7f,
                y * freq + phase * 0.7f + o * 7.3f,
                seed + o * 1013
            )
            sum += ampO * n
            totalAmp += ampO
            ampO *= 0.55f
            freq *= 2.17f
        }
        val v = sum / totalAmp.coerceAtLeast(0.001f)
        // Sharpness control: mix raw value with smoothstep for softer/harder edges
        return v * (0.6f + 0.4f * sharpFactor)
    }

    val points = ArrayList<Pair<Float, Float>>(steps * 4 + 4)

    // Top edge — left to right
    for (i in 0..steps) {
        val t = i.toFloat() / steps
        val x = t * w
        val n = noise2D(x * 0.02f, 0f)
        val y = amp * (1f - n).coerceIn(0f, 1f)
        points.add(x to y)
    }
    // Right edge — top to bottom
    for (i in 0..steps) {
        val t = i.toFloat() / steps
        val y = t * h
        val n = noise2D(w * 0.02f, y * 0.02f)
        val x = w - amp * (1f - n).coerceIn(0f, 1f)
        points.add(x to y)
    }
    // Bottom edge — right to left
    for (i in 0..steps) {
        val t = i.toFloat() / steps
        val x = w - t * w
        val n = noise2D(x * 0.02f, h * 0.02f)
        val y = h - amp * (1f - n).coerceIn(0f, 1f)
        points.add(x to y)
    }
    // Left edge — bottom to top
    for (i in 0..steps) {
        val t = i.toFloat() / steps
        val y = h - t * h
        val n = noise2D(0f, y * 0.02f)
        val x = amp * (1f - n).coerceIn(0f, 1f)
        points.add(x to y)
    }

    if (points.isEmpty()) return path

    path.moveTo(points[0].first, points[0].second)
    for (i in 1 until points.size) {
        val p = points[i]
        // Smooth curve via quadratic — reduces blockiness
        val prev = points[i - 1]
        val midX = (prev.first + p.first) / 2f
        val midY = (prev.second + p.second) / 2f
        path.quadraticBezierTo(prev.first, prev.second, midX, midY)
    }
    path.close()
    return path
}

// Value noise — smooth interpolated hash
private fun valueNoise(x: Float, y: Float, seed: Int): Float {
    val xi = floor(x).toInt()
    val yi = floor(y).toInt()
    val xf = x - xi
    val yf = y - yi

    fun hash(ix: Int, iy: Int): Float {
        var h = ix * 374761393 + iy * 668265263 + seed * 1274126177
        h = (h xor (h shr 13)) * 1013904223
        h = h xor (h shr 16)
        return ((h and 0x7FFFFFFF).toFloat() / 0x7FFFFFFF.toFloat())
    }

    val v00 = hash(xi, yi)
    val v10 = hash(xi + 1, yi)
    val v01 = hash(xi, yi + 1)
    val v11 = hash(xi + 1, yi + 1)

    val sx = xf * xf * (3f - 2f * xf)
    val sy = yf * yf * (3f - 2f * yf)

    val a = v00 + (v10 - v00) * sx
    val b = v01 + (v11 - v01) * sx
    return a + (b - a) * sy
}