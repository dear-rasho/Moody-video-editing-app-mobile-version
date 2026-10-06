@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.moody.moodyvideoeditor.ui.screens

import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.moody.moodyvideoeditor.data.AdjustmentData
import com.moody.moodyvideoeditor.data.BeatsState
import com.moody.moodyvideoeditor.data.BrushType
import com.moody.moodyvideoeditor.data.ChromaState
import com.moody.moodyvideoeditor.data.ColorWheelState
import com.moody.moodyvideoeditor.data.MaskState
import com.moody.moodyvideoeditor.data.OverlayState
import com.moody.moodyvideoeditor.data.RatioLibrary
import com.moody.moodyvideoeditor.data.TransitionState
import com.moody.moodyvideoeditor.data.VisualizerState
import com.moody.moodyvideoeditor.ui.components.ControlBar
import com.moody.moodyvideoeditor.ui.components.FeatureShelf
import com.moody.moodyvideoeditor.ui.components.PlaybackControls
import com.moody.moodyvideoeditor.ui.components.PreviewCanvas
import com.moody.moodyvideoeditor.ui.components.Timeline
import com.moody.moodyvideoeditor.ui.components.TimelineToolbar
import com.moody.moodyvideoeditor.ui.features.AdjustmentsPanel
import com.moody.moodyvideoeditor.ui.features.AnimationsPanel
import com.moody.moodyvideoeditor.ui.features.AspectRatioPanel
import com.moody.moodyvideoeditor.ui.features.AudioPanel
import com.moody.moodyvideoeditor.ui.features.BeatsPanel
import com.moody.moodyvideoeditor.ui.features.BrushPanel
import com.moody.moodyvideoeditor.ui.features.ChromaKeyPanel
import com.moody.moodyvideoeditor.ui.features.ColorWheelPanel
import com.moody.moodyvideoeditor.ui.features.CropPanel
import com.moody.moodyvideoeditor.ui.features.EffectsPanel
import com.moody.moodyvideoeditor.ui.features.ExportDialog
import com.moody.moodyvideoeditor.ui.features.ExportOverlay
import com.moody.moodyvideoeditor.ui.features.FiltersPanel
import com.moody.moodyvideoeditor.ui.features.FreezePanel
import com.moody.moodyvideoeditor.ui.features.MaskPanel
import com.moody.moodyvideoeditor.ui.features.MotionPanel
import com.moody.moodyvideoeditor.ui.features.MusicPanel
import com.moody.moodyvideoeditor.ui.features.OverlaysPanel
import com.moody.moodyvideoeditor.ui.features.PromptPanel
import com.moody.moodyvideoeditor.ui.features.SpeedPanel
import com.moody.moodyvideoeditor.ui.features.StickersPanel
import com.moody.moodyvideoeditor.ui.features.TextPanel
import com.moody.moodyvideoeditor.ui.features.TransformPanel
import com.moody.moodyvideoeditor.ui.features.TransitionsPanel
import com.moody.moodyvideoeditor.ui.features.TrimPanel
import com.moody.moodyvideoeditor.ui.features.VisualizerPanel
import com.moody.moodyvideoeditor.ui.features.VolumePanel
import com.moody.moodyvideoeditor.utils.AudioPreviewEngine
import com.moody.moodyvideoeditor.utils.CropEngine
import com.moody.moodyvideoeditor.utils.PromptEngine
import com.moody.moodyvideoeditor.utils.PromptExecutor
import com.moody.moodyvideoeditor.utils.SpeedEngine
import com.moody.moodyvideoeditor.utils.TransformApplier
import com.moody.moodyvideoeditor.utils.TransformValues
import com.moody.moodyvideoeditor.utils.VideoUtils
import com.moody.moodyvideoeditor.utils.VisualizerEngine
import com.moody.moodyvideoeditor.viewmodel.EditorViewModel
import com.moody.moodyvideoeditor.viewmodel.ExportUiStateBundle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs


private data class MediaInfo(
    val name: String,
    val durationMs: Long,
    val mimeType: String,
    val finalUri: Uri
)

@Composable
fun EditorScreen(
    projectId: String = "",
    onBack: () -> Unit,
    startInCodeMode: Boolean = false,
    viewModel: EditorViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    // Observe export state from service
    val exportState by viewModel.exportState.collectAsState()

    // ═══════════════════════════════════════════════════════════
    //  PROJECT LOAD + AUTO-SAVE
    // ═══════════════════════════════════════════════════════════

    LaunchedEffect(projectId) {
        if (projectId.isNotBlank() && viewModel.getProjectId() != projectId) {
            if (!viewModel.loadProject(context, projectId)) {
                viewModel.setProjectMeta(
                    projectId,
                    if (startInCodeMode) "Code Project" else "New Project"
                )
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                viewModel.saveCurrentProject(context)
            } catch (_: Exception) {
            }
        }
    }

    LaunchedEffect(state.clips) {
        if (projectId.isNotBlank()) {
            delay(1500)
            try {
                viewModel.saveCurrentProject(context)
            } catch (_: Exception) {
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  PANEL STATE
    // ═══════════════════════════════════════════════════════════

    var activePanel by remember {
        mutableStateOf<String?>(if (startInCodeMode) "code" else null)
    }
    var openTextEditor by remember { mutableStateOf(false) }

    val featureScrollState = androidx.compose.foundation.rememberScrollState()

    var filterEditLayerId by remember { mutableStateOf<String?>(null) }
    var effectEditLayerId by remember { mutableStateOf<String?>(null) }

    // Brush state
    var isDrawingMode by remember { mutableStateOf(false) }
    var isMaskPenMode by remember { mutableStateOf(false) }
    var brushType by remember { mutableStateOf(BrushType.PEN) }
    var brushColor by remember { mutableStateOf(0xFFFF0000L) }
    var brushWidth by remember { mutableFloatStateOf(20f) }
    var brushOpacity by remember { mutableFloatStateOf(1f) }
    var brushGradient by remember {
        mutableStateOf(com.moody.moodyvideoeditor.data.BrushGradient())
    }

    // Import state
    var pendingUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var pendingVizImageUri by remember { mutableStateOf<String?>(null) }

    // Visualizer detection state
    var isDetectingBeats by remember { mutableStateOf(false) }
    var beatDetectionProgress by remember { mutableFloatStateOf(0f) }
    var beatDetectionError by remember { mutableStateOf<String?>(null) }

    // Export dialog state
    var exportStartMs by remember { mutableLongStateOf(0L) }
    var exportEndMs by remember { mutableLongStateOf(0L) }
    var useCustomRange by remember { mutableStateOf(false) }
    var showCancelConfirm by remember { mutableStateOf(false) }

    // Prompt state
    var promptFeedback by remember { mutableStateOf("") }
    var promptFeedbackType by remember { mutableStateOf("none") }

    // Playback state
    var isPlaybackActive by remember { mutableStateOf(false) }

    // ═══════════════════════════════════════════════════════════
    //  EXOPLAYERS
    // ═══════════════════════════════════════════════════════════

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply { playWhenReady = false }
    }

    val audioExoPlayer = remember {
        ExoPlayer.Builder(context).build().apply { playWhenReady = false }
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
            audioExoPlayer.release()
        }
    }

    // Visualizer cache cleanup
    DisposableEffect(Unit) {
        onDispose {
            VisualizerEngine.clearCache()
        }
    }

    // Audio FX preview release on panel change
    LaunchedEffect(activePanel) {
        if (activePanel != "audiofx" && activePanel != "soundfx") {
            AudioPreviewEngine.release()
            try {
                exoPlayer.playbackParameters =
                    androidx.media3.common.PlaybackParameters(1f, 1f)
                audioExoPlayer.playbackParameters =
                    androidx.media3.common.PlaybackParameters(1f, 1f)
            } catch (_: Throwable) {
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  PREVIEW CLEAR ON TRACK HIDE
    // ═══════════════════════════════════════════════════════════

    LaunchedEffect(state.hiddenVisualTracks, filterEditLayerId, effectEditLayerId) {
        val editFilter = filterEditLayerId?.let { id ->
            state.clips.firstOrNull { it.id == id }
        }
        if (editFilter != null &&
            state.hiddenVisualTracks.contains(editFilter.trackIndex)
        ) {
            viewModel.clearPreviewFilters()
        }

        val editEffect = effectEditLayerId?.let { id ->
            state.clips.firstOrNull { it.id == id }
        }
        if (editEffect != null &&
            state.hiddenVisualTracks.contains(editEffect.trackIndex)
        ) {
            viewModel.clearPreviewEffect()
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  MUTE STATE HELPERS
    // ═══════════════════════════════════════════════════════════

    val activeClipTrackMuted = remember(state.selectedClip, state.mutedAudioTracks) {
        val sel = state.selectedClip
        sel != null && sel.isAudio && state.mutedAudioTracks.contains(sel.trackIndex)
    }

    val anyActiveAudioMuted = remember(
        state.clips, state.currentPosMs, state.mutedAudioTracks
    ) {
        state.clips.any { c ->
            c.isAudio &&
                    state.currentPosMs >= c.timelineStartMs &&
                    state.currentPosMs < c.timelineEndMs &&
                    state.mutedAudioTracks.contains(c.trackIndex)
        }
    }

    LaunchedEffect(state.volume, state.isMuted, activeClipTrackMuted, anyActiveAudioMuted) {
        exoPlayer.volume = when {
            state.isMuted -> 0f
            activeClipTrackMuted -> 0f
            anyActiveAudioMuted -> 0f
            else -> state.volume
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  VIDEO PLAYBACK SYNC
    // ═══════════════════════════════════════════════════════════

    LaunchedEffect(state.currentPosMs, state.clips, state.hiddenVisualTracks) {
        val playheadMs = state.currentPosMs
        val activeClip = state.clips
            .filter {
                it.isVisualClip &&
                        !it.type.startsWith("image/") &&
                        playheadMs >= it.timelineStartMs &&
                        playheadMs < it.timelineEndMs &&
                        !state.hiddenVisualTracks.contains(it.trackIndex)
            }
            .maxByOrNull { it.trackIndex }

        if (activeClip == null) {
            if (exoPlayer.isPlaying) exoPlayer.pause()
            return@LaunchedEffect
        }

        val currentUri = exoPlayer.currentMediaItem?.localConfiguration?.uri
        if (currentUri != activeClip.uri) {
            val mediaItem = MediaItem.Builder()
                .setUri(activeClip.uri)
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(activeClip.sourceStartMs)
                        .setEndPositionMs(activeClip.sourceEndMs)
                        .build()
                )
                .build()
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
        }

        val speed = activeClip.speed.coerceAtLeast(0.01f)
        val playheadOffset = (playheadMs - activeClip.timelineStartMs)
            .coerceAtLeast(0L)
        val localMs = activeClip.sourceStartMs + (playheadOffset * speed).toLong()
        val clampedLocal = localMs.coerceIn(
            activeClip.sourceStartMs,
            activeClip.sourceEndMs
        )

        val driftThreshold = (150f * speed).toLong().coerceAtLeast(80L)
        val drift = abs(exoPlayer.currentPosition - clampedLocal)
        if (drift > driftThreshold) {
            try {
                exoPlayer.seekTo(clampedLocal)
            } catch (_: Exception) {
            }
        }

        val targetSpeed = SpeedEngine.clampForExoPlayer(activeClip.speed)
        val currentSpeed = exoPlayer.playbackParameters.speed
        if (abs(currentSpeed - targetSpeed) > 0.01f) {
            try {
                exoPlayer.setPlaybackSpeed(targetSpeed)
            } catch (_: Exception) {
            }
        }

        if (isPlaybackActive && !exoPlayer.isPlaying) {
            exoPlayer.play()
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  AUDIO PLAYBACK SYNC
    // ═══════════════════════════════════════════════════════════

    LaunchedEffect(state.currentPosMs, state.clips, state.mutedAudioTracks) {
        val playheadMs = state.currentPosMs
        val activeAudio = state.clips
            .filter {
                it.isAudio &&
                        !it.isAudioEffectClip &&
                        it.uri.toString().isNotBlank() &&
                        it.uri != Uri.EMPTY &&
                        playheadMs >= it.timelineStartMs &&
                        playheadMs < it.timelineEndMs &&
                        !state.mutedAudioTracks.contains(it.trackIndex)
            }
            .maxByOrNull { it.trackIndex }

        if (activeAudio == null) {
            if (audioExoPlayer.isPlaying) audioExoPlayer.pause()
            return@LaunchedEffect
        }

        val currentUri = audioExoPlayer.currentMediaItem?.localConfiguration?.uri
        if (currentUri != activeAudio.uri) {
            val mediaItem = MediaItem.Builder()
                .setUri(activeAudio.uri)
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(activeAudio.sourceStartMs)
                        .setEndPositionMs(activeAudio.sourceEndMs)
                        .build()
                )
                .build()
            audioExoPlayer.setMediaItem(mediaItem)
            audioExoPlayer.prepare()
        }

        val speed = activeAudio.speed.coerceAtLeast(0.01f)
        val playheadOffset = (playheadMs - activeAudio.timelineStartMs)
            .coerceAtLeast(0L)
        val localMs = activeAudio.sourceStartMs + (playheadOffset * speed).toLong()
        val clampedLocal = localMs.coerceIn(
            activeAudio.sourceStartMs,
            activeAudio.sourceEndMs
        )

        val driftThreshold = (150f * speed).toLong().coerceAtLeast(80L)
        val drift = abs(audioExoPlayer.currentPosition - clampedLocal)
        if (drift > driftThreshold) {
            try {
                audioExoPlayer.seekTo(clampedLocal)
            } catch (_: Exception) {
            }
        }

        val targetVolume = if (state.isMuted) 0f else activeAudio.volume
        if (abs(audioExoPlayer.volume - targetVolume) > 0.01f) {
            audioExoPlayer.volume = targetVolume
        }

        val targetSpeed = SpeedEngine.clampForExoPlayer(activeAudio.speed)
        val currentSpeed = audioExoPlayer.playbackParameters.speed
        if (abs(currentSpeed - targetSpeed) > 0.01f) {
            try {
                audioExoPlayer.setPlaybackSpeed(targetSpeed)
            } catch (_: Exception) {
            }
        }

        if (isPlaybackActive && !audioExoPlayer.isPlaying) {
            audioExoPlayer.play()
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  PLAYHEAD TICKER
    // ═══════════════════════════════════════════════════════════

    LaunchedEffect(isPlaybackActive) {
        if (!isPlaybackActive) {
            try {
                audioExoPlayer.pause()
            } catch (_: Exception) {
            }
            return@LaunchedEffect
        }
        var lastWallMs = System.currentTimeMillis()
        while (isPlaybackActive) {
            val now = System.currentTimeMillis()
            val wallDelta = now - lastWallMs
            lastWallMs = now
            val s = viewModel.state.value
            val totalDur = s.totalDurationMs.coerceAtLeast(1000L)
            val next = s.currentPosMs + wallDelta
            if (next >= totalDur) {
                viewModel.setCurrentPos(totalDur)
                isPlaybackActive = false
                exoPlayer.pause()
                audioExoPlayer.pause()
            } else {
                viewModel.setCurrentPos(next)
            }
            delay(33)
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  MEDIA PICKER
    // ═══════════════════════════════════════════════════════════

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            pendingUris = uris
        }
    }

    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri == null) {
            Log.e("FOLDER_PICK", "User cancelled folder picker")
            return@rememberLauncherForActivityResult
        }
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            Log.e("FOLDER_PICK", "Permission granted: $uri")
        } catch (e: Exception) {
            Log.e("FOLDER_PICK", "Permission failed: ${e.message}", e)
        }
        viewModel.setExportFolderUri(uri.toString())
        Log.e("FOLDER_PICK", "Saved URI: $uri")
    }

    val vizImagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) {
            Log.e("VIZ_IMAGE", "User cancelled")
            return@rememberLauncherForActivityResult
        }

        try {
            context.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (e: Exception) {
            Log.e("VIZ_IMAGE", "Permission failed", e)
        }

        val name = VideoUtils.getFileName(context, uri).lowercase()
        Log.e("VIZ_IMAGE", "Picked: $name ($uri)")

        val cacheUri = try {
            val cacheFile = java.io.File(
                context.cacheDir,
                "viz_${System.currentTimeMillis()}_${uri.hashCode()}.jpg"
            )
            context.contentResolver.openInputStream(uri)?.use { input ->
                cacheFile.outputStream().use { out -> input.copyTo(out) }
            }
            Uri.fromFile(cacheFile)
        } catch (e: Exception) {
            Log.e("VIZ_IMAGE", "Cache copy failed, using original", e)
            uri
        }

        pendingVizImageUri = cacheUri.toString()
        Log.e("VIZ_IMAGE", "Final URI: $cacheUri")
    }

    // ═══════════════════════════════════════════════════════════
    //  MULTI-IMPORT PROCESSING
    // ═══════════════════════════════════════════════════════════

    LaunchedEffect(pendingUris) {
        val uris = pendingUris
        if (uris.isEmpty()) return@LaunchedEffect
        try {
            val results = withContext(Dispatchers.IO) {
                uris.mapNotNull { uri ->
                    try {
                        val name = VideoUtils.getFileName(context, uri)
                        var mime = VideoUtils.getMimeType(context, uri)
                        val nameLower = name.lowercase()
                        val isJfifVariant = nameLower.endsWith(".jfif") ||
                                nameLower.endsWith(".jif") ||
                                nameLower.endsWith(".jfi")
                        if (isJfifVariant) mime = "image/jpeg"
                        val isImage = mime.startsWith("image/")
                        val finalUri: Uri = if (isJfifVariant) {
                            try {
                                val cacheFile = java.io.File(
                                    context.cacheDir,
                                    "img_${System.currentTimeMillis()}_" +
                                            "${uri.hashCode()}.jpg"
                                )
                                context.contentResolver
                                    .openInputStream(uri)?.use { input ->
                                        cacheFile.outputStream()
                                            .use { output -> input.copyTo(output) }
                                    }
                                Uri.fromFile(cacheFile)
                            } catch (e: Exception) {
                                uri
                            }
                        } else uri

                        val dur = if (isImage) 5000L
                        else VideoUtils.getVideoDuration(context, uri)

                        try {
                            context.contentResolver.takePersistableUriPermission(
                                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                            )
                        } catch (_: Exception) {
                        }

                        MediaInfo(name, dur, mime, finalUri)
                    } catch (e: Exception) {
                        Log.e("IMPORT", "Failed to import uri: $uri", e)
                        null
                    }
                }
            }

            results.forEach { info ->
                viewModel.addClipSmart(
                    uri = info.finalUri,
                    name = info.name,
                    durationMs = info.durationMs,
                    mediaType = info.mimeType
                )
            }
        } catch (e: Exception) {
            Log.e("IMPORT", "Multi-import failed", e)
        } finally {
            pendingUris = emptyList()
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  PROMPT RUNNER
    // ═══════════════════════════════════════════════════════════

    fun runPrompt(input: String) {
        try {
            val parsed = PromptEngine.parse(input)
            val report = PromptExecutor.execute(parsed, viewModel)
            promptFeedbackType = report.statusType
            val sb = StringBuilder()
            if (report.successCount > 0) {
                sb.append("Applied ${report.successCount}:\n")
                sb.append(report.applied.joinToString("\n") { "  - $it" })
            }
            if (report.unknownCount > 0) {
                if (sb.isNotEmpty()) sb.append("\n\n")
                sb.append("Unknown (${report.unknownCount}):\n")
                sb.append(report.unknown.joinToString("\n") { "  - $it" })
            }
            if (report.errorCount > 0) {
                if (sb.isNotEmpty()) sb.append("\n\n")
                sb.append("Errors (${report.errorCount}):\n")
                sb.append(report.errors.joinToString("\n") { "  - $it" })
            }
            if (sb.isEmpty()) sb.append("Nothing to apply")
            promptFeedback = sb.toString()
        } catch (e: Exception) {
            promptFeedback = "Error: ${e.message}"
            promptFeedbackType = "error"
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  VISUALIZER CREATION WITH BEAT DETECTION
    // ═══════════════════════════════════════════════════════════

    fun launchVisualizerCreation() {
        isDetectingBeats = true
        beatDetectionProgress = 0f
        beatDetectionError = null

        kotlinx.coroutines.CoroutineScope(Dispatchers.Main).launch {
            try {
                val success = viewModel.createVisualizerClipWithBeats(context)
                isDetectingBeats = false
                if (!success) {
                    beatDetectionError = "Could not create visualizer"
                } else {
                    beatDetectionError = null
                    activePanel = null
                }
            } catch (e: Throwable) {
                isDetectingBeats = false
                beatDetectionError = "Failed: ${e.message}"
                Log.e("VISUALIZER", "Create failed", e)
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  MAIN LAYOUT
    // ═══════════════════════════════════════════════════════════

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
    ) {

        // ─── TOP BAR ───
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF0A0A0A))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, "Back", tint = Color.White)
                }
                Text(
                    "Editor",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .height(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF7C3AED))
                        .pointerInput(Unit) {
                            detectTapGestures { activePanel = "export" }
                        }
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text("💾", fontSize = 13.sp)
                        Text(
                            "Export",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // ─── PREVIEW ───
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color(0xFF0A0A0A))
        ) {
            PreviewCanvas(
                exoPlayer = exoPlayer,
                hasVideo = state.clips.any { it.isVisualClip },
                rotation = state.rotation,
                aspectMode = state.aspectMode,
                clips = state.clips,
                currentPosMs = state.currentPosMs,
                isPlaying = isPlaybackActive,
                hiddenVisualTracks = state.hiddenVisualTracks,
                aspectRatioKey = state.aspectRatio,
                selectedClipId = state.selectedClipId,
                multiSelectedIds = state.multiSelectedIds,
                previewFilters = state.previewFilters,
                previewEffectState = state.previewEffectState,
                isDrawingMode = isDrawingMode,
                activeBrushType = brushType,
                activeBrushColor = brushColor,
                activeBrushWidth = brushWidth,
                activeBrushOpacity = brushOpacity,
                isMaskPenMode = isMaskPenMode,
                onBrushStrokeComplete = { stroke ->
                    val targetBrush = state.selectedClip?.takeIf { it.isBrushClip }
                        ?: state.clips.lastOrNull { it.isBrushClip }
                    val strokeWithGradient = stroke.copy(gradient = brushGradient)
                    viewModel.addStrokeToBrushClip(targetBrush?.id, strokeWithGradient)
                },
                onMaskPointAdd = { x, y -> viewModel.addMaskPoint(x, y) },
                onMaskAnchorMove = { index, x, y ->
                    viewModel.moveMaskAnchor(index, x, y)
                },
                onMaskHandleMove = { index, isIn, dx, dy ->
                    viewModel.moveMaskHandle(index, isIn, dx, dy)
                },
                onMaskPointToggle = { index -> viewModel.toggleMaskPointSmooth(index) },
                onMaskPointDelete = { index -> viewModel.deleteMaskPoint(index) },
                onClosePath = { viewModel.setMaskClosed(true) },
                onClipSelected = { clipId ->
                    val clip = state.clips.firstOrNull { it.id == clipId }
                    if (clip != null) viewModel.selectClip(clip)
                },
                onDeleteLayer = { clipId ->
                    state.clips.firstOrNull { it.id == clipId }?.let { clip ->
                        viewModel.selectClip(clip)
                        viewModel.deleteCurrentClip()
                    }
                },
                onGroupGestureStart = { viewModel.beginGroupGesture() },
                onGroupGestureEnd = { viewModel.endGroupGesture() },
                onGroupGesture = { clipId: String, x: Float, y: Float, scale: Float, rot: Float ->
                    viewModel.applyGroupTransform(clipId, x, y, scale, rot)
                },
                onTextPositionChanged = { clipId: String, x: Float, y: Float ->
                    viewModel.updateTextPositionDirect(clipId, x, y)
                },
                onTextTransformChanged = { clipId: String, s: Float, r: Float ->
                    viewModel.updateSelectedTransformBulk(clipId, s, r)
                },
                onStickerPositionChanged = { clipId: String, x: Float, y: Float ->
                    viewModel.updateSelectedPositionBulk(clipId, x, y)
                },
                onStickerTransformChanged = { clipId: String, s: Float, r: Float ->
                    viewModel.updateSelectedTransformBulk(clipId, s, r)
                },
                onBrushPositionChanged = { clipId: String, x: Float, y: Float ->
                    viewModel.updateSelectedPositionBulk(clipId, x, y)
                },
                onBrushTransformChanged = { clipId: String, s: Float, r: Float ->
                    viewModel.updateSelectedTransformBulk(clipId, s, r)
                },
                onVisualizerPositionChanged = { clipId: String, x: Float, y: Float ->
                    viewModel.updateSelectedPositionBulk(clipId, x, y)
                },
                onVisualizerTransformChanged = { clipId: String, s: Float, r: Float ->
                    viewModel.updateSelectedTransformBulk(clipId, s, r)
                }
            )
        }

        // ─── CONTROL BAR ───
        ControlBar(
            onMediaClick = {
                picker.launch(
                    arrayOf(
                        "video/*",
                        "image/*",
                        "audio/*",
                        "image/jpeg",
                        "image/jpg",
                        "application/octet-stream"
                    )
                )
            },
            onAddVisualLayer = { viewModel.addVisualLayer() },
            onAddAudioLayer = { viewModel.addAudioLayer() },
            currentRatio = state.aspectRatio,
            onRatioClick = { activePanel = "ratio" }
        )

        // ─── TIMELINE ───
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                val selectedForToolbar = state.selectedClip
                val linkGroupId = selectedForToolbar?.linkGroupId
                val multiCount = state.multiSelectedIds.size
                val canLink = multiCount >= 2 ||
                        (selectedForToolbar != null && linkGroupId == null)
                val isLinked = linkGroupId != null

                TimelineToolbar(
                    onSelectBackward = { viewModel.selectBackward() },
                    onSelectForward = { viewModel.selectForward() },
                    onSelectAll = { viewModel.selectAllClips() },
                    onSelectAllTrack = { viewModel.selectAllOnCurrentTrack() },
                    onDeselectAll = { viewModel.deselectAll() },
                    onMagnet = { viewModel.closeGapsFromPlayhead() },
                    onAddVisualLayer = { viewModel.addVisualLayer() },
                    onAddAudioLayer = { viewModel.addAudioLayer() },
                    onLink = {
                        val gid = viewModel.linkSelectedClips()
                        Log.e("LINK", "Linked result: $gid")
                    },
                    onUnlink = {
                        selectedForToolbar?.let {
                            viewModel.unlinkClip(it.id)
                            Log.e("LINK", "Unlinked: ${it.id}")
                        }
                    },
                    isLinked = isLinked,
                    canLink = canLink,
                    zoomSlider = state.timelineZoom,
                    totalSec = state.totalDurationMs / 1000f,
                    viewportContentWidthDp = 320f,
                    onZoomChange = { viewModel.setTimelineZoom(it) }
                )

                Timeline(
                    state = state,
                    onClipTapped = { clip ->
                        viewModel.selectClip(clip)
                        viewModel.clearMultiSelect()

                        if (clip.isFilterLayerClip) {
                            filterEditLayerId = clip.id
                            activePanel = "filters"
                        }

                        if (clip.isEffectClip) {
                            effectEditLayerId = clip.id
                            activePanel = "effects"
                        }

                        if (clip.isVisualizerClip) {
                            activePanel = "visualizer"
                        }
                    },
                    onTrackTapped = { ti, aud -> viewModel.selectTrack(ti, aud) },
                    onTrimLeft = { ns -> viewModel.trimClipLeft(ns) },
                    onTrimRight = { ne -> viewModel.trimClipRight(ne) },
                    onTrimCommit = { viewModel.commitTrim() },
                    onSeek = { t ->
                        isPlaybackActive = false
                        if (exoPlayer.isPlaying) exoPlayer.pause()
                        if (audioExoPlayer.isPlaying) audioExoPlayer.pause()
                        viewModel.setCurrentPos(t)
                        val sel = state.selectedClip
                        if (sel != null && sel.isVisualClip) {
                            val localMs = (t - sel.timelineStartMs)
                                .coerceIn(0L, sel.durationMs)
                            exoPlayer.seekTo(localMs)
                        } else {
                            exoPlayer.seekTo(t)
                        }
                    },
                    onMoveClip = { id, ti, aud, ms ->
                        viewModel.ensureLayerExists(ti, aud)
                        viewModel.moveClip(id, ti, aud, ms)
                    },
                    onDragStart = { viewModel.beginDrag() },
                    onDragEnd = { viewModel.commitDrag() },
                    onDragCancel = { viewModel.cancelDrag() },
                    onToggleVisualVisibility = { idx ->
                        viewModel.toggleVisualTrackVisibility(idx)
                    },
                    onToggleAudioMute = { idx ->
                        viewModel.toggleAudioTrackMute(idx)
                    },
                    onSwapTracks = { from, to, isAudio ->
                        viewModel.swapTracks(from, to, isAudio)
                    },
                    onTransitionDelete = { clipId ->
                        viewModel.removeTransitionFor(clipId)
                    },
                    onTransitionDurationChange = { clipId, ms ->
                        viewModel.setTransitionDuration(clipId, ms)
                    },
                    onTimelineZoomChange = { zoom ->
                        viewModel.setTimelineZoom(zoom)
                    }
                )
            }
        }

        // ─── PLAYBACK CONTROLS ───
        PlaybackControls(
            isPlaying = isPlaybackActive,
            isMuted = state.isMuted,
            currentPosMs = state.currentPosMs,
            totalDurationMs = state.totalDurationMs,
            hasVideo = state.clips.isNotEmpty(),
            canUndo = state.canUndo,
            canRedo = state.canRedo,
            hasKeyframeAtPlayhead = viewModel.hasKeyframeAtPlayhead(),
            onPlayPause = {
                if (isPlaybackActive) {
                    isPlaybackActive = false
                    exoPlayer.pause()
                    audioExoPlayer.pause()
                } else {
                    if (state.clips.isNotEmpty()) {
                        val totalDur = state.totalDurationMs
                        if (state.currentPosMs >= totalDur) {
                            viewModel.setCurrentPos(0L)
                        }
                        isPlaybackActive = true

                        val activeVisual = state.clips.firstOrNull {
                            it.isVisualClip &&
                                    state.currentPosMs >= it.timelineStartMs &&
                                    state.currentPosMs < it.timelineEndMs &&
                                    !state.hiddenVisualTracks.contains(it.trackIndex)
                        }
                        if (activeVisual != null) exoPlayer.play()

                        val activeAudio = state.clips.firstOrNull {
                            it.isAudio &&
                                    !it.isAudioEffectClip &&
                                    it.uri != Uri.EMPTY &&
                                    state.currentPosMs >= it.timelineStartMs &&
                                    state.currentPosMs < it.timelineEndMs &&
                                    !state.mutedAudioTracks.contains(it.trackIndex)
                        }
                        if (activeAudio != null) audioExoPlayer.play()
                    }
                }
            },
            onSplit = { viewModel.splitCurrentClip() },
            onDelete = {
                if (state.multiSelectedIds.isNotEmpty()) {
                    viewModel.deleteSelectedClips()
                } else {
                    viewModel.deleteCurrentClip()
                }
            },
            onDuplicate = { viewModel.duplicateCurrentClip() },
            onUndo = { viewModel.undo() },
            onRedo = { viewModel.redo() },
            onMuteToggle = { viewModel.toggleMute() },
            onKeyframe = { viewModel.toggleKeyframeAll() }
        )

        // ─── FEATURE PANEL OR SHELF ───
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 140.dp, max = 320.dp)
        ) {
            val selected = state.selectedClip

            when (activePanel) {
                null -> FeatureShelf(
                    onFeatureSelected = { key ->
                        if (key == "filters") filterEditLayerId = null
                        if (key == "effects") effectEditLayerId = null
                        if (key == "wheel") viewModel.prepareColorWheelLayer()
                        activePanel = key
                    },
                    scrollState = featureScrollState
                )

                // [PART 2 CONTINUES - panel dispatch continues here]
                "code" -> PromptPanel(
                    feedback = promptFeedback,
                    feedbackType = promptFeedbackType,
                    onApply = { input -> runPrompt(input) },
                    onClear = {
                        promptFeedback = ""
                        promptFeedbackType = "none"
                    },
                    onClose = { activePanel = null }
                )

                "trim" -> TrimPanel(
                    clipName = selected?.name ?: "",
                    clipStartMs = selected?.timelineStartMs ?: 0L,
                    clipEndMs = selected?.timelineEndMs ?: 0L,
                    playheadMs = state.currentPosMs,
                    sourceInMs = selected?.sourceStartMs ?: 0L,
                    sourceOutMs = selected?.sourceEndMs ?: 0L,
                    hasClipSelected = selected?.isVisualClip == true,
                    playheadInsideClip = selected?.let {
                        state.currentPosMs > it.timelineStartMs &&
                                state.currentPosMs < it.timelineEndMs
                    } ?: false,
                    onClose = { activePanel = null },
                    onTrimLeft = { viewModel.trimLeft() },
                    onTrimRight = { viewModel.trimRight() },
                    onSplit = { viewModel.splitCurrentClip() }
                )

                "speed" -> SpeedPanel(
                    clipName = selected?.name ?: "",
                    baseDurationMs = viewModel.getSelectedBaseDurationMs(),
                    currentSpeed = selected?.speed ?: 1.0f,
                    hasClipSelected = selected != null,
                    onSpeedChanged = { viewModel.setSpeed(it) },
                    onReset = { viewModel.resetSpeed() },
                    onClose = { activePanel = null }
                )

                "text" -> TextPanel(
                    currentText = viewModel.getSelectedTextState(),
                    hasTextClipSelected = selected?.isTextClip == true ||
                            state.multiSelectedIds.isNotEmpty(),
                    startInEdit = openTextEditor,
                    onTextChanged = { viewModel.updateSelectedText(it) },
                    onDraftTextChanged = { content ->
                        viewModel.updateSelectedText(
                            viewModel.getSelectedTextState().copy(content = content)
                        )
                    },
                    onCreateNew = { viewModel.createTextClip() },
                    onRemove = { viewModel.removeSelectedText() },
                    onApplyTemplate = { templateId ->
                        viewModel.applyTemplate(
                            templateId = templateId,
                            startMs = state.currentPosMs,
                            canvasWidthPx = 720f
                        )
                        activePanel = null
                    },
                    onClose = {
                        activePanel = null
                        openTextEditor = false
                    }
                )

                "animations" -> AnimationsPanel(
                    currentAnimation = selected?.textState?.animation ?: "none",
                    currentDuration = selected?.textState?.animationDuration ?: 0.6f,
                    hasTextClipSelected = selected?.isTextClip == true ||
                            state.multiSelectedIds.isNotEmpty(),
                    onAnimationSelected = { viewModel.setTextAnimation(it) },
                    onDurationChanged = {
                        val st = viewModel.getSelectedTextState()
                        viewModel.updateSelectedText(
                            st.copy(animationDuration = it)
                        )
                    },
                    onPreview = { exoPlayer.seekTo(0) },
                    onClose = { activePanel = null }
                )

                "filters" -> {
                    val editLayer = filterEditLayerId?.let { id ->
                        state.clips.firstOrNull {
                            it.id == id && it.isFilterLayerClip
                        }
                    }
                    FiltersPanel(
                        editLayerId = editLayer?.id,
                        initialFilters = editLayer?.filters,
                        previewClip = selected,
                        previewTimeMs = state.currentPosMs,
                        onPreviewFilters = { viewModel.setPreviewFilters(it) },
                        onApplyAsLayer = { filters ->
                            viewModel.createFilterLayer(filters)
                            filterEditLayerId = null
                        },
                        onUpdateLayer = { filters ->
                            editLayer?.let {
                                viewModel.updateFilterLayer(it.id, filters)
                            }
                        },
                        onDeleteLayer = {
                            editLayer?.let {
                                viewModel.removeFilterLayer(it.id)
                            }
                            filterEditLayerId = null
                            activePanel = null
                        },
                        onClose = {
                            viewModel.clearPreviewFilters()
                            filterEditLayerId = null
                            activePanel = null
                        }
                    )
                }

                "effects" -> {
                    val editLayer = effectEditLayerId?.let { id ->
                        state.clips.firstOrNull {
                            it.id == id && it.isEffectClip
                        }
                    }
                    EffectsPanel(
                        editLayerId = editLayer?.id,
                        initialEffectState = editLayer?.effectState,
                        initialPresetKey = editLayer?.effectKeys?.firstOrNull(),
                        onPreviewEffect = { key, intensity ->
                            viewModel.setPreviewEffect(key, intensity)
                        },
                        onApplyAsLayer = { key, intensity ->
                            viewModel.createEffectLayerAt(key, intensity)
                            effectEditLayerId = null
                        },
                        onUpdateIntensity = { intensity ->
                            editLayer?.let {
                                viewModel.updateEffectLayerIntensity(
                                    it.id, intensity
                                )
                            }
                        },
                        onDeleteLayer = {
                            editLayer?.let {
                                viewModel.removeEffectLayer(it.id)
                            }
                            effectEditLayerId = null
                            activePanel = null
                        },
                        onClose = {
                            viewModel.clearPreviewEffect()
                            effectEditLayerId = null
                            activePanel = null
                        }
                    )
                }

                "adjustments" -> AdjustmentsPanel(
                    adj = selected?.adjustments ?: AdjustmentData(),
                    onAdjChanged = { viewModel.updateSelectedAdjustment(it) },
                    onReset = { viewModel.resetAdjustments() },
                    onClose = { activePanel = null }
                )

                "wheel" -> ColorWheelPanel(
                    state = selected?.colorWheel ?: ColorWheelState(),
                    hasClipSelected = true,
                    onStateChanged = { newState ->
                        selected?.let { viewModel.updateColorWheelLayer(it.id, newState) }
                    },
                    onRemove = { viewModel.removeSelectedColorWheelLayer() },
                    onClose = { activePanel = null }
                )

                "stickers" -> StickersPanel(
                    current = viewModel.getSelectedStickerState(),
                    hasStickerSelected = selected?.isStickerClip == true,
                    playheadMs = state.currentPosMs,
                    onStickerChanged = { viewModel.updateSelectedSticker(it) },
                    onEmojiTapped = { viewModel.addOrUpdateSticker(it) },
                    onRemove = { viewModel.removeSelectedSticker() },
                    onClose = { activePanel = null }
                )

                "overlays" -> OverlaysPanel(
                    current = selected?.overlay ?: OverlayState(),
                    hasClipSelected = true,
                    onStateChanged = { viewModel.updateSelectedOverlay(it) },
                    onRemove = { viewModel.removeSelectedOverlay() },
                    onClose = { activePanel = null }
                )

                "transitions" -> {
                    val target = selected?.let { sel ->
                        val hasLeft = state.clips.any { other ->
                            other.id != sel.id &&
                                    other.isAudio == sel.isAudio &&
                                    other.trackIndex == sel.trackIndex &&
                                    abs(other.timelineEndMs - sel.timelineStartMs) < 100L
                        }
                        if (hasLeft) sel
                        else {
                            state.clips.filter { other ->
                                other.id != sel.id &&
                                        other.isAudio == sel.isAudio &&
                                        other.trackIndex == sel.trackIndex &&
                                        abs(
                                            other.timelineStartMs - sel.timelineEndMs
                                        ) < 100L
                            }.minByOrNull { it.timelineStartMs }
                        }
                    }

                    TransitionsPanel(
                        current = target?.transition ?: TransitionState(),
                        hasPairAvailable = target != null,
                        hintText = when {
                            selected == null -> "Pehle timeline pe ek clip select karo."
                            target == null -> "Is clip ke saath koi adjacent clip chahiye."
                            else -> "Transition lagao"
                        },
                        onTransitionChanged = { newState ->
                            target?.let {
                                viewModel.setTransitionForClip(it.id, newState)
                            }
                        },
                        onRemove = {
                            target?.let {
                                viewModel.setTransitionForClip(
                                    it.id, TransitionState()
                                )
                            }
                        },
                        onClose = { activePanel = null }
                    )
                }

                "chroma" -> ChromaKeyPanel(
                    state = selected?.chroma ?: ChromaState(),
                    hasClipSelected = true,
                    onStateChanged = { viewModel.updateSelectedChroma(it) },
                    onRemove = { viewModel.removeSelectedChroma() },
                    onClose = { activePanel = null }
                )

                "mask" -> MaskPanel(
                    current = selected?.mask ?: MaskState(),
                    hasClipSelected = selected != null,
                    hasKeyframeAtPlayhead = viewModel.hasMaskKeyframeAtPlayhead(),
                    isPenMode = isMaskPenMode,
                    onStateChanged = { viewModel.updateMask(it) },
                    onTypeSelected = { viewModel.setMaskType(it) },
                    onAddKeyframe = { viewModel.addMaskKeyframe() },
                    onClearKeyframes = { viewModel.clearMaskKeyframes() },
                    onPenToolToggle = { isMaskPenMode = !isMaskPenMode },
                    onUndoPoint = { viewModel.removeLastMaskPoint() },
                    onClearPoints = { viewModel.clearMaskPoints() },
                    onClosedToggle = {
                        val cur = selected?.mask ?: return@MaskPanel
                        viewModel.setMaskClosed(!cur.customClosed)
                    },
                    onRemove = { viewModel.removeMask() },
                    onClose = {
                        activePanel = null
                        isMaskPenMode = false
                    }
                )

                "brush" -> {
                    val brushClip = selected?.takeIf { it.isBrushClip }
                        ?: state.clips.lastOrNull { it.isBrushClip }
                    BrushPanel(
                        isDrawing = isDrawingMode,
                        activeType = brushType,
                        activeColor = brushColor,
                        activeWidth = brushWidth,
                        activeOpacity = brushOpacity,
                        activeGradient = brushGradient,
                        strokeCount = brushClip?.brush?.strokes?.size ?: 0,
                        hasBrushLayer = brushClip != null,
                        onToggleDrawing = { isDrawingMode = !isDrawingMode },
                        onTypeChanged = { brushType = it },
                        onColorChanged = { brushColor = it },
                        onWidthChanged = { brushWidth = it },
                        onOpacityChanged = { brushOpacity = it },
                        onGradientChanged = { brushGradient = it },
                        onUndoStroke = {
                            brushClip?.let {
                                viewModel.undoLastStrokeOnBrushClip(it.id)
                            }
                        },
                        onClearStrokes = {
                            brushClip?.let {
                                viewModel.clearBrushClipStrokes(it.id)
                            }
                        },
                        onCreateLayer = {
                            viewModel.createBrushClip()
                            isDrawingMode = true
                        },
                        onClose = {
                            activePanel = null
                            isDrawingMode = false
                        }
                    )
                }

                "crop" -> CropPanel(
                    cropL = selected?.cropL ?: 0f,
                    cropR = selected?.cropR ?: 0f,
                    cropT = selected?.cropT ?: 0f,
                    cropB = selected?.cropB ?: 0f,
                    hasClipSelected = selected?.isVisualClip == true ||
                            state.multiSelectedIds.isNotEmpty(),
                    onCropChanged = { l, r, t, b ->
                        viewModel.setCrop(l, r, t, b)
                    },
                    onAspectSelected = { key ->
                        val q = CropEngine.presetFor(key, 16f, 9f)
                        viewModel.setCrop(q.l, q.r, q.t, q.b)
                    },
                    onReset = { viewModel.setCrop(0f, 0f, 0f, 0f) },
                    onClose = { activePanel = null }
                )

                "transform" -> {
                    val hasAnySelected = selected != null ||
                            state.multiSelectedIds.isNotEmpty()
                    val currentTimeSec = selected?.let {
                        ((state.currentPosMs - it.timelineStartMs).toFloat() / 1000f)
                            .coerceAtLeast(0f)
                    } ?: 0f
                    val clipDurSec = selected?.let { it.durationMs / 1000f } ?: 5f
                    val base = selected?.let { TransformApplier.baseOf(it) }
                        ?: TransformValues()
                    TransformPanel(
                        clipName = if (state.multiSelectedIds.size > 1)
                            "${state.multiSelectedIds.size} clips"
                        else selected?.name ?: "",
                        hasClipSelected = hasAnySelected,
                        base = base,
                        keyframeMap = selected?.keyframes ?: emptyMap(),
                        currentTimeSec = currentTimeSec,
                        clipDurationSec = clipDurSec,
                        onPropertyChanged = { prop, value ->
                            viewModel.changeTransformProperty(prop, value)
                        },
                        onToggleKeyframe = { prop ->
                            viewModel.toggleKeyframeAtPlayhead(prop)
                        },
                        onSetEase = { ease -> viewModel.setEaseAtPlayhead(ease) },
                        onResetAll = { viewModel.resetAllSelectedTransforms() },
                        onUpdateKeyframe = { prop, oldT, newT, newV ->
                            viewModel.updateKeyframeInGraph(prop, oldT, newT, newV)
                        },
                        onDeleteKeyframe = { prop, t ->
                            viewModel.deleteKeyframeFromGraph(prop, t)
                        },
                        onClose = { activePanel = null }
                    )
                }

                "volume" -> VolumePanel(
                    volume = state.volume,
                    isMuted = state.isMuted,
                    hasClipSelected = true,
                    onVolumeChanged = { viewModel.setVolume(it) },
                    onMuteToggle = { viewModel.toggleMute() },
                    onClose = { activePanel = null }
                )

                "audiofx" -> AudioPanel(
                    onPreviewFx = { fx, intensity ->
                        AudioPreviewEngine.apply(
                            exoPlayer, audioExoPlayer, fx, intensity
                        )
                    },
                    onClearPreview = {
                        AudioPreviewEngine.release()
                        try {
                            exoPlayer.playbackParameters =
                                androidx.media3.common.PlaybackParameters(1f, 1f)
                            audioExoPlayer.playbackParameters =
                                androidx.media3.common.PlaybackParameters(1f, 1f)
                        } catch (_: Throwable) {
                        }
                    },
                    onApplyAudioFx = { fx, intensity ->
                        viewModel.createAudioFxLayer(fx, intensity)
                    },
                    onClose = { activePanel = null }
                )

                "soundfx" -> {
                    AudioPanel(
                        onPreviewFx = { fx, intensity ->
                            AudioPreviewEngine.apply(
                                exoPlayer, audioExoPlayer, fx, intensity
                            )
                        },
                        onClearPreview = {
                            AudioPreviewEngine.release()
                            try {
                                exoPlayer.playbackParameters =
                                    androidx.media3.common.PlaybackParameters(1f, 1f)
                                audioExoPlayer.playbackParameters =
                                    androidx.media3.common.PlaybackParameters(1f, 1f)
                            } catch (_: Throwable) {
                            }
                        },
                        onApplyAudioFx = { fx, intensity ->
                            viewModel.createAudioFxLayer(fx, intensity)
                        },
                        onClose = { activePanel = null }
                    )
                }

                "beats" -> BeatsPanel(
                    state = BeatsState(
                        detected = state.beatsDetected,
                        count = state.beatsCount,
                        filter = state.beatsFilter
                    ),
                    viewModel = viewModel,
                    onDetect = { },
                    onClear = { viewModel.clearBeats() },
                    onClose = { activePanel = null }
                )

                "visualizer" -> {
                    val selectedAudioId: String? = selected?.takeIf {
                        it.isAudio && !it.isAudioEffectClip
                    }?.id

                    val vizClip = selected?.takeIf { it.isVisualizerClip }
                        ?: state.clips.firstOrNull { clip ->
                            clip.isVisualizerClip &&
                                    selectedAudioId != null &&
                                    clip.visualizer?.linkedAudioClipId == selectedAudioId
                        }

                    when {
                        isDetectingBeats -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF1A1F3A))
                                    .padding(20.dp)
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text("🎵", fontSize = 36.sp)
                                    Text(
                                        "Detecting beats...",
                                        color = Color(0xFF60EFFF),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        "Analyzing linked audio. This may take a few seconds.",
                                        color = Color(0xFF888888),
                                        fontSize = 10.sp,
                                        textAlign = TextAlign.Center
                                    )
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(4.dp)
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(Color(0xFF0F0F0F))
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth(
                                                    beatDetectionProgress.coerceIn(0f, 1f)
                                                )
                                                .height(4.dp)
                                                .background(Color(0xFF60EFFF))
                                        )
                                    }

                                    Box(
                                        modifier = Modifier
                                            .height(36.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0xFF181818))
                                            .pointerInput(Unit) {
                                                detectTapGestures {
                                                    isDetectingBeats = false
                                                    beatDetectionError = null
                                                    activePanel = null
                                                }
                                            }
                                            .padding(horizontal = 20.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            "✕ Cancel",
                                            color = Color(0xFFAAAAAA),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }

                        beatDetectionError != null && vizClip == null -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF2A0F0F))
                                    .padding(16.dp)
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text("❌", fontSize = 32.sp)
                                    Text(
                                        beatDetectionError ?: "Error",
                                        color = Color(0xFFFF6B6B),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center
                                    )

                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .height(36.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(Color(0xFF181818))
                                                .pointerInput(Unit) {
                                                    detectTapGestures {
                                                        beatDetectionError = null
                                                        activePanel = null
                                                    }
                                                }
                                                .padding(horizontal = 20.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                "✕ Back",
                                                color = Color(0xFFAAAAAA),
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }

                                        Box(
                                            modifier = Modifier
                                                .height(36.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(Color(0xFF7C3AED))
                                                .pointerInput(Unit) {
                                                    detectTapGestures {
                                                        beatDetectionError = null
                                                    }
                                                }
                                                .padding(horizontal = 20.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                "↻ Retry",
                                                color = Color.White,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        vizClip == null -> {
                            val sel = selected
                            val selectedIsAudio = sel != null &&
                                    sel.isAudio && !sel.isAudioEffectClip

                            val anyVisualizerInTimeline = state.clips.any {
                                it.isVisualizerClip
                            }

                            if (!selectedIsAudio) {
                                if (anyVisualizerInTimeline) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(Color(0xFF1A1F3A))
                                            .padding(16.dp)
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Text("🎵", fontSize = 36.sp)
                                            Text(
                                                "Visualizer exists on timeline",
                                                color = Color.White,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                textAlign = TextAlign.Center
                                            )
                                            Text(
                                                "Tap it on the timeline to edit, " +
                                                        "or select an audio clip to create a new one.",
                                                color = Color(0xFF888888),
                                                fontSize = 10.sp,
                                                textAlign = TextAlign.Center
                                            )

                                            Box(
                                                modifier = Modifier
                                                    .height(40.dp)
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .background(Color(0xFF7C3AED))
                                                    .pointerInput(Unit) {
                                                        detectTapGestures {
                                                            activePanel = null
                                                        }
                                                    }
                                                    .padding(horizontal = 24.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    "✕ Back to Editor",
                                                    color = Color.White,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(Color(0xFF2A0F0F))
                                            .padding(16.dp)
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text("⚠️", fontSize = 32.sp)
                                            Text(
                                                "Select an audio clip first",
                                                color = Color(0xFFFF6B6B),
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                "Visualizer only reacts to its linked audio clip. " +
                                                        "Please select an audio clip on the timeline.",
                                                color = Color(0xFF888888),
                                                fontSize = 11.sp,
                                                textAlign = TextAlign.Center
                                            )

                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .height(36.dp)
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(Color(0xFF181818))
                                                        .pointerInput(Unit) {
                                                            detectTapGestures {
                                                                activePanel = null
                                                            }
                                                        }
                                                        .padding(horizontal = 20.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        "✕ Back",
                                                        color = Color(0xFFAAAAAA),
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }

                                                Box(
                                                    modifier = Modifier
                                                        .height(36.dp)
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(Color(0xFF7C3AED))
                                                        .pointerInput(Unit) {
                                                            detectTapGestures {
                                                                activePanel = null
                                                            }
                                                        }
                                                        .padding(horizontal = 20.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        "✓ OK",
                                                        color = Color.White,
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color(0xFF1A1F3A))
                                        .padding(16.dp)
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Text("🎵", fontSize = 36.sp)
                                        Text(
                                            "Create Visualizer for \"${sel.name.take(20)}\"",
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            textAlign = TextAlign.Center
                                        )
                                        Text(
                                            "Beats will be detected from this audio.",
                                            color = Color(0xFF888888),
                                            fontSize = 10.sp,
                                            textAlign = TextAlign.Center
                                        )

                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .height(44.dp)
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .background(Color(0xFF181818))
                                                    .pointerInput(Unit) {
                                                        detectTapGestures {
                                                            activePanel = null
                                                        }
                                                    }
                                                    .padding(horizontal = 16.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    "✕ Cancel",
                                                    color = Color(0xFFAAAAAA),
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }

                                            Box(
                                                modifier = Modifier
                                                    .height(44.dp)
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .background(Color(0xFF7C3AED))
                                                    .pointerInput(sel.id) {
                                                        detectTapGestures {
                                                            launchVisualizerCreation()
                                                        }
                                                    }
                                                    .padding(horizontal = 24.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    "✨ Create Visualizer",
                                                    color = Color.White,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        else -> {
                            LaunchedEffect(pendingVizImageUri) {
                                val uri = pendingVizImageUri
                                    ?: return@LaunchedEffect
                                viewModel.updateVisualizerLayer(
                                    vizClip.id,
                                    (vizClip.visualizer ?: VisualizerState()).copy(
                                        imageUri = uri,
                                        showImage = true
                                    )
                                )
                                pendingVizImageUri = null
                            }

                            val hasAudio = state.clips.any {
                                it.isAudio && !it.isAudioEffectClip
                            }

                            VisualizerPanel(
                                current = vizClip.visualizer ?: VisualizerState(),
                                hasAudio = hasAudio,
                                onStateChanged = {
                                    viewModel.updateVisualizerLayer(vizClip.id, it)
                                },
                                onPickImage = {
                                    vizImagePicker.launch(
                                        arrayOf(
                                            "image/*",
                                            "image/jpeg",
                                            "image/jpg",
                                            "image/pjpeg",
                                            "image/webp",
                                            "image/gif",
                                            "image/bmp",
                                            "image/heic",
                                            "image/heif",
                                            "application/octet-stream",
                                            "*/*"
                                        )
                                    )
                                },
                                onClearImage = {
                                    viewModel.updateVisualizerLayer(
                                        vizClip.id,
                                        (vizClip.visualizer ?: VisualizerState()).copy(
                                            imageUri = null,
                                            showImage = false
                                        )
                                    )
                                },
                                onRemove = {
                                    viewModel.removeVisualizerLayer(vizClip.id)
                                    activePanel = null
                                },
                                onClose = { activePanel = null }
                            )
                        }
                    }
                }

                "ratio" -> AspectRatioPanel(
                    currentRatio = state.aspectRatio,
                    onRatioSelected = { key ->
                        viewModel.updateRatio(RatioLibrary.find(key))
                    },
                    onClose = { activePanel = null }
                )

                "freeze" -> FreezePanel(
                    hasClipSelected = selected?.isVisualClip == true,
                    onFreeze = { dur -> viewModel.addFreezeFrame(dur) },
                    onClose = { activePanel = null }
                )

                "music" -> MusicPanel(onClose = { activePanel = null })

                "motion" -> MotionPanel(
                    current = "none",
                    onSelected = { },
                    onClose = { activePanel = null }
                )

                "duplicate" -> {
                    viewModel.duplicateCurrentClip()
                    activePanel = null
                }

                "delete" -> {
                    if (state.multiSelectedIds.isNotEmpty()) {
                        viewModel.deleteSelectedClips()
                    } else {
                        viewModel.deleteCurrentClip()
                    }
                    activePanel = null
                }

                else -> FeatureShelf(
                    onFeatureSelected = {
                        if (it == "wheel") viewModel.prepareColorWheelLayer()
                        activePanel = it
                    },
                    scrollState = featureScrollState
                )
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  EXPORT CONFIG DIALOG
    // ═══════════════════════════════════════════════════════════

    if (activePanel == "export") {
        ExportDialog(
            isExporting = exportState.isExporting,
            exportProgress = exportState.progress,
            exportMessage = exportState.message,
            timelineDurationMs = state.totalDurationMs,
            aspectRatio = state.aspectRatio,
            startMs = exportStartMs,
            endMs = if (exportEndMs > 0L) exportEndMs else state.totalDurationMs,
            useCustomRange = useCustomRange,
            onUseCustomRangeChange = { useCustomRange = it },
            onStartChange = { exportStartMs = it },
            onEndChange = { exportEndMs = it },
            currentFolderUri = state.exportFolderUri,
            onChooseFolder = { folderPicker.launch(null) },
            onResetFolder = { viewModel.setExportFolderUri(null) },
            onStartExport = { config ->
                val clipsForExport = state.clips.filter {
                    !state.hiddenVisualTracks.contains(it.trackIndex)
                }
                val startMs = if (config.useCustomRange) config.startMs else 0L
                val endMs = if (config.useCustomRange) config.endMs
                else state.totalDurationMs

                val bundle = ExportUiStateBundle(
                    fileName = config.fileName,
                    mode = config.mode,
                    resolution = config.resolution,
                    fps = config.fps,
                    bitrateKbps = config.bitrateKbps,
                    videoFormat = config.videoFormat,
                    audioFormat = config.audioFormat,
                    audioBitrateKbps = config.audioBitrateKbps,
                    imageFormat = config.imageFormat,
                    jpegQuality = config.jpegQuality,
                    aspectRatio = config.aspectRatio,
                    outputWidth = config.outputWidth,
                    outputHeight = config.outputHeight,
                    folderUri = config.customFolderUri,
                    startMs = startMs,
                    endMs = endMs,
                    totalDurationMs = state.totalDurationMs,
                    clips = clipsForExport
                )

                viewModel.setExportResolution(config.resolution)
                viewModel.setExportFps(config.fps)
                viewModel.setExportBitrate(config.bitrateKbps)
                viewModel.setExportFormat(config.videoFormat)

                viewModel.startExport(context, bundle)
                activePanel = null
            },
            onCancelExport = { showCancelConfirm = true },
            onDismiss = {
                if (!exportState.isExporting) {
                    activePanel = null
                }
            }
        )
    }

    // ═══════════════════════════════════════════════════════════
    //  EXPORT OVERLAY (fullscreen)
    // ═══════════════════════════════════════════════════════════

    if (exportState.isExporting ||
        exportState.isCompleted ||
        exportState.isCancelled ||
        exportState.errorMessage != null
    ) {
        ExportOverlay(
            state = exportState,
            onCancel = {
                viewModel.cancelExport(context)
            },
            onDismiss = {
                viewModel.clearExportState()
            }
        )
    }

    // ═══════════════════════════════════════════════════════════
    //  CANCEL CONFIRMATION DIALOG
    // ═══════════════════════════════════════════════════════════

    if (showCancelConfirm) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showCancelConfirm = false },
            title = {
                Text(
                    "Cancel Export?",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    "Do you really want to cancel this export?",
                    color = Color(0xFFAAAAAA),
                    fontSize = 12.sp
                )
            },
            confirmButton = {
                Box(
                    modifier = Modifier
                        .height(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFFF3B3B).copy(alpha = 0.2f))
                        .pointerInput(Unit) {
                            detectTapGestures {
                                viewModel.cancelExport(context)
                                showCancelConfirm = false
                            }
                        }
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Yes, Cancel",
                        color = Color(0xFFFF3B3B),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                Box(
                    modifier = Modifier
                        .height(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF181818))
                        .pointerInput(Unit) {
                            detectTapGestures { showCancelConfirm = false }
                        }
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No, Continue",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            containerColor = Color(0xFF1A1A1A),
            shape = RoundedCornerShape(16.dp)
        )
    }
}