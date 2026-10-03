package com.moody.moodyvideoeditor.utils

import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import com.moody.moodyvideoeditor.data.MaskKeyframe
import com.moody.moodyvideoeditor.data.MaskPoint
import com.moody.moodyvideoeditor.data.MaskState
import com.moody.moodyvideoeditor.data.MaskType
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object MaskEngine {


    //  SAMPLE — keyframe interpolation (now with per-point path)

    fun sampleAt(state: MaskState, timeSec: Float): MaskState {
        if (state.keyframes.isEmpty()) return state
        val kfs = state.keyframes.sortedBy { it.timeMs }
        if (kfs.size == 1) return applyKeyframe(state, kfs[0])

        val timeMs = (timeSec * 1000f).toLong()
        val first = kfs.first()
        val last = kfs.last()
        if (timeMs <= first.timeMs) return applyKeyframe(state, first)
        if (timeMs >= last.timeMs) return applyKeyframe(state, last)

        for (i in 0 until kfs.size - 1) {
            val a = kfs[i]
            val b = kfs[i + 1]
            if (timeMs in a.timeMs..b.timeMs) {
                val span = (b.timeMs - a.timeMs).coerceAtLeast(1L)
                val raw = (timeMs - a.timeMs).toFloat() / span.toFloat()
                val t = KeyframeStore.easeFn(raw, a.ease)
                return lerpKeyframes(state, a, b, t)
            }
        }
        return state
    }

    private fun applyKeyframe(base: MaskState, k: MaskKeyframe): MaskState = base.copy(
        centerX = k.centerX,
        centerY = k.centerY,
        radius = k.radius,
        width = k.width,
        height = k.height,
        rotation = k.rotation,
        cornerRadius = k.cornerRadius,
        scale = k.scale,
        positionY = k.positionY,
        feather = k.feather,
        expansion = k.expansion,
        opacity = k.opacity,
        customPoints = if (k.customPoints.isNotEmpty()) k.customPoints else base.customPoints
    )

    private fun lerpKeyframes(
        base: MaskState,
        a: MaskKeyframe,
        b: MaskKeyframe,
        t: Float
    ): MaskState {
        // Interpolate custom path points if both keyframes have them
        val interpPoints = if (a.customPoints.isNotEmpty() && b.customPoints.isNotEmpty()
            && a.customPoints.size == b.customPoints.size
        ) {
            a.customPoints.zip(b.customPoints) { pa, pb -> pa.lerp(pb, t) }
        } else base.customPoints

        return base.copy(
            centerX = lerp(a.centerX, b.centerX, t),
            centerY = lerp(a.centerY, b.centerY, t),
            radius = lerp(a.radius, b.radius, t),
            width = lerp(a.width, b.width, t),
            height = lerp(a.height, b.height, t),
            rotation = lerp(a.rotation, b.rotation, t),
            cornerRadius = lerp(a.cornerRadius, b.cornerRadius, t),
            scale = lerp(a.scale, b.scale, t),
            positionY = lerp(a.positionY, b.positionY, t),
            feather = lerp(a.feather, b.feather, t),
            expansion = lerp(a.expansion, b.expansion, t),
            opacity = lerp(a.opacity, b.opacity, t),
            customPoints = interpPoints
        )
    }

    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t


    //  AUTO HANDLES

    fun autoHandleOffsets(
        pts: List<MaskPoint>,
        index: Int,
        strength: Float = 0.25f
    ): Pair<Float, Float> {
        val n = pts.size
        if (n < 2) return 0f to 0f
        val prev = pts[(index - 1 + n) % n]
        val next = pts[(index + 1) % n]
        val tx = next.x - prev.x
        val ty = next.y - prev.y
        return (tx * strength) to (ty * strength)
    }


    //  EXPANSION

    private fun expandPoint(
        pts: List<MaskPoint>,
        index: Int,
        expansion: Float
    ): MaskPoint {
        if (expansion == 0f) return pts[index]
        val n = pts.size
        val pt = pts[index]
        val prev = pts[(index - 1 + n) % n]
        val next = pts[(index + 1) % n]

        var tx = next.x - prev.x
        var ty = next.y - prev.y
        val len = sqrt(tx * tx + ty * ty).coerceAtLeast(0.0001f)
        tx /= len
        ty /= len

        val nx = -ty
        val ny = tx

        return pt.copy(
            x = pt.x + nx * expansion,
            y = pt.y + ny * expansion
        )
    }


    //  BUILD PATH (cubic bezier)

    fun buildCustomPath(
        pts: List<MaskPoint>,
        w: Float,
        h: Float,
        closed: Boolean,
        expansion: Float,
        rotationDeg: Float,
        centerX: Float,
        centerY: Float
    ): Path? {
        if (pts.size < 2) return null

        val expandedPts = if (expansion != 0f) {
            pts.mapIndexed { i, _ -> expandPoint(pts, i, expansion) }
        } else pts

        val path = Path()
        val cx = centerX * w
        val cy = centerY * h

        val rad = Math.toRadians(rotationDeg.toDouble())
        val cosR = cos(rad).toFloat()
        val sinR = sin(rad).toFloat()

        fun rot(px: Float, py: Float): Pair<Float, Float> {
            if (rotationDeg == 0f) return px to py
            val dx = px - cx
            val dy = py - cy
            return (cx + dx * cosR - dy * sinR) to (cy + dx * sinR + dy * cosR)
        }

        val p0 = expandedPts[0]
        val p0r = rot(p0.x * w, p0.y * h)
        path.moveTo(p0r.first, p0r.second)

        for (i in 1 until expandedPts.size) {
            val prev = expandedPts[i - 1]
            val curr = expandedPts[i]

            if (prev.hasHandles || curr.hasHandles) {
                val c1 = rot(prev.outX * w, prev.outY * h)
                val c2 = rot(curr.inX * w, curr.inY * h)
                val end = rot(curr.x * w, curr.y * h)
                path.cubicTo(c1.first, c1.second, c2.first, c2.second, end.first, end.second)
            } else {
                val end = rot(curr.x * w, curr.y * h)
                path.lineTo(end.first, end.second)
            }
        }

        if (closed && expandedPts.size >= 3) {
            val last = expandedPts.last()
            val first = expandedPts.first()

            if (last.hasHandles || first.hasHandles) {
                val c1 = rot(last.outX * w, last.outY * h)
                val c2 = rot(first.inX * w, first.inY * h)
                val end = rot(first.x * w, first.y * h)
                path.cubicTo(c1.first, c1.second, c2.first, c2.second, end.first, end.second)
            } else {
                path.close()
            }
        }

        return path
    }


    //  DRAW MASK

    fun drawMask(
        canvas: Canvas,
        state: MaskState,
        viewWidth: Float,
        viewHeight: Float
    ) {
        if (!state.isActive) return

        // 🆕 Skip if custom mask is incomplete
        if (state.type == MaskType.CUSTOM && state.customPoints.size < 3) return

        val featherPx = state.feather / 100f * 80f
        val opacityInt = (state.opacity / 100f * 255f).toInt().coerceIn(0, 255)

        val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            style = Paint.Style.FILL
            alpha = opacityInt
        }

        val xfermode = PorterDuffXfermode(
            if (state.isInverted) PorterDuff.Mode.DST_OUT
            else PorterDuff.Mode.DST_IN
        )
        maskPaint.xfermode = xfermode

        // Feather — supports negative (opposite) by using abs for blur, invert logic
        val effectiveFeather = abs(featherPx)
        if (effectiveFeather > 0.5f) {
            maskPaint.maskFilter = BlurMaskFilter(
                effectiveFeather,
                BlurMaskFilter.Blur.NORMAL
            )
        }

        val minDim = minOf(viewWidth, viewHeight)
        val expansionPx = state.expansion / 100f * minDim

        when (state.type) {
            MaskType.CIRCLE -> drawCircle(canvas, state, viewWidth, viewHeight, maskPaint)
            MaskType.RECTANGLE -> drawRectangle(canvas, state, viewWidth, viewHeight, maskPaint)
            MaskType.LINEAR -> drawLinear(canvas, state, viewWidth, viewHeight, maskPaint)
            MaskType.HEART -> drawHeart(canvas, state, viewWidth, viewHeight, maskPaint)
            MaskType.CUSTOM -> drawCustom(
                canvas,
                state,
                viewWidth,
                viewHeight,
                maskPaint,
                expansionPx
            )

            MaskType.NONE -> {}
        }

        maskPaint.xfermode = null
        maskPaint.maskFilter = null
    }

    private fun drawCircle(canvas: Canvas, state: MaskState, w: Float, h: Float, paint: Paint) {
        val cx = state.centerX * w
        val cy = state.centerY * h
        val r = state.radius * minOf(w, h)
        canvas.drawCircle(cx, cy, r, paint)
    }

    private fun drawRectangle(canvas: Canvas, state: MaskState, w: Float, h: Float, paint: Paint) {
        val cx = state.centerX * w
        val cy = state.centerY * h
        val halfW = state.width * w / 2f
        val halfH = state.height * h / 2f
        val cornerPx = state.cornerRadius * minOf(w, h)
        val rect = RectF(cx - halfW, cy - halfH, cx + halfW, cy + halfH)

        val save = canvas.save()
        if (state.rotation != 0f) canvas.rotate(state.rotation, cx, cy)
        if (cornerPx > 1f) canvas.drawRoundRect(rect, cornerPx, cornerPx, paint)
        else canvas.drawRect(rect, paint)
        canvas.restoreToCount(save)
    }

    private fun drawLinear(canvas: Canvas, state: MaskState, w: Float, h: Float, paint: Paint) {
        val cx = state.centerX * w
        val cy = state.centerY * h
        val py = state.positionY * h
        val save = canvas.save()
        canvas.rotate(state.rotation, cx, cy)
        val diag = sqrt(w * w + h * h)
        val rect = RectF(cx - diag, py - diag, cx + diag, py + diag)
        canvas.drawRect(rect, paint)
        canvas.restoreToCount(save)
    }

    private fun drawHeart(canvas: Canvas, state: MaskState, w: Float, h: Float, paint: Paint) {
        val cx = state.centerX * w
        val cy = state.centerY * h
        val s = state.scale * minOf(w, h) * 0.4f
        val path = buildHeartPath(cx, cy, s)

        val save = canvas.save()
        if (state.rotation != 0f) canvas.rotate(state.rotation, cx, cy)
        canvas.drawPath(path, paint)
        canvas.restoreToCount(save)
    }

    private fun drawCustom(
        canvas: Canvas,
        state: MaskState,
        w: Float,
        h: Float,
        paint: Paint,
        expansionPx: Float
    ) {
        val path = buildCustomPath(
            pts = state.customPoints,
            w = w,
            h = h,
            closed = state.customClosed,
            expansion = state.expansion,
            rotationDeg = state.rotation,
            centerX = state.centerX,
            centerY = state.centerY
        ) ?: return

        canvas.drawPath(path, paint)
    }

    private fun buildHeartPath(cx: Float, cy: Float, s: Float): Path {
        val path = Path()
        path.moveTo(cx, cy + 0.6f * s)
        path.cubicTo(
            cx - 0.7f * s, cy + 0.1f * s,
            cx - 1.0f * s, cy - 0.5f * s,
            cx - 0.5f * s, cy - 0.7f * s
        )
        path.cubicTo(
            cx - 0.15f * s, cy - 0.85f * s,
            cx, cy - 0.55f * s,
            cx, cy - 0.3f * s
        )
        path.cubicTo(
            cx, cy - 0.55f * s,
            cx + 0.15f * s, cy - 0.85f * s,
            cx + 0.5f * s, cy - 0.7f * s
        )
        path.cubicTo(
            cx + 1.0f * s, cy - 0.5f * s,
            cx + 0.7f * s, cy + 0.1f * s,
            cx, cy + 0.6f * s
        )
        path.close()
        return path
    }


    //  KEYFRAME CRUD

    fun addKeyframe(state: MaskState, k: MaskKeyframe): MaskState {
        val list = state.keyframes.toMutableList()
        val idx = list.indexOfFirst { abs(it.timeMs - k.timeMs) < 50 }
        if (idx >= 0) list[idx] = k else list.add(k)
        list.sortBy { it.timeMs }
        return state.copy(keyframes = list)
    }

    fun removeKeyframe(state: MaskState, timeMs: Long): MaskState {
        val list = state.keyframes.filter { abs(it.timeMs - timeMs) >= 50 }
        return state.copy(keyframes = list)
    }

    fun clearKeyframes(state: MaskState): MaskState = state.copy(keyframes = emptyList())

    fun hasKeyframeAt(state: MaskState, timeMs: Long): Boolean =
        state.keyframes.any { abs(it.timeMs - timeMs) < 50 }


    //  POINT OPS

    fun addPoint(state: MaskState, pt: MaskPoint): MaskState {
        val base = if (state.type != MaskType.CUSTOM) MaskState(type = MaskType.CUSTOM)
        else state
        return base.copy(customPoints = base.customPoints + pt)
    }

    fun updatePoint(state: MaskState, index: Int, pt: MaskPoint): MaskState {
        if (index !in state.customPoints.indices) return state
        val list = state.customPoints.toMutableList()
        list[index] = pt
        return state.copy(customPoints = list)
    }

    fun removePoint(state: MaskState, index: Int): MaskState {
        if (index !in state.customPoints.indices) return state
        val list = state.customPoints.toMutableList()
        list.removeAt(index)
        return state.copy(customPoints = list)
    }

    fun clearPoints(state: MaskState): MaskState =
        state.copy(customPoints = emptyList())
}