package com.moody.moodyvideoeditor.utils

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.moody.moodyvideoeditor.data.BrushStroke
import com.moody.moodyvideoeditor.data.BrushType
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

object BrushEngine {


    //  COLOR SAMPLING — gradient along stroke path

    private fun sampleColorAt(stroke: BrushStroke, t: Float): Color {
        val g = stroke.gradient
        if (!g.enabled) return Color(stroke.color)

        val tt = if (g.mode == "reverse") 1f - t.coerceIn(0f, 1f)
        else t.coerceIn(0f, 1f)

        return if (g.hasMid) {
            // 3-stop gradient: c1 → c3 → c2
            if (tt < 0.5f) {
                lerpColor(Color(g.color1), Color(g.color3), tt * 2f)
            } else {
                lerpColor(Color(g.color3), Color(g.color2), (tt - 0.5f) * 2f)
            }
        } else {
            // 2-stop gradient
            lerpColor(Color(g.color1), Color(g.color2), tt)
        }
    }

    private fun lerpColor(a: Color, b: Color, t: Float): Color {
        val tt = t.coerceIn(0f, 1f)
        return Color(
            red = a.red + (b.red - a.red) * tt,
            green = a.green + (b.green - a.green) * tt,
            blue = a.blue + (b.blue - a.blue) * tt,
            alpha = a.alpha + (b.alpha - a.alpha) * tt
        )
    }


    //  MAIN DRAW

    fun drawStroke(
        scope: DrawScope,
        stroke: BrushStroke,
        viewW: Float,
        viewH: Float,
        currentTimeMs: Long
    ) {
        if (currentTimeMs < stroke.startMs || currentTimeMs > stroke.endMs) return

        if (stroke.points.size < 2) {
            if (stroke.points.size == 1) {
                val p = stroke.points[0]
                val x = p.x * viewW
                val y = p.y * viewH
                val c = if (stroke.gradient.enabled) sampleColorAt(stroke, 0f)
                else Color(stroke.color)

                when (stroke.type) {
                    BrushType.NEON -> {
                        scope.drawCircle(
                            c.copy(alpha = stroke.opacity * 0.3f),
                            radius = stroke.width * 1.8f / 2f,
                            center = Offset(x, y)
                        )
                        scope.drawCircle(
                            c, radius = stroke.width / 2f, center = Offset(x, y)
                        )
                    }

                    else -> scope.drawCircle(
                        c.copy(alpha = stroke.opacity),
                        radius = stroke.width / 2f,
                        center = Offset(x, y)
                    )
                }
            }
            return
        }

        when (stroke.type) {
            BrushType.PEN -> drawPen(scope, stroke, viewW, viewH)
            BrushType.MARKER -> drawMarker(scope, stroke, viewW, viewH)
            BrushType.CHALK -> drawChalk(scope, stroke, viewW, viewH)
            BrushType.NEON -> drawNeon(scope, stroke, viewW, viewH)
            BrushType.GLOW -> drawGlow(scope, stroke, viewW, viewH)
            BrushType.SPRAY -> drawSpray(scope, stroke, viewW, viewH)
        }
    }


    private fun buildPath(stroke: BrushStroke, w: Float, h: Float): Path {
        val path = Path()
        stroke.points.forEachIndexed { i, p ->
            val x = p.x * w
            val y = p.y * h
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        return path
    }

    // 🆕 Draw gradient stroke by segmenting path into small chunks
    // and coloring each chunk by its position along the stroke.
    private fun drawGradientStroke(
        scope: DrawScope,
        stroke: BrushStroke,
        w: Float,
        h: Float,
        cap: StrokeCap,
        join: StrokeJoin,
        alphaMul: Float = 1f,
        widthMul: Float = 1f,
        layerPass: Int = 0   // 0 = base, 1+ = glow layers
    ) {
        val pts = stroke.points
        if (pts.size < 2) return

        // Segment count proportional to point count
        val totalSegments = (pts.size - 1).coerceAtLeast(1)
        var segStart = 0

        for (i in 0 until totalSegments) {
            val p1 = pts[i]
            val p2 = pts[i + 1]
            val x1 = p1.x * w
            val y1 = p1.y * h
            val x2 = p2.x * w
            val y2 = p2.y * h

            val tMid = (i + 0.5f) / totalSegments
            val c = sampleColorAt(stroke, tMid).copy(
                alpha = stroke.opacity * alphaMul
            )

            scope.drawLine(
                color = c,
                start = Offset(x1, y1),
                end = Offset(x2, y2),
                strokeWidth = stroke.width * widthMul,
                cap = cap
            )
            segStart = i + 1
        }
    }

    private fun drawPen(scope: DrawScope, stroke: BrushStroke, w: Float, h: Float) {
        if (stroke.gradient.enabled) {
            drawGradientStroke(
                scope, stroke, w, h,
                cap = StrokeCap.Round, join = StrokeJoin.Round
            )
        } else {
            val path = buildPath(stroke, w, h)
            scope.drawPath(
                path = path,
                color = Color(stroke.color).copy(alpha = stroke.opacity),
                style = Stroke(
                    width = stroke.width,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }
    }

    private fun drawMarker(scope: DrawScope, stroke: BrushStroke, w: Float, h: Float) {
        if (stroke.gradient.enabled) {
            drawGradientStroke(
                scope, stroke, w, h,
                cap = StrokeCap.Square, join = StrokeJoin.Bevel
            )
        } else {
            val path = buildPath(stroke, w, h)
            scope.drawPath(
                path = path,
                color = Color(stroke.color).copy(alpha = stroke.opacity),
                style = Stroke(
                    width = stroke.width,
                    cap = StrokeCap.Square,
                    join = StrokeJoin.Bevel
                )
            )
        }
    }

    private fun drawChalk(scope: DrawScope, stroke: BrushStroke, w: Float, h: Float) {
        val baseColor = if (stroke.gradient.enabled)
            sampleColorAt(stroke, 0.5f) else Color(stroke.color)

        // Base path
        if (stroke.gradient.enabled) {
            drawGradientStroke(
                scope, stroke, w, h,
                cap = StrokeCap.Round, join = StrokeJoin.Round,
                alphaMul = 0.5f
            )
        } else {
            val path = buildPath(stroke, w, h)
            scope.drawPath(
                path = path,
                color = baseColor.copy(alpha = stroke.opacity * 0.5f),
                style = Stroke(
                    width = stroke.width,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }

        // Grainy dots
        val rnd = Random(stroke.id.hashCode())
        val totalPts = stroke.points.size
        stroke.points.forEachIndexed { idx, p ->
            val baseX = p.x * w
            val baseY = p.y * h
            val t = idx.toFloat() / totalPts.coerceAtLeast(1)
            val dotColor = if (stroke.gradient.enabled)
                sampleColorAt(stroke, t) else Color(stroke.color)

            repeat(8) {
                val angle = rnd.nextFloat() * 2f * Math.PI.toFloat()
                val dist = rnd.nextFloat() * stroke.width
                val gx = baseX + cos(angle) * dist
                val gy = baseY + sin(angle) * dist
                scope.drawCircle(
                    color = dotColor.copy(alpha = stroke.opacity * 0.7f),
                    radius = rnd.nextFloat() * stroke.width * 0.15f + 0.5f,
                    center = Offset(gx, gy)
                )
            }
        }
    }

    private fun drawNeon(scope: DrawScope, stroke: BrushStroke, w: Float, h: Float) {
        if (stroke.gradient.enabled) {
            // Multi-pass gradient glow
            listOf(3.0f to 0.15f, 2.0f to 0.3f, 1.5f to 0.5f).forEach { (mul, a) ->
                drawGradientStroke(
                    scope, stroke, w, h,
                    cap = StrokeCap.Round, join = StrokeJoin.Round,
                    alphaMul = a, widthMul = mul
                )
            }
            // Bright core
            drawGradientStroke(
                scope, stroke, w, h,
                cap = StrokeCap.Round, join = StrokeJoin.Round,
                alphaMul = 0.9f, widthMul = 0.5f
            )
            drawGradientStroke(
                scope, stroke, w, h,
                cap = StrokeCap.Round, join = StrokeJoin.Round
            )
        } else {
            val path = buildPath(stroke, w, h)
            val c = Color(stroke.color)
            val a = stroke.opacity

            listOf(3.0f to 0.15f, 2.0f to 0.3f, 1.5f to 0.5f).forEach { (mul, alpha) ->
                scope.drawPath(
                    path = path,
                    color = c.copy(alpha = a * alpha),
                    style = Stroke(
                        width = stroke.width * mul,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }
            scope.drawPath(
                path = path,
                color = Color.White.copy(alpha = a * 0.9f),
                style = Stroke(
                    width = stroke.width * 0.5f,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
            scope.drawPath(
                path = path,
                color = c.copy(alpha = a),
                style = Stroke(
                    width = stroke.width,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }
    }

    private fun drawGlow(scope: DrawScope, stroke: BrushStroke, w: Float, h: Float) {
        if (stroke.gradient.enabled) {
            listOf(2.5f to 0.1f, 1.8f to 0.2f, 1.2f to 0.4f).forEach { (mul, a) ->
                drawGradientStroke(
                    scope, stroke, w, h,
                    cap = StrokeCap.Round, join = StrokeJoin.Round,
                    alphaMul = a, widthMul = mul
                )
            }
            drawGradientStroke(
                scope, stroke, w, h,
                cap = StrokeCap.Round, join = StrokeJoin.Round,
                alphaMul = 0.85f, widthMul = 0.6f
            )
        } else {
            val path = buildPath(stroke, w, h)
            val c = Color(stroke.color)
            val a = stroke.opacity

            listOf(2.5f to 0.1f, 1.8f to 0.2f, 1.2f to 0.4f).forEach { (mul, alpha) ->
                scope.drawPath(
                    path = path,
                    color = c.copy(alpha = a * alpha),
                    style = Stroke(
                        width = stroke.width * mul,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }
            scope.drawPath(
                path = path,
                color = c.copy(alpha = a * 0.85f),
                style = Stroke(
                    width = stroke.width * 0.6f,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }
    }

    private fun drawSpray(scope: DrawScope, stroke: BrushStroke, w: Float, h: Float) {
        val rnd = Random(stroke.id.hashCode())
        val totalPts = stroke.points.size

        for (i in 0 until stroke.points.size - 1) {
            val p1 = stroke.points[i]
            val p2 = stroke.points[i + 1]
            val x1 = p1.x * w
            val y1 = p1.y * h
            val x2 = p2.x * w
            val y2 = p2.y * h
            val dist = kotlin.math.sqrt(
                (x2 - x1) * (x2 - x1) + (y2 - y1) * (y2 - y1)
            )
            val steps = (dist / 3f).toInt().coerceAtLeast(1)
            val t = i.toFloat() / totalPts.coerceAtLeast(1)

            val c = if (stroke.gradient.enabled)
                sampleColorAt(stroke, t) else Color(stroke.color)

            repeat(steps) {
                val tt = it / steps.toFloat()
                val mx = x1 + (x2 - x1) * tt
                val my = y1 + (y2 - y1) * tt

                repeat(6) {
                    val angle = rnd.nextFloat() * 2f * Math.PI.toFloat()
                    val radius = rnd.nextFloat() * stroke.width / 2f
                    scope.drawCircle(
                        color = c.copy(
                            alpha = stroke.opacity * (0.3f + rnd.nextFloat() * 0.5f)
                        ),
                        radius = 0.6f + rnd.nextFloat() * 1.2f,
                        center = Offset(
                            mx + cos(angle) * radius,
                            my + sin(angle) * radius
                        )
                    )
                }
            }
        }
    }
}