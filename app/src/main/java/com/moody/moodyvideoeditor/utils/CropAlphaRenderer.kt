package com.moody.moodyvideoeditor.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.media.MediaMetadataRetriever
import android.util.Log
import com.moody.moodyvideoeditor.data.EditorClip
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

data class CropAlphaSequence(
    val clipId: String,
    val pattern: String,
    val fps: Int,
    val startNumber: Int,
    val directory: File,
    val isStatic: Boolean,
    val staticFile: File?
)

object CropAlphaRenderer {

    suspend fun render(
        context: Context,
        clips: List<EditorClip>,
        canvasW: Int,
        canvasH: Int,
        fps: Int,
        totalMs: Long,
        onProgress: (Float) -> Unit = {},
        shouldCancel: () -> Boolean = { false }
    ): List<CropAlphaSequence> = withContext(Dispatchers.IO) {

        if (clips.isEmpty()) return@withContext emptyList()

        val results = mutableListOf<CropAlphaSequence>()
        val totalFrames = ((totalMs * fps) / 1000L).toInt().coerceAtLeast(1)

        clips.forEachIndexed { idx, clip ->
            if (shouldCancel()) throw CancellationException("crop cancelled")

            val dims = probeDims(context, clip)
            if (dims.first <= 0 || dims.second <= 0) return@forEachIndexed

            val isAnimated = listOf("cropL", "cropR", "cropT", "cropB")
                .any { clip.keyframes[it].orEmpty().size > 1 }

            val dir = File(context.cacheDir, "crop_alpha_${UUID.randomUUID()}")
            dir.mkdirs()

            val sequence = if (isAnimated) {
                for (frame in 0 until totalFrames) {
                    if (shouldCancel()) throw CancellationException("crop cancelled")
                    val timelineMs = (frame.toLong() * 1000L) / fps
                    val localSec = ((timelineMs - clip.timelineStartMs).coerceAtLeast(0L)) / 1000f
                    val t = TransformApplier.resolveLive(clip, localSec)
                    val bmp = renderFrame(canvasW, canvasH, dims, t)
                    val f = File(dir, "c_%05d.png".format(frame + 1))
                    FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    bmp.recycle()
                    onProgress(((idx + (frame + 1f) / totalFrames) / clips.size))
                }
                CropAlphaSequence(
                    clipId = clip.id,
                    pattern = File(dir, "c_%05d.png").absolutePath,
                    fps = fps,
                    startNumber = 1,
                    directory = dir,
                    isStatic = false,
                    staticFile = null
                )
            } else {
                val t = TransformApplier.baseOf(clip)
                val bmp = renderFrame(canvasW, canvasH, dims, t)
                val f = File(dir, "c_static.png")
                FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bmp.recycle()
                onProgress(((idx + 1f) / clips.size))
                CropAlphaSequence(
                    clipId = clip.id,
                    pattern = f.absolutePath,
                    fps = fps,
                    startNumber = 1,
                    directory = dir,
                    isStatic = true,
                    staticFile = f
                )
            }
            results.add(sequence)
        }
        Log.e("CROP_ALPHA", "Rendered ${results.size} sequences")
        results
    }

    private fun renderFrame(
        W: Int, H: Int,
        dims: Pair<Int, Int>,
        t: TransformValues
    ): Bitmap {
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.BLACK)

        val imgAspect = dims.first.toFloat() / dims.second
        val bounds = fittedBoundsFractions(W.toFloat(), H.toFloat(), imgAspect)
        val fitW = bounds.first
        val fitH = bounds.second
        val leftOff = (1f - fitW) / 2f
        val topOff = (1f - fitH) / 2f

        val x1 = (leftOff + t.cropL * fitW) * W
        val y1 = (topOff + t.cropT * fitH) * H
        val x2 = (leftOff + (1f - t.cropR) * fitW) * W
        val y2 = (topOff + (1f - t.cropB) * fitH) * H

        if (x2 > x1 && y2 > y1) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
            c.drawRect(RectF(x1, y1, x2, y2), paint)
        }
        return bmp
    }

    private fun fittedBoundsFractions(W: Float, H: Float, ar: Float): Pair<Float, Float> {
        if (W <= 0f || H <= 0f || ar <= 0f || !ar.isFinite()) return 1f to 1f
        val car = W / H
        return if (ar >= car) 1f to (car / ar) else (ar / car) to 1f
    }

    private fun probeDims(context: Context, clip: EditorClip): Pair<Int, Int> {
        return try {
            if (clip.type.startsWith("image/")) {
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(clip.uri)?.use {
                    BitmapFactory.decodeStream(it, null, opts)
                }
                opts.outWidth to opts.outHeight
            } else {
                val r = MediaMetadataRetriever()
                try {
                    r.setDataSource(context, clip.uri)
                    val w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
                    val h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
                    val rot = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
                    if (rot == 90 || rot == 270) h to w else w to h
                } finally { r.release() }
            }
        } catch (_: Throwable) { 0 to 0 }
    }
}