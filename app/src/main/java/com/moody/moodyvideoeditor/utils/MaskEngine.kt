package com.moody.moodyvideoeditor.utils

import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import com.moody.moodyvideoeditor.data.Keyframe
import com.moody.moodyvideoeditor.data.MaskAnimatableProps
import com.moody.moodyvideoeditor.data.MaskKeyframe
import com.moody.moodyvideoeditor.data.MaskPoint
import com.moody.moodyvideoeditor.data.MaskState
import com.moody.moodyvideoeditor.data.MaskType
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

object MaskEngine {

    // ═══════════════════════════════════════════════════════════════
    //  PER-PROPERTY SAMPLING
    //  Each mask property animates independently via its own
    //  keyframe track. If a property has no keyframes, base value
    //  is returned (no interpolation).
    // ═══════════════════════════════════════════════════════════════

    fun sampleProperty(
        state: MaskState,
        prop: String,
        timeSec: Float
    ): Float {
        val base = state.valueOf(prop)
        val kfs = state.propertyKeyframes[prop]?.sortedBy { it.time } ?: return base
        if (kfs.isEmpty()) return base
        val sampled = KeyframeStore.sample(
            mapOf(prop to kfs),
            prop,
            timeSec,
            base
        )
        return when (prop) {
            "cornerRadius" -> sampled.coerceAtLeast(0f)
            "feather" -> sampled.coerceIn(0f, 300f)
            else -> sampled
        }
    }

    fun sampleAt(state: MaskState, timeSec: Float): MaskState {
        if (!state.hasPropertyKeyframes) {
            if (state.keyframes.isEmpty()) return state
            return sampleLegacy(state, timeSec)
        }

        var result = state
        MaskAnimatableProps.ALL.forEach { prop ->
            val v = sampleProperty(state, prop, timeSec)
            result = result.withValue(prop, v)
        }
        return result
    }

    fun trimToTime(state: MaskState, cutMs: Long): MaskState {
        if (cutMs <= 0L) return state
        val cutSec = cutMs / 1000f
        val sampled = sampleAt(state, cutSec)
        val propertyTracks = state.propertyKeyframes.mapNotNull { (prop, keyframes) ->
            val sorted = keyframes.sortedBy { it.time }
            if (sorted.isEmpty()) return@mapNotNull null
            val startEase = sorted.lastOrNull { it.time <= cutSec }?.ease
                ?: sorted.first().ease
            val start = Keyframe(
                time = 0f,
                value = sampleProperty(state, prop, cutSec),
                ease = startEase
            )
            val remaining = sorted.filter { it.time > cutSec }.map {
                it.copy(time = it.time - cutSec)
            }
            prop to (listOf(start) + remaining)
        }.toMap()

        val legacyKeyframes = if (state.keyframes.isEmpty()) {
            emptyList()
        } else {
            val start = sampleLegacy(state, cutSec).toMaskKeyframe(0L)
            val remaining = state.keyframes.filter { it.timeMs > cutMs }
                .map { it.copy(timeMs = it.timeMs - cutMs) }
            (listOf(start) + remaining).distinctBy { it.timeMs }
                .sortedBy { it.timeMs }
        }

        return sampled.copy(
            propertyKeyframes = propertyTracks,
            keyframes = legacyKeyframes
        )
    }

    private fun MaskState.toMaskKeyframe(timeMs: Long) = MaskKeyframe(
        timeMs = timeMs,
        positionX = positionX,
        positionY = positionY,
        radius = radius,
        width = width,
        height = height,
        rotation = rotation,
        cornerRadius = cornerRadius,
        scale = scale,
        linearPosition = linearPosition,
        feather = feather,
        expansion = expansion,
        opacity = opacity,
        customPoints = customPoints
    )

    // ═══════════════════════════════════════════════════════════════
    //  LEGACY SNAPSHOT KEYFRAMES — backward compat
    // ═══════════════════════════════════════════════════════════════

    private fun sampleLegacy(state: MaskState, timeSec: Float): MaskState {
        val kfs = state.keyframes.sortedBy { it.timeMs }
        if (kfs.isEmpty()) return state
        if (kfs.size == 1) return applySnapshotKeyframe(state, kfs[0])

        val timeMs = (timeSec * 1000f).toLong()
        if (timeMs <= kfs.first().timeMs) {
            return applySnapshotKeyframe(state, kfs.first())
        }
        if (timeMs >= kfs.last().timeMs) {
            return applySnapshotKeyframe(state, kfs.last())
        }

        for (i in 0 until kfs.size - 1) {
            val a = kfs[i]
            val b = kfs[i + 1]
            if (timeMs in a.timeMs..b.timeMs) {
                val span = (b.timeMs - a.timeMs).coerceAtLeast(1L)
                val raw = (timeMs - a.timeMs).toFloat() / span.toFloat()
                val t = KeyframeStore.easeFn(raw, a.ease)
                return lerpSnapshot(state, a, b, t)
            }
        }
        return state
    }

    private fun applySnapshotKeyframe(
        base: MaskState,
        k: MaskKeyframe
    ): MaskState = base.copy(
        positionX = k.positionX,
        positionY = k.positionY,
        radius = k.radius,
        width = k.width,
        height = k.height,
        rotation = k.rotation,
        cornerRadius = k.cornerRadius.coerceAtLeast(0f),
        scale = k.scale,
        linearPosition = k.linearPosition,
        feather = k.feather.coerceIn(0f, 300f),
        expansion = k.expansion,
        opacity = k.opacity,
        customPoints = if (k.customPoints.isNotEmpty()) k.customPoints
        else base.customPoints
    )

    private fun lerpSnapshot(
        base: MaskState,
        a: MaskKeyframe,
        b: MaskKeyframe,
        t: Float
    ): MaskState {
        val interpPoints: List<MaskPoint> = when {
            a.customPoints.isEmpty() -> b.customPoints
            b.customPoints.isEmpty() -> a.customPoints
            a.customPoints.size == b.customPoints.size ->
                a.customPoints.zip(b.customPoints) { pa, pb ->
                    pa.lerp(pb, t)
                }

            else -> if (t < 0.5f) a.customPoints else b.customPoints
        }

        fun lerpF(x: Float, y: Float) = x + (y - x) * t

        return base.copy(
            positionX = lerpF(a.positionX, b.positionX),
            positionY = lerpF(a.positionY, b.positionY),
            radius = lerpF(a.radius, b.radius),
            width = lerpF(a.width, b.width),
            height = lerpF(a.height, b.height),
            rotation = lerpF(a.rotation, b.rotation),
            cornerRadius = lerpF(a.cornerRadius, b.cornerRadius).coerceAtLeast(0f),
            scale = lerpF(a.scale, b.scale),
            linearPosition = lerpF(a.linearPosition, b.linearPosition),
            feather = lerpF(a.feather, b.feather).coerceIn(0f, 300f),
            expansion = lerpF(a.expansion, b.expansion),
            opacity = lerpF(a.opacity, b.opacity),
            customPoints = interpPoints
        )
    }

    // ═══════════════════════════════════════════════════════════════
    //  AUTO HANDLES
    // ═══════════════════════════════════════════════════════════════

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

    // ═══════════════════════════════════════════════════════════════
    //  EXPANSION
    // ═══════════════════════════════════════════════════════════════

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

        val signedArea = pts.indices.fold(0f) { area, pointIndex ->
            val current = pts[pointIndex]
            val following = pts[(pointIndex + 1) % n]
            area + current.x * following.y - following.x * current.y
        }
        val normalDirection = if (signedArea >= 0f) -1f else 1f

        var tx = next.x - prev.x
        var ty = next.y - prev.y
        val len = sqrt(tx * tx + ty * ty).coerceAtLeast(0.0001f)
        tx /= len
        ty /= len

        val nx = -ty * normalDirection
        val ny = tx * normalDirection

        return pt.copy(
            x = pt.x + nx * expansion,
            y = pt.y + ny * expansion
        )
    }

    fun expandedPoints(
        pts: List<MaskPoint>,
        expansion: Float
    ): List<MaskPoint> =
        if (expansion == 0f || pts.size < 3) pts
        else pts.mapIndexed { index, _ ->
            expandPoint(pts, index, expansion)
        }

    // ═══════════════════════════════════════════════════════════════
    //  BUILD CUSTOM PATH (with bezier handles)
    // ═══════════════════════════════════════════════════════════════

    fun buildCustomPath(
        pts: List<MaskPoint>,
        w: Float,
        h: Float,
        closed: Boolean,
        expansion: Float,
        rotationDeg: Float,
        positionX: Float,
        positionY: Float
    ): Path? {
        if (pts.size < 2) return null

        val expandedPts = expandedPoints(pts, expansion)

        val path = Path()
        val cx = positionX * w
        val cy = positionY * h

        val rad = Math.toRadians(rotationDeg.toDouble())
        val cosR = cos(rad).toFloat()
        val sinR = sin(rad).toFloat()

        fun rot(px: Float, py: Float): Pair<Float, Float> {
            if (rotationDeg == 0f) return px to py
            val dx = px - cx
            val dy = py - cy
            return (cx + dx * cosR - dy * sinR) to
                    (cy + dx * sinR + dy * cosR)
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
                path.cubicTo(
                    c1.first, c1.second,
                    c2.first, c2.second,
                    end.first, end.second
                )
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
                path.cubicTo(
                    c1.first, c1.second,
                    c2.first, c2.second,
                    end.first, end.second
                )
            }
            path.close()
        }

        return path
    }

    // ═══════════════════════════════════════════════════════════════
    //  ROUNDED CUSTOM PATH
    //  Each corner replaced with a quadratic arc.
    //  NOTE: android.graphics.Path uses quadTo (not quadraticBezierTo)
    // ═══════════════════════════════════════════════════════════════

    fun buildRoundedCustomPath(
        pts: List<MaskPoint>,
        w: Float,
        h: Float,
        closed: Boolean,
        expansion: Float,
        rotationDeg: Float,
        positionX: Float,
        positionY: Float,
        cornerRadius: Float
    ): Path? {
        if (pts.size < 3 || !closed) {
            return buildCustomPath(
                pts, w, h, closed, expansion, rotationDeg, positionX, positionY
            )
        }

        val expandedPts = expandedPoints(pts, expansion)
        val cx = positionX * w
        val cy = positionY * h
        val rad = Math.toRadians(rotationDeg.toDouble())
        val cosR = cos(rad).toFloat()
        val sinR = sin(rad).toFloat()
        val cornerPx = cornerRadius.coerceAtLeast(0f) * minOf(w, h)

        fun rot(px: Float, py: Float): Pair<Float, Float> {
            if (rotationDeg == 0f) return px to py
            val dx = px - cx
            val dy = py - cy
            return (cx + dx * cosR - dy * sinR) to
                    (cy + dx * sinR + dy * cosR)
        }

        val absPts = expandedPts.map { p ->
            rot(p.x * w, p.y * h)
        }

        val path = Path()
        val n = absPts.size

        for (i in 0 until n) {
            val prev = absPts[(i - 1 + n) % n]
            val curr = absPts[i]
            val next = absPts[(i + 1) % n]

            val vInX = prev.first - curr.first
            val vInY = prev.second - curr.second
            val vOutX = next.first - curr.first
            val vOutY = next.second - curr.second
            val lenIn = sqrt(vInX * vInX + vInY * vInY)
                .coerceAtLeast(0.0001f)
            val lenOut = sqrt(vOutX * vOutX + vOutY * vOutY)
                .coerceAtLeast(0.0001f)

            val r = cornerPx
                .coerceAtMost(lenIn * 0.5f)
                .coerceAtMost(lenOut * 0.5f)

            val entryX = curr.first + vInX / lenIn * r
            val entryY = curr.second + vInY / lenIn * r
            val exitX = curr.first + vOutX / lenOut * r
            val exitY = curr.second + vOutY / lenOut * r

            if (i == 0) path.moveTo(entryX, entryY)
            else path.lineTo(entryX, entryY)

            path.quadTo(curr.first, curr.second, exitX, exitY)
        }
        path.close()
        return path
    }

    // ═══════════════════════════════════════════════════════════════
    //  DRAW MASK — main entry
    //  DST_IN = keep inside mask (hide outside)  ← default
    //  DST_OUT = hide inside mask (keep outside) ← inverted
    // ═══════════════════════════════════════════════════════════════

    fun drawMask(
        canvas: Canvas,
        state: MaskState,
        viewWidth: Float,
        viewHeight: Float
    ) {
        if (!state.isActive) return

        if (state.type == MaskType.CUSTOM && !state.hasCustomPath) return

        val featherPx = state.feather / 100f * minOf(viewWidth, viewHeight) / 9f
        val opacityInt = (state.opacity / 100f * 255f).toInt().coerceIn(0, 255)

        val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            style = Paint.Style.FILL
            alpha = opacityInt
        }

        val compositePaint = Paint().apply {
            xfermode = PorterDuffXfermode(
            if (state.isInverted) PorterDuff.Mode.DST_OUT
            else PorterDuff.Mode.DST_IN
            )
        }

        if (abs(featherPx) > 0.5f) {
            maskPaint.maskFilter = BlurMaskFilter(
                abs(featherPx),
                BlurMaskFilter.Blur.NORMAL
            )
        }

        val minDim = minOf(viewWidth, viewHeight).coerceAtLeast(1f)
        val expansionPx = state.expansion / 100f * minDim

        val layerSaveCount = canvas.saveLayer(
            RectF(0f, 0f, viewWidth, viewHeight),
            compositePaint
        )
        try {
            when (state.type) {
                MaskType.CIRCLE -> drawCircle(canvas, state, viewWidth, viewHeight, maskPaint, minDim, expansionPx)
                MaskType.RECTANGLE -> drawRectangle(canvas, state, viewWidth, viewHeight, maskPaint)
                MaskType.LINEAR -> drawLinear(canvas, state, viewWidth, viewHeight, maskPaint)
                MaskType.HEART -> drawHeart(canvas, state, viewWidth, viewHeight, maskPaint, minDim, expansionPx)
                MaskType.CUSTOM -> drawCustom(canvas, state, viewWidth, viewHeight, maskPaint, expansionPx)
                MaskType.NONE -> {}
            }
        } finally {
            canvas.restoreToCount(layerSaveCount)
            maskPaint.reset()
            compositePaint.reset()
        }
    }
    fun drawMaskedLayer(
        canvas: Canvas,
        state: MaskState,
        timeSec: Float,
        viewWidth: Float,
        viewHeight: Float,
        drawContent: () -> Unit
    ) {
        if (!state.isActive) {
            drawContent()
            return
        }
        val saveCount = canvas.saveLayer(
            RectF(0f, 0f, viewWidth, viewHeight),
            null
        )
        try {
            drawContent()
            drawMask(
                canvas,
                sampleAt(state, timeSec),
                viewWidth,
                viewHeight
            )
        } finally {
            canvas.restoreToCount(saveCount)
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  SHAPE DRAWERS
    // ═══════════════════════════════════════════════════════════════

    private fun drawCircle(
        canvas: Canvas,
        state: MaskState,
        w: Float,
        h: Float,
        paint: Paint,
        minDim: Float,
        expansionPx: Float
    ) {
        val cx = state.positionX * w
        val cy = state.positionY * h
        val r = (state.radius * minDim + expansionPx).coerceAtLeast(0f)

        val save = canvas.save()
        if (state.rotation != 0f) canvas.rotate(state.rotation, cx, cy)
        canvas.drawCircle(cx, cy, r, paint)
        canvas.restoreToCount(save)
    }

    private fun drawRectangle(
        canvas: Canvas,
        state: MaskState,
        w: Float,
        h: Float,
        paint: Paint
    ) {
        val cx = state.positionX * w
        val cy = state.positionY * h
        val expansionPx = state.expansion / 100f * minOf(w, h)
        val halfW = (state.width * w / 2f + expansionPx).coerceAtLeast(0f)
        val halfH = (state.height * h / 2f + expansionPx).coerceAtLeast(0f)
        val cornerPx = state.cornerRadius * minOf(w, h)
        val absCorner = cornerPx

        val save = canvas.save()
        if (state.rotation != 0f) canvas.rotate(state.rotation, cx, cy)

        if (absCorner <= 1f) {
            // Sharp rectangle
            canvas.drawRect(
                RectF(cx - halfW, cy - halfH, cx + halfW, cy + halfH),
                paint
            )
        } else {
            canvas.drawRoundRect(
                RectF(cx - halfW, cy - halfH, cx + halfW, cy + halfH),
                absCorner.coerceAtMost(minOf(halfW, halfH)),
                absCorner.coerceAtMost(minOf(halfW, halfH)),
                paint
            )
        }
        canvas.restoreToCount(save)
    }

    private fun drawLinear(
        canvas: Canvas,
        state: MaskState,
        w: Float,
        h: Float,
        paint: Paint
    ) {
        val cx = state.positionX * w
        val cy = state.positionY * h
        val expansionPx = state.expansion / 100f * minOf(w, h)
        val py = (state.linearPosition * h) - expansionPx

        val save = canvas.save()
        canvas.rotate(state.rotation, cx, cy)
        val diag = sqrt(w * w + h * h)
        val rect = RectF(cx - diag, py - diag, cx + diag, py + diag)
        canvas.drawRect(rect, paint)
        canvas.restoreToCount(save)
    }

    private fun drawHeart(
        canvas: Canvas,
        state: MaskState,
        w: Float,
        h: Float,
        paint: Paint,
        minDim: Float,
        expansionPx: Float
    ) {
        val cx = state.positionX * w
        val cy = state.positionY * h
        val s = (state.scale * minDim * 0.4f + expansionPx)
            .coerceAtLeast(0f)
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
        val path = if (state.cornerRadius > 0.001f &&
            state.customPoints.size >= 3
        ) {
            buildRoundedCustomPath(
                pts = state.customPoints,
                w = w, h = h,
                closed = state.customClosed,
                expansion = state.expansion / 100f,
                rotationDeg = state.rotation,
                positionX = state.positionX,
                positionY = state.positionY,
                cornerRadius = state.cornerRadius
            )
        } else {
            buildCustomPath(
                pts = state.customPoints,
                w = w, h = h,
                closed = state.customClosed,
                expansion = state.expansion / 100f,
                rotationDeg = state.rotation,
                positionX = state.positionX,
                positionY = state.positionY
            )
        } ?: return
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

    // ═══════════════════════════════════════════════════════════════
    //  LEGACY SNAPSHOT KEYFRAME CRUD — for old saved projects
    // ═══════════════════════════════════════════════════════════════

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

    fun clearKeyframes(state: MaskState): MaskState =
        state.copy(keyframes = emptyList())

    fun hasKeyframeAt(state: MaskState, timeMs: Long): Boolean =
        state.keyframes.any { abs(it.timeMs - timeMs) < 50 }

    // ═══════════════════════════════════════════════════════════════
    //  POINT CRUD
    // ═══════════════════════════════════════════════════════════════

    fun addPoint(state: MaskState, pt: MaskPoint): MaskState {
        val base = if (state.type != MaskType.CUSTOM)
            MaskState(type = MaskType.CUSTOM)
        else state
        return base.copy(customPoints = base.customPoints + pt)
    }

    fun updatePoint(
        state: MaskState,
        index: Int,
        pt: MaskPoint
    ): MaskState {
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