package com.moody.moodyvideoeditor.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PorterDuff
import android.util.Log
import com.moody.moodyvideoeditor.data.EditorClip
import java.io.File
import java.io.FileOutputStream

object VisualizerBitmapRenderer {

    private const val TAG = "VIZ_RENDER"
    private const val CHUNK_SIZE = 300

    /**
     * Render visualizer overlay PNG sequences for export.
     *
     * @param rangeStart  Real timeline offset — used to compute audio-relative time
     * @param allClips    All clips (needed to find linked audio for source-relative time)
     */
    fun renderCombinedOverlays(
        context: Context,
        visualizerClips: List<EditorClip>,
        allClips: List<EditorClip>,
        rangeStart: Long,
        W: Int,
        H: Int,
        fps: Int,
        totalDurationMs: Long
    ): List<TextOverlaySequence> {
        if (visualizerClips.isEmpty()) return emptyList()

        val totalFrames = ((totalDurationMs * fps) / 1000L).toInt().coerceAtLeast(1)
        val sequences = mutableListOf<TextOverlaySequence>()

        // Clean old files
        try {
            context.cacheDir.listFiles()?.forEach { f ->
                if (f.name.startsWith("viz_combined_") && f.name.endsWith(".png")) {
                    f.delete()
                }
            }
        } catch (_: Exception) {
        }

        val sortedViz = visualizerClips.sortedBy { it.timelineStartMs }

        // ═══════════════════════════════════════════════════════
        //  PRELOAD CENTER IMAGES
        // ═══════════════════════════════════════════════════════
        val vizBitmaps = mutableMapOf<String, Bitmap?>()
        sortedViz.forEach { c ->
            val uri = c.visualizer?.imageUri
            if (!uri.isNullOrBlank()) {
                try {
                    val parsed = android.net.Uri.parse(uri)
                    val bmp = context.contentResolver.openInputStream(parsed)?.use {
                        BitmapFactory.decodeStream(it)
                    }
                    vizBitmaps[c.id] = bmp
                } catch (_: Throwable) {
                    vizBitmaps[c.id] = null
                }
            }
        }

        // ═══════════════════════════════════════════════════════
        //  PRECOMPUTE linked audio for each visualizer
        //  (source-relative time needs audio's sourceStartMs + speed)
        // ═══════════════════════════════════════════════════════
        data class AudioInfo(
            val sourceStartMs: Long,
            val speed: Float
        )

        val audioInfoMap = mutableMapOf<String, AudioInfo>()
        sortedViz.forEach { vc ->
            val audioId = vc.visualizer?.linkedAudioClipId
            if (audioId != null) {
                val audioClip = allClips.firstOrNull { it.id == audioId }
                if (audioClip != null) {
                    audioInfoMap[vc.id] = AudioInfo(
                        sourceStartMs = audioClip.sourceStartMs,
                        speed = audioClip.speed.coerceAtLeast(0.01f)
                    )
                }
            }
        }

        var chunkStart = 0
        var chunkIdx = 0

        while (chunkStart < totalFrames) {
            val chunkEnd = (chunkStart + CHUNK_SIZE).coerceAtMost(totalFrames)
            val chunkFrames = chunkEnd - chunkStart

            for (i in 0 until chunkFrames) {
                val globalFrame = chunkStart + i
                val timelineMsLocal = (globalFrame.toLong() * 1000L) / fps
                // Real timeline position
                val timelineMsReal = timelineMsLocal + rangeStart

                val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bmp)
                canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

                sortedViz.forEach { vc ->
                    if (timelineMsReal < vc.timelineStartMs ||
                        timelineMsReal >= vc.timelineEndMs
                    ) return@forEach

                    val state = vc.visualizer ?: return@forEach
                    val imageBmp = vizBitmaps[vc.id]

                    // ═══════════════════════════════════════════
                    //  COMPUTE relativeMs — SAME FORMULA as preview
                    // ═══════════════════════════════════════════
                    val timelineOffset =
                        (timelineMsReal - vc.timelineStartMs).coerceAtLeast(0L)
                    val audioInfo = audioInfoMap[vc.id]
                    val sourceStart = audioInfo?.sourceStartMs ?: 0L
                    val speed = audioInfo?.speed ?: 1.0f

                    val relativeMs = sourceStart +
                            (timelineOffset * speed).toLong()

                    val elapsedSec = timelineMsLocal / 1000f

                    try {
                        VisualizerBitmapHelper.drawVisualizerFrame(
                            canvas = canvas,
                            state = state,
                            relativeMs = relativeMs,
                            elapsedSec = elapsedSec,
                            W = W,
                            H = H,
                            imageBitmap = imageBmp,
                            instanceKey = "export_${vc.id}"
                        )
                    } catch (e: Throwable) {
                        Log.e(TAG, "Visualizer frame failed", e)
                    }
                }

                val file = File(
                    context.cacheDir,
                    "viz_combined_${chunkIdx}_f%05d.png".format(i + 1)
                )
                FileOutputStream(file).use { fos ->
                    bmp.compress(Bitmap.CompressFormat.PNG, 85, fos)
                }
                bmp.recycle()
            }

            val pattern = File(
                context.cacheDir,
                "viz_combined_${chunkIdx}_f%05d.png"
            ).absolutePath

            val startSec = chunkStart.toDouble() / fps.toDouble()
            val endSec = chunkEnd.toDouble() / fps.toDouble()

            sequences.add(
                TextOverlaySequence(
                    pattern = pattern,
                    frameCount = chunkFrames,
                    fps = fps,
                    startNumber = 1,
                    startSec = startSec,
                    endSec = endSec
                )
            )

            chunkStart = chunkEnd
            chunkIdx++
        }

        Log.e(TAG, "Rendered ${sequences.size} chunk(s), frames=$totalFrames")
        return sequences
    }
}