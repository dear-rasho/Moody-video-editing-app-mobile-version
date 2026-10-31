@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.moody.moodyvideoeditor.ui.screens

import com.moody.moodyvideoeditor.utils.CaptionGenerator
import com.moody.moodyvideoeditor.utils.ManualCaptionParser
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.ui.platform.LocalConfiguration
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
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.moody.moodyvideoeditor.data.AdjustmentData
import com.moody.moodyvideoeditor.data.BeatsState

import com.moody.moodyvideoeditor.data.ChromaKeyState
import com.moody.moodyvideoeditor.data.ColorWheelState
import com.moody.moodyvideoeditor.data.MaskState
import com.moody.moodyvideoeditor.data.OverlayState
import com.moody.moodyvideoeditor.data.RecentMediaItem
import com.moody.moodyvideoeditor.data.RecentMediaRepository
import com.moody.moodyvideoeditor.data.RatioLibrary
import com.moody.moodyvideoeditor.data.TextState
import com.moody.moodyvideoeditor.data.SpotlightState
import com.moody.moodyvideoeditor.data.TransitionState
import com.moody.moodyvideoeditor.data.VisualizerState
import com.moody.moodyvideoeditor.ui.components.ControlBar
import com.moody.moodyvideoeditor.ui.components.EyedropperOverlay
import com.moody.moodyvideoeditor.ui.components.FeatureShelf
import com.moody.moodyvideoeditor.ui.components.PlaybackControls
import com.moody.moodyvideoeditor.ui.components.PreviewCanvas
import com.moody.moodyvideoeditor.ui.components.Timeline
import com.moody.moodyvideoeditor.ui.components.TimelineToolbar
import com.moody.moodyvideoeditor.ui.features.AdjustmentsPanel
import com.moody.moodyvideoeditor.ui.features.AnimationsPanel
import com.moody.moodyvideoeditor.ui.features.AspectRatioPanel
import com.moody.moodyvideoeditor.ui.features.AudioPanel
import com.moody.moodyvideoeditor.ui.features.AutoCaptionsPanel
import com.moody.moodyvideoeditor.ui.features.BeatsPanel
import com.moody.moodyvideoeditor.ui.features.BlendModePanel

import com.moody.moodyvideoeditor.ui.features.ColorMattePanel
import com.moody.moodyvideoeditor.ui.features.TrackMattePanel
import com.moody.moodyvideoeditor.ui.features.ColorRemoverPanel
import com.moody.moodyvideoeditor.ui.features.ColorWheelPanel
import com.moody.moodyvideoeditor.ui.features.CropPanel
import com.moody.moodyvideoeditor.ui.features.EffectsPanel
import com.moody.moodyvideoeditor.ui.features.ExportDialog
import com.moody.moodyvideoeditor.ui.features.ExportOverlay
import com.moody.moodyvideoeditor.ui.features.FiltersPanel
import com.moody.moodyvideoeditor.ui.features.FreezePanel
import com.moody.moodyvideoeditor.ui.features.KeyframeEasingPanel
import com.moody.moodyvideoeditor.ui.features.LayerShadowPanel
import com.moody.moodyvideoeditor.ui.features.ManualCaptionsPanel
import com.moody.moodyvideoeditor.ui.features.MaskPanel

import com.moody.moodyvideoeditor.ui.features.OverlaysPanel
import com.moody.moodyvideoeditor.ui.features.OpacityPanel
import com.moody.moodyvideoeditor.ui.features.RecentMediaPickerPanel
import com.moody.moodyvideoeditor.ui.features.PromptPanel
import com.moody.moodyvideoeditor.ui.features.SpeedPanel
import com.moody.moodyvideoeditor.ui.features.SoundFxPanel
import com.moody.moodyvideoeditor.ui.features.SpotlightPanel
import com.moody.moodyvideoeditor.ui.features.TextPanel
import com.moody.moodyvideoeditor.ui.features.TransformPanel
import com.moody.moodyvideoeditor.ui.features.TransitionsPanel
import com.moody.moodyvideoeditor.ui.features.TrimPanel
import com.moody.moodyvideoeditor.ui.features.VisualizerPanel
import com.moody.moodyvideoeditor.ui.features.VolumePanel
import com.moody.moodyvideoeditor.utils.AudioPreviewEngine
import com.moody.moodyvideoeditor.utils.CropEngine
import com.moody.moodyvideoeditor.utils.KeyframeStore
import com.moody.moodyvideoeditor.utils.PromptEngine
import com.moody.moodyvideoeditor.utils.PromptExecutor
import com.moody.moodyvideoeditor.utils.SettingsConsumer
import com.moody.moodyvideoeditor.utils.SpeedEngine
import com.moody.moodyvideoeditor.utils.TransformApplier
import com.moody.moodyvideoeditor.utils.TransformValues
import com.moody.moodyvideoeditor.utils.VideoUtils
import com.moody.moodyvideoeditor.utils.VolumeEngine
import com.moody.moodyvideoeditor.utils.VisualizerEngine
import com.moody.moodyvideoeditor.utils.WhisperModelManager
import com.moody.moodyvideoeditor.viewmodel.EditorViewModel
import com.moody.moodyvideoeditor.viewmodel.ExportUiStateBundle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import com.moody.moodyvideoeditor.data.EditorClip
private data class MediaInfo(
    val name: String,
    val durationMs: Long,
    val mimeType: String,
    val finalUri: Uri,
    val hasAudioTrack: Boolean,
    val originalUri: Uri
)

@Composable
fun EditorScreen(
    projectId: String = "",
    onBack: () -> Unit,
    startInCodeMode: Boolean = false,
    initialSavedTemplateId: String? = null,
    viewModel: EditorViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val exportState by viewModel.exportState.collectAsState()

    // ═══════════════════════════════════════════════════════════
    //  PROJECT LOAD + AUTO-SAVE
    // ═══════════════════════════════════════════════════════════

    LaunchedEffect(projectId, initialSavedTemplateId) {
        if (projectId.isNotBlank() && viewModel.getProjectId() != projectId) {
            val loaded = viewModel.loadProject(context, projectId)

            if (!loaded) {
                viewModel.setProjectMeta(
                    projectId,
                    if (startInCodeMode) "Code Project" else "New Project"
                )
            }
        }

        if (!initialSavedTemplateId.isNullOrBlank()) {
            val loaded = viewModel.loadFromTemplate(context, initialSavedTemplateId)
            if (loaded) {
                Log.e("EditorScreen", "✅ Loaded saved template")
            } else {
                Log.e("EditorScreen", "❌ Failed to load template")
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

    val settings by SettingsConsumer.current.collectAsState()

    LaunchedEffect(
        projectId,
        settings.autoSaveEnabled,
        settings.autoSaveIntervalSec
    ) {
        if (projectId.isNotBlank() && settings.autoSaveEnabled) {
            while (true) {
                delay(settings.autoSaveIntervalSec.coerceAtLeast(1) * 1000L)
                if (!viewModel.saveCurrentProject(context)) {
                    Log.e("EditorScreen", "Project auto-save failed for $projectId")
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  PANEL STATE
    // ═══════════════════════════════════════════════════════════

    var activePanel by remember {
        mutableStateOf<String?>(if (startInCodeMode) "code" else null)
    }
    var showRecentMediaPicker by remember { mutableStateOf(false) }
    var recentMedia by remember(projectId) {
        mutableStateOf(RecentMediaRepository.getRecent(context, projectId))
    }
    var openTextEditor by remember { mutableStateOf(false) }
    var lastLayerTap by remember { mutableStateOf<Pair<String, Long>?>(null) }

    val featureScrollState = androidx.compose.foundation.rememberScrollState()

    var filterEditLayerId by remember { mutableStateOf<String?>(null) }
    var effectEditLayerId by remember { mutableStateOf<String?>(null) }
    var matteEditLayerId by remember { mutableStateOf<String?>(null) }
    var spotlightEditLayerId by remember { mutableStateOf<String?>(null) }

    fun openLayerPanel(clip: EditorClip) {
        viewModel.selectClip(clip)
        openTextEditor = false
        when {
            clip.isTextClip -> activePanel = "text"
            clip.isFilterLayerClip -> {
                filterEditLayerId = clip.id
                activePanel = "filters"
            }
            clip.isColorMatteClip -> {
                matteEditLayerId = clip.id
                activePanel = "matte"
            }
            clip.isSpotlightClip -> {
                spotlightEditLayerId = clip.id
                activePanel = "spotlight"
            }
            clip.isEffectClip -> {
                effectEditLayerId = clip.id
                activePanel = "effects"
            }
            clip.isVisualizerClip -> activePanel = "visualizer"
            clip.type.startsWith("video/") &&
                    clip.linkedId != null &&
                    state.clips.any {
                        it.isAudio && !it.isAudioEffectClip &&
                                it.linkedId == clip.linkedId
                    } -> activePanel = "visualizer"
            clip.isAudioEffectClip -> {
                activePanel = if (clip.isSoundFxClip) "soundfx" else "audiofx"
            }
            else -> activePanel = null
        }
    }

    fun handleLayerTap(clip: EditorClip) {
        val now = android.os.SystemClock.uptimeMillis()
        val previousTap = lastLayerTap
        val isDoubleTap = previousTap?.first == clip.id &&
                now - previousTap.second <= 350L
        lastLayerTap = if (isDoubleTap) null else clip.id to now
        viewModel.selectClip(clip)
        if (isDoubleTap) openLayerPanel(clip)
    }

    var isMaskPenMode by remember { mutableStateOf(false) }
    var isMaskHandMode by remember { mutableStateOf(false) }

    var pendingUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var pendingVizImageUri by remember { mutableStateOf<String?>(null) }
    var pendingVizImageIsBackground by remember { mutableStateOf(false) }

    var isDetectingBeats by remember { mutableStateOf(false) }
    var beatDetectionProgress by remember { mutableFloatStateOf(0f) }
    var beatDetectionError by remember { mutableStateOf<String?>(null) }

    var exportStartMs by remember { mutableLongStateOf(0L) }
    var exportEndMs by remember { mutableLongStateOf(0L) }
    var useCustomRange by remember { mutableStateOf(false) }
    var showCancelConfirm by remember { mutableStateOf(false) }

    var promptFeedback by remember { mutableStateOf("") }
    var promptFeedbackType by remember { mutableStateOf("none") }

    var isPlaybackActive by remember { mutableStateOf(false) }

    // 📝 Caption state
    var captionProgress by remember { mutableFloatStateOf(0f) }
    var captionStatus by remember { mutableStateOf("") }
    var isGeneratingCaptions by remember { mutableStateOf(false) }
    var captionJob by remember { mutableStateOf<Job?>(null) }

    var generatedCaptions by remember {
        mutableStateOf<List<com.moody.moodyvideoeditor.utils.CaptionSegment>>(emptyList())
    }
    var detectedCaptionLanguage by remember { mutableStateOf("") }

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

    DisposableEffect(Unit) {
        onDispose {
            VisualizerEngine.clearCache()
        }
    }

    LaunchedEffect(activePanel) {
        if (activePanel != "filters") {
            viewModel.clearPreviewFilters()
        }
        if (activePanel != "effects") {
            viewModel.clearPreviewEffect()
        }

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

    val activeVideoClip = remember(state.clips, state.currentPosMs, state.hiddenVisualTracks) {
        state.clips
            .filter {
                it.isVisualClip &&
                        !it.type.startsWith("image/") &&
                        state.currentPosMs >= it.timelineStartMs &&
                        state.currentPosMs < it.timelineEndMs &&
                        !state.hiddenVisualTracks.contains(it.trackIndex)
            }
            .maxByOrNull { it.trackIndex }
    }
    val activeVideoHasSeparateAudio = remember(
        activeVideoClip?.linkedId,
        state.clips,
        state.currentPosMs
    ) {
        val linkedId = activeVideoClip?.linkedId
        linkedId != null && state.clips.any { clip ->
            clip.isAudio &&
                    !clip.isAudioEffectClip &&
                    clip.linkedId == linkedId &&
                    state.currentPosMs >= clip.timelineStartMs &&
                    state.currentPosMs < clip.timelineEndMs
        }
    }
    val activeAudioClip = remember(state.clips, state.currentPosMs, state.mutedAudioTracks) {
        state.clips
            .filter {
                it.isAudio &&
                        !it.isAudioEffectClip &&
                        it.uri.toString().isNotBlank() &&
                        it.uri != Uri.EMPTY &&
                        state.currentPosMs >= it.timelineStartMs &&
                        state.currentPosMs < it.timelineEndMs &&
                        !state.mutedAudioTracks.contains(it.trackIndex)
            }
            .maxByOrNull { it.trackIndex }
    }

    val videoPlaybackVolume = when {
        state.isMuted -> 0f
        activeClipTrackMuted -> 0f
        anyActiveAudioMuted -> 0f
        activeVideoHasSeparateAudio -> 0f
        else -> activeVideoClip?.volume ?: state.volume
    }
    val audioPlaybackVolume = if (state.isMuted) 0f else activeAudioClip?.volume ?: 0f
    val latestVideoPlaybackVolume by rememberUpdatedState(videoPlaybackVolume)
    val latestAudioPlaybackVolume by rememberUpdatedState(audioPlaybackVolume)

    LaunchedEffect(
        state.volume,
        state.isMuted,
        activeClipTrackMuted,
        anyActiveAudioMuted,
        activeVideoClip?.id,
        activeVideoClip?.volume,
        activeVideoHasSeparateAudio
    ) {
        VolumeEngine.applyToPlayer(exoPlayer, videoPlaybackVolume)
    }

    LaunchedEffect(state.isMuted, activeAudioClip?.id, activeAudioClip?.volume) {
        VolumeEngine.applyToPlayer(audioExoPlayer, audioPlaybackVolume)
    }

    DisposableEffect(exoPlayer, audioExoPlayer) {
        val videoListener = object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                VolumeEngine.applyToPlayer(exoPlayer, latestVideoPlaybackVolume)
            }
        }
        val audioListener = object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                VolumeEngine.applyToPlayer(audioExoPlayer, latestAudioPlaybackVolume)
            }
        }
        exoPlayer.addListener(videoListener)
        audioExoPlayer.addListener(audioListener)
        onDispose {
            exoPlayer.removeListener(videoListener)
            audioExoPlayer.removeListener(audioListener)
            VolumeEngine.releasePlayer(exoPlayer)
            VolumeEngine.releasePlayer(audioExoPlayer)
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  VIDEO PLAYBACK SYNC
    // ═══════════════════════════════════════════════════════════

    LaunchedEffect(
        state.currentPosMs,
        state.clips,
        state.hiddenVisualTracks,
        isPlaybackActive
    ) {
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
            exoPlayer.pause()
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

        if (isPlaybackActive) {
            if (!exoPlayer.isPlaying) exoPlayer.play()
        } else {
            exoPlayer.pause()
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  AUDIO PLAYBACK SYNC
    // ═══════════════════════════════════════════════════════════

    LaunchedEffect(
        state.currentPosMs,
        state.clips,
        state.mutedAudioTracks,
        isPlaybackActive
    ) {
        val playheadMs = state.currentPosMs
        val activeAudio = activeAudioClip

        if (activeAudio == null) {
            audioExoPlayer.pause()
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

        val targetSpeed = SpeedEngine.clampForExoPlayer(activeAudio.speed)
        val currentSpeed = audioExoPlayer.playbackParameters.speed
        if (abs(currentSpeed - targetSpeed) > 0.01f) {
            try {
                audioExoPlayer.setPlaybackSpeed(targetSpeed)
            } catch (_: Exception) {
            }
        }

        if (isPlaybackActive) {
            if (!audioExoPlayer.isPlaying) audioExoPlayer.play()
        } else {
            audioExoPlayer.pause()
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
            delay(40)
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
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (e: Exception) {
            Log.e("FOLDER_PICK", "Permission failed: ${e.message}", e)
        }
        viewModel.setExportFolderUri(uri.toString())
    }

    val vizImagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) {
            return@rememberLauncherForActivityResult
        }
        try {
            context.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: Exception) {
        }

        val cacheUri = try {
            val cacheFile = java.io.File(
                context.cacheDir,
                "viz_${System.currentTimeMillis()}_${uri.hashCode()}.jpg"
            )
            context.contentResolver.openInputStream(uri)?.use { input ->
                cacheFile.outputStream().use { out -> input.copyTo(out) }
            }
            Uri.fromFile(cacheFile)
        } catch (_: Exception) {
            uri
        }

        pendingVizImageUri = cacheUri.toString()
    }

    val textTexturePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val selectedText = viewModel.state.value.selectedClip
            ?.takeIf { it.isTextClip } ?: return@rememberLauncherForActivityResult
        val text = selectedText.textState ?: return@rememberLauncherForActivityResult
        val mimeType = context.contentResolver.getType(uri)
            ?: when (uri.lastPathSegment?.substringAfterLast('.', "")?.lowercase()) {
                "jpg", "jpeg" -> "image/jpeg"
                "png" -> "image/png"
                "webp" -> "image/webp"
                "gif" -> "image/gif"
                "mp4" -> "video/mp4"
                "mov" -> "video/quicktime"
                "mkv" -> "video/x-matroska"
                else -> null
            }
        if (mimeType?.startsWith("image/") != true &&
            mimeType?.startsWith("video/") != true
        ) {
            Log.e("TEXT_TEXTURE", "Unsupported texture media type: $mimeType")
            android.widget.Toast.makeText(
                context, "Choose an image or video file.", android.widget.Toast.LENGTH_LONG
            ).show()
            return@rememberLauncherForActivityResult
        }
        coroutineScope.launch {
            try {
                val storedTexture = withContext(Dispatchers.IO) {
                    val textureDirectory = java.io.File(context.filesDir, "text_textures")
                    if (!textureDirectory.exists() && !textureDirectory.mkdirs()) {
                        throw java.io.IOException("Could not create texture storage")
                    }
                    val extension = mimeType.substringAfter('/', "bin")
                        .substringBefore('+')
                        .replace("jpeg", "jpg")
                        .replace(Regex("[^A-Za-z0-9]"), "")
                        .ifBlank { "bin" }
                    val file = java.io.File(
                        textureDirectory,
                        "${java.util.UUID.randomUUID()}.$extension"
                    )
                    try {
                        val input = context.contentResolver.openInputStream(uri)
                            ?: throw java.io.IOException("Selected texture cannot be opened")
                        input.use { source ->
                            file.outputStream().use { destination ->
                                source.copyTo(destination)
                            }
                        }
                        file
                    } catch (error: Exception) {
                        file.delete()
                        throw error
                    }
                }
                viewModel.updateClipTextTexture(
                    selectedText.id,
                    text.copy(
                        textureUri = Uri.fromFile(storedTexture).toString(),
                        textureMimeType = mimeType
                    )
                )
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.e("TEXT_TEXTURE", "Could not store selected texture", error)
                android.widget.Toast.makeText(
                    context,
                    "Texture import failed: ${error.message ?: "unknown error"}",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }
        }
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
                            } catch (_: Exception) {
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

                        val hasAudioTrack = when {
                            isImage -> false
                            mime.startsWith("audio/") -> true
                            else -> VideoUtils.hasAudioTrack(context, finalUri)
                        }
                        MediaInfo(name, dur, mime, finalUri, hasAudioTrack, uri)
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
                    mediaType = info.mimeType,
                    hasAudioTrack = info.hasAudioTrack
                )
                if (info.mimeType.startsWith("video/") || info.mimeType.startsWith("image/")) {
                    val recentItem = RecentMediaItem(info.originalUri, info.name)
                    withContext(Dispatchers.IO) {
                        RecentMediaRepository.add(context, projectId, recentItem)
                    }
                    recentMedia = RecentMediaRepository.getRecent(context, projectId)
                }
            }
        } catch (e: Exception) {
            Log.e("IMPORT", "Multi-import failed", e)
        } finally {
            pendingUris = emptyList()
        }
    }

    if (showRecentMediaPicker) {
        RecentMediaPickerPanel(
            recentMedia = recentMedia,
            onRecentSelected = { item ->
                pendingUris = listOf(item.uri)
                showRecentMediaPicker = false
            },
            onOpenStorage = {
                showRecentMediaPicker = false
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
            onDismiss = { showRecentMediaPicker = false }
        )
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

        CoroutineScope(Dispatchers.Main).launch {
            try {
                val success = viewModel.createVisualizerClipWithBeats(context)
                isDetectingBeats = false
                if (!success) {
                    beatDetectionError = "Could not create visualizer"
                } else {
                    beatDetectionError = null
                    activePanel = "visualizer"
                }
            } catch (e: Throwable) {
                isDetectingBeats = false
                beatDetectionError = "Failed: ${e.message}"
                Log.e("VISUALIZER", "Create failed", e)
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  PANEL HOST
    // ═══════════════════════════════════════════════════════════
    @Composable
    fun EditorPanelHost() {
        val selected = state.selectedClip

        when (activePanel) {
            null -> FeatureShelf(
                onFeatureSelected = { key ->
                    if (key == "filters") filterEditLayerId = null
                    if (key == "effects") effectEditLayerId = null
                    if (key == "matte") {
                        matteEditLayerId = selected?.takeIf { it.isColorMatteClip }?.id
                    }
                    if (key == "wheel") viewModel.prepareColorWheelLayer()
                    if (key == "spotlight") {
                        val spotlightClip = selected?.takeIf { it.isSpotlightClip }
                            ?: viewModel.createSpotlightClip()
                        spotlightEditLayerId = spotlightClip.id
                    }
                    activePanel = key
                },
                scrollState = featureScrollState
            )

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
                hasClipSelected = selected != null,
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
                keyframesEnabled = selected?.isTextClip == true,
                keyframeMap = selected?.keyframes.orEmpty(),
                keyframeTimeSec = selected?.let {
                    KeyframeStore.clipLocalTimeSeconds(
                        state.currentPosMs, it.timelineStartMs, it.durationMs
                    )
                } ?: 0f,
                onToggleKeyframe = viewModel::toggleKeyframeAtPlayhead,
                onTypographyAnimationSelected =
                    viewModel::applySelectedTextTypographyPreset,
                onTextChanged = { viewModel.updateSelectedText(it) },
                onAlignmentChanged = { viewModel.updateSelectedTextAlignment(it) },
                onDraftTextChanged = { content ->
                    viewModel.updateSelectedText(
                        viewModel.getSelectedTextState().copy(content = content)
                    )
                },
                onCreateNew = { viewModel.createTextClip() },
                onRemove = { viewModel.removeSelectedText() },
                onClose = {
                    activePanel = null
                    openTextEditor = false
                }
            )
            "layer_shadow" -> {
                val target = selected?.takeIf {
                    !it.isAudio && (it.isVisualClip || it.isTextClip ||
                            it.isColorMatteClip || it.isOverlayClip ||
                            it.isVisualizerClip || it.isSpotlightClip ||
                            (it.isEffectClip && it.effectState?.overlay != null))
                }
                LayerShadowPanel(
                    layerId = target?.id,
                    layerName = target?.name,
                    initialState = target?.layerShadow
                        ?: com.moody.moodyvideoeditor.data.LayerShadowState(),
                    onUpdate = { shadow ->
                        target?.let { viewModel.updateLayerShadow(it.id, shadow) }
                    },
                    onClose = { activePanel = null }
                )
            }
            "track_matte" -> {
                val selectedText = selected?.takeIf { it.isTextClip }
                TrackMattePanel(
                    textState = selectedText?.textState,
                    onPickTexture = {
                        if (selectedText != null) {
                            textTexturePicker.launch(arrayOf("image/*", "video/*"))
                        }
                    },
                    onTextStateChange = { textState ->
                        selectedText?.let {
                            viewModel.updateClipTextTexture(it.id, textState)
                        }
                    },
                    onToggleKeyframe = viewModel::toggleKeyframeAtPlayhead,
                    hasKeyframeAtPlayhead = { property ->
                        viewModel.hasKeyframeForPropertyAtPlayhead(property)
                    },
                    keyframeMap = selectedText?.keyframes ?: emptyMap(),
                    clipDurationSec = (selectedText?.durationMs ?: 3000L) / 1000f,
                    onUpdateKeyframe = { property, oldTime, newTime, newValue ->
                        viewModel.updateKeyframeInGraph(
                            property, oldTime, newTime, newValue
                        )
                    },
                    onDeleteKeyframe = { property, time ->
                        viewModel.deleteKeyframeFromGraph(property, time)
                    },
                    onKeyframeEditStart = { viewModel.beginKeyframeGraphEdit() },
                    onKeyframeEditEnd = { viewModel.commitKeyframeGraphEdit() },
                    onClose = { activePanel = null }
                )
            }
            "animations" -> AnimationsPanel(
                currentAnimation = viewModel.getSelectedAnimation(),
                currentDuration = viewModel.getSelectedAnimationDuration(),
                hasTextClipSelected = selected?.isTextClip == true ||
                        state.multiSelectedIds.isNotEmpty(),
                onAnimationSelected = { viewModel.setSelectedAnimation(it) },
                onDurationChanged = { viewModel.setSelectedAnimationDuration(it) },
                onPreview = { exoPlayer.seekTo(0) },
                onClose = { activePanel = null }
            )

            "matte" -> {
                val editLayer = matteEditLayerId?.let { id ->
                    state.clips.firstOrNull { it.id == id && it.isColorMatteClip }
                } ?: selected?.takeIf { it.isColorMatteClip }
                ColorMattePanel(
                    editLayerId = editLayer?.id,
                    initialStyle = editLayer?.matteStyle
                        ?: com.moody.moodyvideoeditor.data.ColorMatteStyle(),
                    initialOpacity = editLayer?.filters?.opacity
                        ?: com.moody.moodyvideoeditor.data.ColorMatteDefaults.DEFAULT_OPACITY,
                    initialDurationMs = editLayer?.durationMs
                        ?: com.moody.moodyvideoeditor.data.ColorMatteDefaults.DEFAULT_DURATION_MS,
                    onApply = { style, opacity, durationMs ->
                        viewModel.createColorMatte(
                            color = style.solidColor,
                            opacity = opacity,
                            durationMs = durationMs,
                            style = style
                        )
                        matteEditLayerId = null
                    },
                    onUpdate = { style, opacity, durationMs ->
                        editLayer?.let {
                            viewModel.updateColorMatte(
                                it.id, style, opacity, durationMs
                            )
                        }
                    },
                    onDelete = {
                        editLayer?.let { viewModel.removeColorMatte(it.id) }
                        matteEditLayerId = null
                        activePanel = null
                    },
                    onClose = {
                        matteEditLayerId = null
                        activePanel = null
                    }
                )
            }
            "spotlight" -> {
                val editLayer = spotlightEditLayerId?.let { id ->
                    state.clips.firstOrNull { it.id == id && it.isSpotlightClip }
                }
                SpotlightPanel(
                    editLayerId = editLayer?.id,
                    initialState = editLayer?.spotlight ?: SpotlightState(),
                    keyframeMap = editLayer?.keyframes ?: emptyMap(),
                    currentTimeSec = editLayer?.let {
                        KeyframeStore.clipLocalTimeSeconds(
                            state.currentPosMs,
                            it.timelineStartMs,
                            it.durationMs
                        )
                    } ?: 0f,
                    clipDurationSec = (editLayer?.durationMs ?: 5_000L) / 1000f,
                    onToggleKeyframe = { property ->
                        editLayer?.let { viewModel.selectClip(it) }
                        viewModel.toggleKeyframeAtPlayhead(property)
                    },
                    onUpdateKeyframe = { property, oldTime, newTime, newValue ->
                        viewModel.updateKeyframeInGraph(property, oldTime, newTime, newValue)
                    },
                    onDeleteKeyframe = { property, time ->
                        viewModel.deleteKeyframeFromGraph(property, time)
                    },
                    onKeyframeEditStart = { viewModel.beginKeyframeGraphEdit() },
                    onKeyframeEditEnd = { viewModel.commitKeyframeGraphEdit() },
                    onCreate = { spotlight ->
                        val created = viewModel.createSpotlightClip()
                        viewModel.updateSpotlight(created.id, spotlight)
                        spotlightEditLayerId = created.id
                        activePanel = null
                    },
                    onUpdate = { spotlight ->
                        editLayer?.let { viewModel.updateSpotlight(it.id, spotlight) }
                    },
                    onDelete = {
                        editLayer?.let { viewModel.removeSpotlight(it.id) }
                        spotlightEditLayerId = null
                        activePanel = null
                    },
                    onClose = {
                        spotlightEditLayerId = null
                        activePanel = null
                    }
                )
            }
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
                    onApplyOverlayLayer = { key, intensity, config ->
                        viewModel.createEffectLayerAtWithConfig(key, intensity, config)
                        effectEditLayerId = null
                    },
                    onUpdateIntensity = { intensity ->
                        editLayer?.let {
                            viewModel.updateEffectLayerIntensity(
                                it.id, intensity
                            )
                        }
                    },
                    onOverlayPropertyChange = { key, config ->
                        val preset = com.moody.moodyvideoeditor.data.EffectLibrary
                            .findByKey(key)
                        if (preset != null) {
                            val currentPreview = viewModel.state.value.previewEffectState
                            val updated = if (
                                currentPreview != null &&
                                currentPreview.presetKey == key
                            ) {
                                currentPreview.copy(overlay = config)
                            } else {
                                com.moody.moodyvideoeditor.data.EffectState(
                                    kind = com.moody.moodyvideoeditor.data.EffectState.KIND_EFFECT,
                                    presetKey = key,
                                    overlay = config,
                                    motion = preset.motion,
                                    filters = preset.filters,
                                    edgeGlow = preset.edgeGlow,
                                    lightLeak = preset.lightLeak,
                                    masterIntensity = 100f
                                )
                            }
                            viewModel.setPreviewEffectFull(updated)
                        }
                    },
                    onLightningPropertyChange = { key, config ->
                        val currentPreview = viewModel.state.value.previewEffectState
                        if (currentPreview != null &&
                            currentPreview.presetKey == key &&
                            currentPreview.lightning != null
                        ) {
                            viewModel.setPreviewEffectFull(
                                currentPreview.copy(lightning = config)
                            )
                        }
                        if (editLayer != null) {
                            viewModel.updateClipEffectLightning(editLayer.id, config)
                        }
                    },
                    onApplyLightningLayer = { key, intensity, config ->
                        viewModel.createEffectLayerAt(key, intensity)
                        val newLayer = viewModel.state.value.selectedClip
                        if (newLayer != null && newLayer.isEffectClip) {
                            viewModel.updateClipEffectLightning(newLayer.id, config)
                        }
                        effectEditLayerId = null
                    },
                    onParticlePropertyChange = { key, config ->
                        val currentPreview = viewModel.state.value.previewEffectState
                        if (currentPreview != null &&
                            currentPreview.presetKey == key &&
                            currentPreview.particle != null
                        ) {
                            viewModel.setPreviewEffectFull(
                                currentPreview.copy(particle = config)
                            )
                        }
                        if (editLayer != null) {
                            viewModel.updateClipEffectParticle(editLayer.id, config)
                        }
                    },
                    onApplyParticleLayer = { key, intensity, config ->
                        viewModel.createEffectLayerAt(key, intensity)
                        val newLayer = viewModel.state.value.selectedClip
                        if (newLayer != null && newLayer.isEffectClip) {
                            viewModel.updateClipEffectParticle(newLayer.id, config)
                        }
                        effectEditLayerId = null
                    },
                    onFirePropertyChange = { key, config ->
                        val currentPreview = viewModel.state.value.previewEffectState
                        if (currentPreview != null &&
                            currentPreview.presetKey == key &&
                            currentPreview.fire != null
                        ) {
                            viewModel.setPreviewEffectFull(
                                currentPreview.copy(fire = config)
                            )
                        }
                        if (editLayer != null) {
                            viewModel.updateClipEffectFire(editLayer.id, config)
                        }
                    },
                    onApplyFireLayer = { key, intensity, config ->
                        viewModel.createEffectLayerAt(key, intensity)
                        val newLayer = viewModel.state.value.selectedClip
                        if (newLayer != null && newLayer.isEffectClip) {
                            viewModel.updateClipEffectFire(newLayer.id, config)
                        }
                        effectEditLayerId = null
                    },
                    onDustPropertyChange = { key, config ->
                        val currentPreview = viewModel.state.value.previewEffectState
                        if (currentPreview != null &&
                            currentPreview.presetKey == key &&
                            currentPreview.dust != null
                        ) {
                            viewModel.setPreviewEffectFull(
                                currentPreview.copy(dust = config)
                            )
                        }
                        if (editLayer != null) {
                            viewModel.updateClipEffectDust(editLayer.id, config)
                        }
                    },
                    onApplyDustLayer = { key, intensity, config ->
                        viewModel.createEffectLayerAt(key, intensity)
                        val newLayer = viewModel.state.value.selectedClip
                        if (newLayer != null && newLayer.isEffectClip) {
                            viewModel.updateClipEffectDust(newLayer.id, config)
                        }
                        effectEditLayerId = null
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

            "chromakey" -> ColorRemoverPanel(
                current = selected?.chromaKey ?: ChromaKeyState(),
                hasClipSelected = selected != null,
                onStateChanged = { viewModel.updateChromaKey(it) },
                onPickFromScreen = {
                    viewModel.startEyedropper()
                },
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
                val selectedVisual = selected?.takeIf { it.isVisualClip }
                val target = selectedVisual?.let { sel ->
                    val hasLeft = state.clips.any { other ->
                        other.id != sel.id && other.isVisualClip &&
                                other.trackIndex == sel.trackIndex &&
                                abs(other.timelineEndMs - sel.timelineStartMs) < 100L
                    }
                    if (hasLeft) sel
                    else state.clips.filter { other ->
                        other.id != sel.id && other.isVisualClip &&
                                other.trackIndex == sel.trackIndex &&
                                abs(other.timelineStartMs - sel.timelineEndMs) < 100L
                    }.minByOrNull { it.timelineStartMs }
                }

                TransitionsPanel(
                    current = target?.transition ?: TransitionState(),
                    hasPairAvailable = target != null,
                    hintText = when {
                        selectedVisual == null ->
                            "Pehle timeline pe ek video ya image clip select karo."

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
            "mask" -> MaskPanel(
                current = viewModel.selectedMaskAtPlayhead(),
                hasClipSelected = selected != null,
                hasKeyframeAtPlayhead = viewModel.hasMaskKeyframeAtPlayhead(),
                isPenMode = isMaskPenMode,
                isHandMode = isMaskHandMode,
                viewModel = viewModel,
                onTypeSelected = { type ->
                    viewModel.setMaskType(type)
                    if (type == com.moody.moodyvideoeditor.data.MaskType.CUSTOM) {
                        isMaskPenMode = true
                        isMaskHandMode = false
                    }
                },
                onPenToolToggle = {
                    isMaskPenMode = !isMaskPenMode
                    if (isMaskPenMode) isMaskHandMode = false
                },
                onHandToolToggle = {
                    isMaskHandMode = !isMaskHandMode
                    if (isMaskHandMode) isMaskPenMode = false
                },
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
                    isMaskHandMode = false
                }
            )

            "crop" -> CropPanel(
                clip = selected,
                playheadMs = state.currentPosMs,
                hasClipSelected = selected != null &&
                        (selected.isVisualClip || selected.isColorMatteClip),
                onCropChanged = { prop, value01 ->
                    viewModel.changeTransformProperty(prop, value01)
                },
                onToggleKeyframe = { prop ->
                    viewModel.toggleKeyframeAtPlayhead(prop)
                },
                onReset = { viewModel.setCrop(0f, 0f, 0f, 0f) },
                onClose = { activePanel = null }
            )

            "transform" -> {
                val hasAnySelected = selected != null ||
                        state.multiSelectedIds.isNotEmpty()
                val currentTimeSec = selected?.let {
                    KeyframeStore.clipLocalTimeSeconds(
                        state.currentPosMs, it.timelineStartMs, it.durationMs
                    )
                } ?: 0f
                val clipDurSec = selected?.let { it.durationMs / 1000f } ?: 5f
                val base = selected?.let { clip ->
                    TransformApplier.resolveLive(clip, currentTimeSec)
                } ?: TransformValues()
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
                    onResetAll = { viewModel.resetAllSelectedTransforms() },
                    onUpdateKeyframe = { prop, oldT, newT, newV ->
                        viewModel.updateKeyframeInGraph(prop, oldT, newT, newV)
                    },
                    onDeleteKeyframe = { prop, t ->
                        viewModel.deleteKeyframeFromGraph(prop, t)
                    },
                    onKeyframeEditStart = { viewModel.beginKeyframeGraphEdit() },
                    onKeyframeEditEnd = { viewModel.commitKeyframeGraphEdit() },
                    onClose = { activePanel = null }
                )
            }

            "keyframe_easing" -> {
                val playheadTimeSec = selected?.let {
                    KeyframeStore.clipLocalTimeSeconds(
                        state.currentPosMs, it.timelineStartMs, it.durationMs
                    )
                } ?: 0f
                KeyframeEasingPanel(
                    clipId = selected?.id,
                    keyframeMap = selected?.keyframes ?: emptyMap(),
                    playheadTimeSec = playheadTimeSec,
                    hasTimelineKeyframes = state.clips.any {
                        KeyframeStore.hasAnyKeyframes(it.keyframes)
                    },
                    onApplySelected = { ease ->
                        viewModel.setEaseAtPlayhead(ease)
                    },
                    onApplyTimeline = viewModel::setTimelineKeyframeEases,
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

            "soundfx" -> selected?.takeIf { it.isSoundFxClip }?.let { soundFxClip ->
                SoundFxPanel(
                    current = soundFxClip.soundFx,
                    onSelected = { viewModel.setClipSoundFx(soundFxClip.id, it) },
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
                val selectedAudio = when {
                    selected?.isAudio == true && !selected.isAudioEffectClip -> selected
                    selected?.isVisualClip == true && selected.linkedId != null ->
                        state.clips.firstOrNull {
                            it.isAudio && !it.isAudioEffectClip &&
                                    it.linkedId == selected.linkedId
                        }
                    else -> null
                }
                val selectedAudioId = selectedAudio?.id

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
                                                    launchVisualizerCreation()
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
                        val hasSelectedAudio = selectedAudio != null

                        val anyVisualizerInTimeline = state.clips.any {
                            it.isVisualizerClip
                        }

                        if (!hasSelectedAudio) {
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
                                            "Select a video with audio or an audio clip " +
                                                    "to create a beat-reactive visualizer.",
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
                                        "Create Visualizer for \"${selectedAudio?.name?.take(20).orEmpty()}\"",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center
                                    )
                                    Text(
                                        "Beats will be detected from this clip's audio.",
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
                                                .pointerInput(selectedAudio?.id) {
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
                                (vizClip.visualizer ?: VisualizerState()).let { visualizer ->
                                    if (pendingVizImageIsBackground) {
                                        visualizer.copy(
                                            backgroundImageUri = uri,
                                            showBackgroundImage = true
                                        )
                                    } else {
                                        visualizer.copy(imageUri = uri, showImage = true)
                                    }
                                }
                            )
                            pendingVizImageUri = null
                        }

                        val vizAudioId = vizClip.visualizer?.linkedAudioClipId
                        val hasAudio = state.clips.any {
                            it.id == vizAudioId && it.isAudio && !it.isAudioEffectClip
                        }

                        VisualizerPanel(
                            current = vizClip.visualizer ?: VisualizerState(),
                            hasAudio = hasAudio,
                            onStateChanged = {
                                viewModel.updateVisualizerLayer(vizClip.id, it)
                            },
                            onPickImage = {
                                pendingVizImageIsBackground = false
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
                            onPickBackgroundImage = {
                                pendingVizImageIsBackground = true
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
                            onClearBackgroundImage = {
                                viewModel.updateVisualizerLayer(
                                    vizClip.id,
                                    (vizClip.visualizer ?: VisualizerState()).copy(
                                        backgroundImageUri = null,
                                        showBackgroundImage = false
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
                hasClipSelected = selected?.let {
                    it.isVisualClip && it.type.startsWith("video/")
                } == true,
                onFreeze = { dur ->
                    viewModel.addFreezeFrame(context, dur) { message ->
                        android.widget.Toast.makeText(
                            context,
                            message,
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    }
                },
                onClose = { activePanel = null }
            )

            "delete" -> {
                if (state.multiSelectedIds.isNotEmpty()) {
                    viewModel.deleteSelectedClips()
                } else if (!viewModel.deleteKeyframesAtPlayhead()) {
                    viewModel.deleteCurrentClip()
                }
                activePanel = null
            }

            "blend" -> BlendModePanel(
                currentBlendMode = selected?.blendMode ?: "normal",
                currentBlendOpacity = selected?.blendOpacity ?: 100f,
                hasClipSelected = selected != null ||
                        state.multiSelectedIds.isNotEmpty(),
                multiCount = state.multiSelectedIds.size,
                onBlendModeSelected = { key -> viewModel.setBlendMode(key) },
                onBlendOpacityChanged = { v -> viewModel.setBlendOpacity(v) },
                onClose = { activePanel = null }
            )
            "opacity" -> OpacityPanel(
                layerName = selected?.name.orEmpty(),
                opacity = selected?.let {
                    KeyframeStore.layerOpacityAt(
                        it.keyframes,
                        it.layerOpacity,
                        KeyframeStore.clipLocalTimeSeconds(
                            state.currentPosMs, it.timelineStartMs, it.durationMs
                        )
                    )
                } ?: 100f,
                keyframes = selected?.keyframes.orEmpty(),
                timeSec = selected?.let {
                    KeyframeStore.clipLocalTimeSeconds(
                        state.currentPosMs, it.timelineStartMs, it.durationMs
                    )
                } ?: 0f,
                hasLayerSelected = selected != null && !selected.isAudio,
                onOpacityChanged = { viewModel.changeTransformProperty("layer.opacity", it) },
                onToggleKeyframe = viewModel::toggleKeyframeAtPlayhead,
                onClose = { activePanel = null }
            )
            "captions" -> AutoCaptionsPanel(
                isProcessing = isGeneratingCaptions,
                progress = captionProgress,
                statusMessage = captionStatus,
                hasClipSelected = selected != null,
                generatedCaptions = generatedCaptions,
                detectedLanguage = detectedCaptionLanguage,
                onDownloadModel = { modelFile ->
                    captionJob?.cancel()
                    captionJob = coroutineScope.launch {
                        isGeneratingCaptions = true
                        captionProgress = 0f
                        captionStatus = "Downloading $modelFile…"

                        val ok = WhisperModelManager.downloadModel(
                            context = context,
                            fileName = modelFile,
                            onProgress = { p ->
                                captionProgress = p
                                captionStatus = "Downloading… ${(p * 100).toInt()}%"
                            }
                        )

                        isGeneratingCaptions = false
                        captionProgress = 0f
                        captionStatus = if (ok) "✅ Ready" else "❌ Failed"
                        if (ok) {
                            delay(1200)
                            captionStatus = ""
                        }
                    }
                },
                onCancelDownload = {
                    captionJob?.cancel()
                    captionJob = null
                    isGeneratingCaptions = false
                    captionProgress = 0f
                    captionStatus = "⏸ Paused"
                },
                onDeleteModel = { modelFile ->
                    WhisperModelManager.deleteModel(context, modelFile)
                },
                onGenerateCaptions = { grouping, language, modelFile, wpl, wpline, presetId, highlightColor ->
                    val sel = selected
                    if (sel != null) {
                        captionJob?.cancel()
                        captionJob = coroutineScope.launch {
                            isGeneratingCaptions = true
                            captionStatus = ""
                            captionProgress = 0f

                            val res = CaptionGenerator.generate(
                                context = context,
                                clip = sel,
                                language = language,
                                modelFile = modelFile,
                                grouping = grouping,
                                wordsPerLayer = wpl,
                                wordsPerLine = wpline,
                                viewModel = viewModel,
                                onStatus = { captionStatus = it },
                                onProgress = { captionProgress = it },
                                captionPresetId = presetId,
                                highlightColor = highlightColor
                            )

                            if (res.success) {
                                captionStatus = "✅ ${res.segmentsAdded} clips"
                                generatedCaptions = res.segments
                                detectedCaptionLanguage = res.detectedLanguage
                                delay(800)
                                isGeneratingCaptions = false
                                captionStatus = ""
                            } else {
                                captionStatus = "❌ ${res.errorMessage ?: "Failed"}"
                                isGeneratingCaptions = false
                            }
                        }
                    }
                },
                onCancel = {
                    captionJob?.cancel()
                    captionJob = null
                    isGeneratingCaptions = false
                    captionStatus = ""
                    captionProgress = 0f
                },
                onClearCaptions = {
                    generatedCaptions = emptyList()
                    detectedCaptionLanguage = ""
                },
                onClose = { activePanel = null }
            )

            "manual_captions" -> ManualCaptionsPanel(
                statusMessage = captionStatus,
                onAddManual = { input, grouping, wordsPerLayer, wordsPerLine, presetId, highlightColor ->
                    val videoDurationMs = viewModel.state.value.totalDurationMs
                    val parsed = ManualCaptionParser.parse(input, videoDurationMs)
                    if (parsed.isEmpty()) {
                        captionStatus = "❌ No valid captions — check format"
                        return@ManualCaptionsPanel
                    }

                    captionJob?.cancel()
                    captionJob = coroutineScope.launch {
                        isGeneratingCaptions = true
                        captionStatus = "Generating ${parsed.size} captions…"
                        captionProgress = 0f

                        val res = CaptionGenerator.generateFromManual(
                            parsedCaptions = parsed,
                            grouping = grouping,
                            wordsPerLayer = wordsPerLayer,
                            wordsPerLine = wordsPerLine,
                            viewModel = viewModel,
                            offsetMs = 0L,
                            onProgress = { captionProgress = it },
                            captionPresetId = presetId,
                            highlightColor = highlightColor
                        )

                        isGeneratingCaptions = false
                        captionProgress = 0f

                        if (res.success) {
                            captionStatus = "✅ ${res.segmentsAdded} clips added"
                            generatedCaptions = res.segments
                            detectedCaptionLanguage = ""
                            delay(1200)
                            captionStatus = ""
                        } else {
                            captionStatus = "❌ ${res.errorMessage ?: "Failed"}"
                        }
                    }
                },
                onClearCaptions = {
                    generatedCaptions = emptyList()
                    detectedCaptionLanguage = ""
                },
                onClose = { activePanel = null }
            )

            "save_template" -> {
                var templateName by remember { mutableStateOf("My Template") }
                var saved by remember { mutableStateOf(false) }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF1A1F3A))
                        .padding(16.dp)
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            "💾 Save as Template",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Current timeline will be saved. " +
                                    "Image/video layers become replaceable placeholders.",
                            color = Color(0xFF888888),
                            fontSize = 10.sp,
                            lineHeight = 14.sp
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF0A0A0A))
                                .padding(horizontal = 12.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            BasicTextField(
                                value = templateName,
                                onValueChange = { templateName = it },
                                singleLine = true,
                                textStyle = TextStyle(
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                cursorBrush = SolidColor(Color(0xFF7C3AED)),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF181818))
                                    .pointerInput(Unit) {
                                        detectTapGestures { activePanel = null }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "Cancel",
                                    color = Color(0xFFAAAAAA),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .weight(2f)
                                    .height(44.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (saved) Color(0xFF22C55E)
                                        else Color(0xFF7C3AED)
                                    )
                                    .pointerInput(templateName, saved) {
                                        detectTapGestures {
                                            if (!saved && templateName.isNotBlank()) {
                                                val ok = viewModel.saveAsTemplate(
                                                    context, templateName.trim()
                                                )
                                                if (ok) {
                                                    saved = true
                                                    captionStatus = "✅ Template saved"
                                                } else {
                                                    captionStatus = "❌ Save failed"
                                                }
                                            }
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    if (saved) "✓ Saved!" else "💾 Save Template",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        if (saved) {
                            LaunchedEffect(Unit) {
                                delay(1000)
                                activePanel = null
                                captionStatus = ""
                            }
                        }
                    }
                }
            }

            "replace_media" -> {
                val sel = selected

                when {
                    sel == null -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF2A0F0F))
                                .padding(16.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    "⚠️ No layer selected",
                                    color = Color(0xFFFF6B6B),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "Tap a layer on the timeline first.",
                                    color = Color(0xFF888888),
                                    fontSize = 10.sp
                                )
                                Box(
                                    modifier = Modifier
                                        .height(36.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF181818))
                                        .pointerInput(Unit) {
                                            detectTapGestures { activePanel = null }
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
                            }
                        }
                    }

                    sel.isTextClip -> {
                        val currentText = sel.textState?.content ?: ""
                        var newText by remember(sel.id) {
                            mutableStateOf(currentText)
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF1A1F3A))
                                .padding(16.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(
                                    "📝 Replace Text",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "All fonts, colors, animations, keyframes " +
                                            "will be preserved.",
                                    color = Color(0xFF888888),
                                    fontSize = 10.sp,
                                    lineHeight = 14.sp
                                )

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 80.dp, max = 160.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF0A0A0A))
                                        .padding(12.dp)
                                ) {
                                    BasicTextField(
                                        value = newText,
                                        onValueChange = { newText = it },
                                        textStyle = TextStyle(
                                            color = Color.White,
                                            fontSize = 13.sp,
                                            lineHeight = 18.sp
                                        ),
                                        cursorBrush = SolidColor(Color(0xFF7C3AED)),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(44.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0xFF181818))
                                            .pointerInput(Unit) {
                                                detectTapGestures { activePanel = null }
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            "Cancel",
                                            color = Color(0xFFAAAAAA),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    Box(
                                        modifier = Modifier
                                            .weight(2f)
                                            .height(44.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                if (newText.isNotBlank() && newText != currentText)
                                                    Color(0xFF22C55E)
                                                else Color(0xFF2A2A2A)
                                            )
                                            .pointerInput(newText, currentText) {
                                                detectTapGestures {
                                                    if (newText.isNotBlank() &&
                                                        newText != currentText
                                                    ) {
                                                        val st = sel.textState
                                                        if (st != null) {
                                                            viewModel.updateSelectedText(
                                                                st.copy(content = newText)
                                                            )
                                                            captionStatus =
                                                                "✅ Text replaced"
                                                            activePanel = null
                                                        }
                                                    }
                                                }
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            "✓ Replace Text",
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }

                    sel.type.startsWith("image/") ||
                            sel.type.startsWith("video/") -> {

                        val mediaPicker = rememberLauncherForActivityResult(
                            contract = ActivityResultContracts.OpenDocument()
                        ) { uri: Uri? ->
                            if (uri != null) {
                                try {
                                    context.contentResolver
                                        .takePersistableUriPermission(
                                            uri,
                                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                                        )
                                } catch (_: Exception) {}
                                viewModel.replaceClipUri(sel.id, uri)
                                captionStatus = "✅ Media replaced"
                                activePanel = null
                            }
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF1A1F3A))
                                .padding(16.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(
                                    "🔄 Replace Media",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "Current: \"${sel.name}\"\n" +
                                            "All transforms, animations, keyframes will be preserved.",
                                    color = Color(0xFF888888),
                                    fontSize = 10.sp,
                                    lineHeight = 14.sp
                                )

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(44.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0xFF181818))
                                            .pointerInput(Unit) {
                                                detectTapGestures { activePanel = null }
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            "Cancel",
                                            color = Color(0xFFAAAAAA),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    Box(
                                        modifier = Modifier
                                            .weight(2f)
                                            .height(44.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0xFF4F9DFF))
                                            .pointerInput(Unit) {
                                                detectTapGestures {
                                                    mediaPicker.launch(
                                                        arrayOf("image/*", "video/*")
                                                    )
                                                }
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            "📷 Pick New Media",
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }

                    else -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF2A1A0F))
                                .padding(16.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    "⚠️ This layer can't be replaced",
                                    color = Color(0xFFFFAA33),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "Only image, video, and text layers support replacement.",
                                    color = Color(0xFF888888),
                                    fontSize = 10.sp
                                )
                                Box(
                                    modifier = Modifier
                                        .height(36.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF181818))
                                        .pointerInput(Unit) {
                                            detectTapGestures { activePanel = null }
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
                            }
                        }
                    }
                }
            }

            else -> FeatureShelf(
                onFeatureSelected = {
                    if (it == "wheel") viewModel.prepareColorWheelLayer()
                    if (it == "matte") {
                        matteEditLayerId = state.selectedClip?.takeIf {
                            it.isColorMatteClip
                        }?.id
                    }
                    activePanel = it
                },
                scrollState = featureScrollState
            )
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  RESPONSIVE PREVIEW HEIGHT — screen size ke hisaab se
    // ═══════════════════════════════════════════════════════════

    val configuration = LocalConfiguration.current
    val screenHeightDp = configuration.screenHeightDp
    val previewHeight = when {
        screenHeightDp < 640 -> 150.dp
        screenHeightDp < 800 -> 212.dp
        else -> 242.dp
    }
    val timelineHeight = when {
        screenHeightDp < 640 -> 170.dp
        screenHeightDp < 800 -> 200.dp
        else -> 230.dp
    }
    val visibleTimelineHeight = if (activePanel != null && activePanel != "export") {
        (timelineHeight / 2).coerceAtLeast(80.dp)
    } else {
        timelineHeight
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF121212))
        ) {

            // ═══════════════════════════════════════════════════════════
            //  1. TOP BAR
            // ═══════════════════════════════════════════════════════════
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

            // ═══════════════════════════════════════════════════════════
            //  2. PREVIEW (fixed responsive height)
            // ═══════════════════════════════════════════════════════════
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(previewHeight)
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
                    isMaskPenMode = isMaskPenMode,
                    isMaskHandMode = isMaskHandMode,
                    onMaskGestureStart = { viewModel.beginGroupGesture() },
                    onMaskGestureEnd = { viewModel.endGroupGesture() },
                    onMaskPointAdd = { x, y -> viewModel.addMaskPoint(x, y) },
                    onMaskAnchorMove = { index, x, y ->
                        viewModel.moveMaskAnchor(index, x, y)
                    },
                    onMaskHandleMove = { index, isIn, dx, dy ->
                        viewModel.moveMaskHandle(index, isIn, dx, dy)
                    },
                    onMaskPointToggle = { index -> viewModel.toggleMaskPointSmooth(index) },
                    onMaskPointDelete = { index -> viewModel.deleteMaskPoint(index) },
                    onMaskMove = { dx, dy -> viewModel.moveMaskBy(dx, dy) },
                    onMaskStateChanged = { viewModel.updateMask(it) },
                    onClosePath = { viewModel.setMaskClosed(true) },
                    onClipSelected = { clipId ->
                        state.clips.firstOrNull { it.id == clipId }?.let(::handleLayerTap)
                    },
                    onEditText = { clipId ->
                        state.clips.firstOrNull { it.id == clipId }?.let { clip ->
                            viewModel.selectClip(clip)
                            openTextEditor = true
                            activePanel = "text"
                        }
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
                    onLayerPositionChanged = { clipId: String, x: Float, y: Float ->
                        viewModel.updateSelectedPositionBulk(clipId, x, y)
                    },
                    onLayerTransformChanged = { clipId: String, s: Float, r: Float ->
                        viewModel.updateSelectedTransformBulk(clipId, s, r)
                    },
                    onVisualizerPositionChanged = { clipId: String, x: Float, y: Float ->
                        viewModel.updateSelectedPositionBulk(clipId, x, y)
                    },
                    onVisualizerTransformChanged = { clipId: String, s: Float, r: Float ->
                        viewModel.updateSelectedTransformBulk(clipId, s, r)
                    },
                    onSpotlightChanged = { clipId, spotlight ->
                        viewModel.updateSpotlight(clipId, spotlight)
                    },
                )

                // 🟩 EYEDROPPER — PreviewCanvas ke BAHAR, Box ke andar
                val eyedropperActive by viewModel.eyedropperActive.collectAsState()
                if (eyedropperActive) {
                    EyedropperOverlay(
                        active = true,
                        onColorPicked = { color ->
                            viewModel.applyPickedColor(color)
                        },
                        onCancel = {
                            viewModel.cancelEyedropper()
                        }
                    )
                }
            }

            // ═══════════════════════════════════════════════════════════
            //  3. CONTROL BAR — media, playback, undo and redo
            // ═══════════════════════════════════════════════════════════
            ControlBar(
                onMediaClick = { showRecentMediaPicker = true },
                currentRatio = state.aspectRatio,
                onRatioClick = { activePanel = "ratio" },
                isPlaying = isPlaybackActive,
                canUndo = state.canUndo,
                canRedo = state.canRedo,
                seekStepSeconds = settings.playheadSeekStepSeconds,
                onSeekBy = { offsetMs ->
                    val totalDuration = state.totalDurationMs.coerceAtLeast(0L)
                    viewModel.setCurrentPos(
                        (state.currentPosMs + offsetMs).coerceIn(0L, totalDuration)
                    )
                },
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
                onUndo = { viewModel.undo() },
                onRedo = { viewModel.redo() },
                isMuted = state.isMuted,
                hasKeyframeAtPlayhead = viewModel.hasKeyframeAtPlayhead(),
                onMuteToggle = { viewModel.toggleMute() },
                onKeyframe = { viewModel.toggleKeyframeAll() },
                onMagnet = { viewModel.closeGapsOnSelectedTrack() },
                onDuplicate = { viewModel.duplicateCurrentClip() },
                onDelete = {
                    if (state.multiSelectedIds.isNotEmpty()) {
                        viewModel.deleteSelectedClips()
                    } else if (!viewModel.deleteKeyframesAtPlayhead()) {
                        viewModel.deleteCurrentClip()
                    }
                },
                hasVideo = state.clips.isNotEmpty(),
                currentPosMs = state.currentPosMs,
                totalDurationMs = state.totalDurationMs
            )

            // ═══════════════════════════════════════════════════════════
            //  4. EDITING TOOLS — one horizontally scrollable row
            // ═══════════════════════════════════════════════════════════
            PlaybackControls(
                hasVideo = state.clips.isNotEmpty(),
                onCopy = { viewModel.copySelectedClips() },
                onCut = { viewModel.cutSelectedClips() },
                onPaste = { viewModel.pasteClipsAtPlayhead() },
                canCopy = state.selectedClipId != null ||
                        state.multiSelectedIds.isNotEmpty(),
                canPaste = state.clipboardAction == "paste",
                onAddVisualLayer = { viewModel.addVisualLayer() },
                onAddAudioLayer = { viewModel.addAudioLayer() },
                onSelectBackward = { viewModel.selectBackward() },
                onSelectForward = { viewModel.selectForward() },
                onSelectAll = { viewModel.selectAllClips() },
                onSelectAllTrack = { viewModel.selectAllOnCurrentTrack() },
                onDeselectAll = { viewModel.deselectAll() },
                onLink = { viewModel.linkSelectedClips() },
                onUnlink = {
                    state.selectedClip?.let {
                        viewModel.unlinkClip(it.id)
                    }
                },
                isLinked = state.selectedClip?.let {
                    it.linkGroupId != null || it.linkedId != null
                } == true,
                canLink = state.multiSelectedIds.size >= 2 &&
                        state.selectedClip?.let {
                            it.linkGroupId == null && it.linkedId == null
                        } != false,
                onSplit = { viewModel.splitCurrentClip() },
                canSplit = state.selectedClip?.let { sel ->
                    state.currentPosMs > (sel.timelineStartMs + EditorClip.MIN_DURATION_MS) &&
                            state.currentPosMs < (sel.timelineEndMs - EditorClip.MIN_DURATION_MS)
                } ?: false,
                onTrimLeft = { viewModel.trimLeft() },
                onTrimRight = { viewModel.trimRight() },
                canTrimLeft = state.selectedClip?.let { sel ->
                    state.currentPosMs > (sel.timelineStartMs + EditorClip.MIN_DURATION_MS)
                } ?: false,
                canTrimRight = state.selectedClip?.let { sel ->
                    state.currentPosMs < (sel.timelineEndMs - EditorClip.MIN_DURATION_MS)
                } ?: false,
                zoomSlider = state.timelineZoom,
                totalSec = state.totalDurationMs / 1000f,
                viewportContentWidthDp = 320f,
                onZoomChange = { viewModel.setTimelineZoom(it) }
            )

            // ═══════════════════════════════════════════════════════════
            //  5. TIMELINE (responsive fixed height)
            // ═══════════════════════════════════════════════════════════
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(visibleTimelineHeight)
            ) {
                Timeline(
                    state = state,
                    onClipTapped = { clip ->
                        handleLayerTap(clip)
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
                    },
                    onReplaceRequested = { clipId ->
                        val clip = state.clips.firstOrNull { it.id == clipId }
                        if (clip != null) {
                            viewModel.selectClip(clip)
                            activePanel = "replace_media"
                        }
                    },
                    onEmptyAreaTapped = {
                        viewModel.clearAllSelection()
                    }
                )
            }

            // ═══════════════════════════════════════════════════════════
            //  6. FEATURE PANEL — poori bachi hui jagah (weight 1f)
            // ═══════════════════════════════════════════════════════════
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .heightIn(min = 90.dp)
            ) {
                EditorPanelHost()
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  EXPORT DIALOG
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
    //  EXPORT OVERLAY
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