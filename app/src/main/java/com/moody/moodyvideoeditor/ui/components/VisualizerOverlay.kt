package com.moody.moodyvideoeditor.ui.components

import android.content.Context
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
    //  LOAD CENTER IMAGE
    // ═══════════════════════════════════════════════════════════
    LaunchedEffect(state.imageUri) {
        val uri = state.imageUri
        if (uri.isNullOrBlank()) {
            VisualizerEngine.setCenterImage(null, null)
            return@LaunchedEffect
        }
        val bmp = withContext(Dispatchers.IO) {
            loadImageBitmap(context, uri)
        }
        VisualizerEngine.setCenterImage(uri, bmp)
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
    //  DRAW — passes relativeMs (not rawFft) to engine
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
            resolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream)
            }
        }
        bmp?.asImageBitmap()
    } catch (e: Throwable) {
        Log.e(TAG, "Image decode failed", e)
        null
    }
}