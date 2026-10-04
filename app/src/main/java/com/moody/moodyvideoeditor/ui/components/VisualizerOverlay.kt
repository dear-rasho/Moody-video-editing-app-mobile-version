package com.moody.moodyvideoeditor.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import com.moody.moodyvideoeditor.data.EditorClip
import com.moody.moodyvideoeditor.data.VisualizerState
import com.moody.moodyvideoeditor.utils.VisualizerEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "VIS_OVERLAY"

@Composable
fun VisualizerOverlay(
    state: VisualizerState,
    visualizerClip: EditorClip,
    allClips: List<EditorClip>,
    currentPosMs: Long,
    isPlaying: Boolean,
    enabled: Boolean = true,
    modifier: Modifier = Modifier.fillMaxSize()
) {
    if (!enabled) return

    val context = LocalContext.current

    // ═══════════════════════════════════════════════════════════
    //  FIND LINKED AUDIO CLIP
    // ═══════════════════════════════════════════════════════════
    val linkedAudio = remember(state.linkedAudioClipId, allClips) {
        state.linkedAudioClipId?.let { id ->
            allClips.firstOrNull { it.id == id && it.isAudio }
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  LOAD CENTER IMAGE — robust
    // ═══════════════════════════════════════════════════════════
    LaunchedEffect(state.imageUri, state.showImage) {
        val uri = state.imageUri

        if (uri.isNullOrBlank() || !state.showImage) {
            VisualizerEngine.setCenterImage(null, null)
            return@LaunchedEffect
        }

        try {
            val bmp = withContext(Dispatchers.IO) {
                loadImageBitmap(context, uri)
            }

            if (bmp != null) {
                VisualizerEngine.setCenterImage(uri, bmp)
                Log.e("VIZ_OVERLAY", "✅ Image loaded: $uri")
            } else {
                Log.e("VIZ_OVERLAY", "❌ Image load returned null: $uri")
                VisualizerEngine.setCenterImage(null, null)
            }
        } catch (e: Throwable) {
            Log.e("VIZ_OVERLAY", "❌ Image load failed: ${e.message}", e)
            VisualizerEngine.setCenterImage(null, null)
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  ELAPSED TIME — for idle motion (rotation, waves)
    // ═══════════════════════════════════════════════════════════
    var elapsedSec by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(isPlaying) {
        if (!isPlaying) return@LaunchedEffect
        val startNanos = System.nanoTime()
        val baseElapsed = elapsedSec
        while (true) {
            withFrameNanos { _ ->
                elapsedSec = baseElapsed +
                        (System.nanoTime() - startNanos) / 1_000_000_000f
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  COMPUTE relativeMs FROM PLAYHEAD
    //  Maps timeline position → audio source position
    //  SAME formula used in export → perfect sync
    // ═══════════════════════════════════════════════════════════
    val relativeMs = remember(currentPosMs, linkedAudio) {
        val audio = linkedAudio ?: return@remember 0L
        val timelineOffset = (currentPosMs - audio.timelineStartMs).coerceAtLeast(0L)
        val speed = audio.speed.coerceAtLeast(0.01f)
        (audio.sourceStartMs + (timelineOffset * speed).toLong())
    }

    // ═══════════════════════════════════════════════════════════
    //  DRAW
    // ═══════════════════════════════════════════════════════════
    Canvas(modifier = modifier) {
        VisualizerEngine.draw(
            scope = this,
            state = state,
            relativeMs = relativeMs,
            elapsedSec = elapsedSec,
            instanceKey = visualizerClip.id
        )
    }
}

private fun loadImageBitmap(context: Context, uriStr: String): ImageBitmap? {
    return try {
        val uri = Uri.parse(uriStr)
        val resolver = context.contentResolver

        val bmp = if (uri.scheme == "file") {
            BitmapFactory.decodeFile(uri.path)
        } else {
            try {
                resolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream)
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Content resolver failed, fallback to file", e)
                try {
                    BitmapFactory.decodeFile(uri.path)
                } catch (_: Throwable) {
                    null
                }
            }
        }

        if (bmp == null) {
            Log.e(TAG, "Bitmap decode returned null for: $uriStr")
            return null
        }

        // Downscale if too large
        val maxDim = 1024
        val scaled = if (bmp.width > maxDim || bmp.height > maxDim) {
            val scale = maxDim.toFloat() / maxOf(bmp.width, bmp.height)
            val newW = (bmp.width * scale).toInt()
            val newH = (bmp.height * scale).toInt()
            val scaledBmp = Bitmap.createScaledBitmap(bmp, newW, newH, true)
            if (scaledBmp != bmp) bmp.recycle()
            scaledBmp
        } else bmp

        scaled.asImageBitmap()
    } catch (e: Throwable) {
        Log.e(TAG, "Image decode failed: ${e.message}", e)
        null
    }
}