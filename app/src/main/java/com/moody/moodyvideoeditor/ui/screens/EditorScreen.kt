@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.moody.moodyvideoeditor.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.moody.moodyvideoeditor.data.AdjustmentData
import com.moody.moodyvideoeditor.ui.components.ControlBar
import com.moody.moodyvideoeditor.ui.components.FeatureShelf
import com.moody.moodyvideoeditor.ui.components.PlaybackControls
import com.moody.moodyvideoeditor.ui.components.PreviewCanvas
import com.moody.moodyvideoeditor.ui.components.Timeline
import com.moody.moodyvideoeditor.ui.features.AdjustmentsPanel
import com.moody.moodyvideoeditor.ui.features.AnimationsPanel
import com.moody.moodyvideoeditor.ui.features.AspectRatioPanel
import com.moody.moodyvideoeditor.ui.features.AudioFxPanel
import com.moody.moodyvideoeditor.ui.features.BeatsPanel
import com.moody.moodyvideoeditor.ui.features.ChromaKeyPanel
import com.moody.moodyvideoeditor.ui.features.ColorWheelPanel
import com.moody.moodyvideoeditor.ui.features.CropPanel
import com.moody.moodyvideoeditor.ui.features.EffectsPanel
import com.moody.moodyvideoeditor.ui.features.ExportPanel
import com.moody.moodyvideoeditor.ui.features.FiltersPanel
import com.moody.moodyvideoeditor.ui.features.FreezePanel
import com.moody.moodyvideoeditor.ui.features.MotionPanel
import com.moody.moodyvideoeditor.ui.features.MusicPanel
import com.moody.moodyvideoeditor.ui.features.OverlaysPanel
import com.moody.moodyvideoeditor.ui.features.SoundFxPanel
import com.moody.moodyvideoeditor.ui.features.SpeedPanel
import com.moody.moodyvideoeditor.ui.features.StickersPanel
import com.moody.moodyvideoeditor.ui.features.TextPanel
import com.moody.moodyvideoeditor.ui.features.TransformPanel
import com.moody.moodyvideoeditor.ui.features.TransitionsPanel
import com.moody.moodyvideoeditor.ui.features.TrimPanel
import com.moody.moodyvideoeditor.ui.features.VolumePanel
import com.moody.moodyvideoeditor.utils.SpeedEngine
import com.moody.moodyvideoeditor.utils.TrimPlaybackEnforcer
import com.moody.moodyvideoeditor.utils.VideoExporter
import com.moody.moodyvideoeditor.utils.VideoUtils
import com.moody.moodyvideoeditor.viewmodel.EditorViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun EditorScreen(
    onBack: () -> Unit,
    viewModel: EditorViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    var activePanel by remember { mutableStateOf<String?>(null) }
    var pendingUri by remember { mutableStateOf<Uri?>(null) }
    var isExporting by remember { mutableStateOf(false) }
    var exportProgress by remember { mutableFloatStateOf(0f) }
    var exportMessage by remember { mutableStateOf("") }

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply { playWhenReady = false }
    }
    DisposableEffect(Unit) {
        onDispose { exoPlayer.release() }
    }

    // Speed + volume
    LaunchedEffect(state.selectedClipId, state.selectedClip?.speed) {
        val clip = state.selectedClip
        if (clip != null) exoPlayer.setPlaybackSpeed(SpeedEngine.clampForExoPlayer(clip.speed))
        else exoPlayer.setPlaybackSpeed(1.0f)
    }
    LaunchedEffect(state.volume, state.isMuted) {
        exoPlayer.volume = if (state.isMuted) 0f else state.volume
    }

    // Load selected clip
    LaunchedEffect(
        state.selectedClipId,
        state.selectedClip?.sourceStartMs,
        state.selectedClip?.sourceEndMs
    ) {
        val clip = state.selectedClip ?: return@LaunchedEffect
        if (clip.isAudio) return@LaunchedEffect
        val currentPos = exoPlayer.currentPosition
        val mediaItem = MediaItem.Builder()
            .setUri(clip.uri)
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(clip.sourceStartMs)
                    .setEndPositionMs(clip.sourceEndMs)
                    .build()
            )
            .build()
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
        exoPlayer.seekTo(
            currentPos.coerceAtMost(
                (clip.sourceEndMs - clip.sourceStartMs).coerceAtLeast(
                    0L
                )
            )
        )
        exoPlayer.setPlaybackSpeed(SpeedEngine.clampForExoPlayer(clip.speed))
        exoPlayer.volume = if (state.isMuted) 0f else state.volume
    }

    // Playback tick → state
    LaunchedEffect(exoPlayer) {
        while (true) {
            viewModel.setCurrentPos(exoPlayer.currentPosition)
            viewModel.setPlaying(exoPlayer.isPlaying)
            delay(50)
        }
    }

    // Trim playback enforcer
    LaunchedEffect(exoPlayer, state.selectedClipId) {
        while (true) {
            val clip = state.selectedClip
            if (clip != null && !clip.isAudio) TrimPlaybackEnforcer.enforce(exoPlayer, clip)
            delay(50)
        }
    }

    // Media picker
    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? -> uri?.let { pendingUri = it } }

    LaunchedEffect(pendingUri) {
        val uri = pendingUri ?: return@LaunchedEffect
        try {
            val result = withContext(Dispatchers.IO) {
                val name = VideoUtils.getFileName(context, uri)
                val dur = VideoUtils.getVideoDuration(context, uri)
                Triple(name, dur, dur)
            }
            viewModel.addClipWithSource(uri, result.first, result.second, result.third)
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            pendingUri = null
        }
    }

    fun startExport() {
        if (state.clips.isEmpty()) {
            exportMessage = "❌ No clips to export"; return
        }
        isExporting = true; exportProgress = 0f; exportMessage = "Starting FFmpeg…"
        val exporter = VideoExporter(
            context = context,
            onProgress = { p ->
                exportProgress = p; exportMessage = "Processing… ${(p * 100).toInt()}%"
            },
            onSuccess = {
                isExporting = false; exportProgress = 1f; exportMessage =
                "✅ Saved to Movies/MoodyEditor"
            },
            onError = { msg -> isExporting = false; exportMessage = "❌ $msg" }
        )
        exporter.export(
            clips = state.clips,
            fileName = "MoodyExport_${System.currentTimeMillis()}",
            adjustments = state.selectedClip?.adjustments ?: AdjustmentData()
        )
    }

    Column(modifier = Modifier
        .fillMaxSize()
        .background(Color(0xFF121212))) {

        // ═══ HEADER ═══
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .background(Color(0xFF0A0A0A))
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.Filled.ArrowBack,
                    "Back",
                    tint = Color.White
                )
            }
            Text(
                "Editor",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Text(
                "Export", color = Color(0xFF7C3AED), fontSize = 14.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .padding(end = 12.dp)
                    .pointerInput(Unit) {
                        detectTapGestures { activePanel = "export" }
                    }
            )
        }

        // ═══ PREVIEW ═══
        Box(modifier = Modifier
            .fillMaxWidth()
            .weight(1f)) {
            PreviewCanvas(
                exoPlayer = exoPlayer,
                hasVideo = state.clips.isNotEmpty(),
                rotation = state.rotation,
                aspectMode = state.aspectMode,
                textClips = state.textClips,
                stickerClips = state.stickerClips,
                currentPosMs = state.currentPosMs,
                adjustments = state.selectedClip?.adjustments ?: AdjustmentData(),
                filters = state.selectedClip?.filters
                    ?: com.moody.moodyvideoeditor.data.FilterState(),
                colorWheel = state.selectedClip?.colorWheel
                    ?: com.moody.moodyvideoeditor.data.ColorWheelState(),
                overlay = state.selectedClip?.overlay
                    ?: com.moody.moodyvideoeditor.data.OverlayState()
            )
        }

        // ═══ CONTROL BAR ═══
        ControlBar(
            onMediaClick = { picker.launch("video/*") },
            onAddVisualLayer = { viewModel.addVisualLayer() },
            onAddAudioLayer = { viewModel.addAudioLayer() }
        )

        // ═══ TIMELINE ═══
        Box(modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)) {
            Timeline(
                state = state,
                onClipTapped = { clip -> viewModel.selectClip(clip) },
                onTrackTapped = { ti, aud -> viewModel.selectTrack(ti, aud) },
                onTrimLeft = { ns -> viewModel.trimClipLeft(ns) },
                onTrimRight = { ne -> viewModel.trimClipRight(ne) },
                onTrimCommit = { viewModel.commitTrim() },
                onSeek = { t ->
                    val sel = state.selectedClip
                    if (sel != null && !sel.isAudio) {
                        val localMs = (t - sel.timelineStartMs).coerceIn(0L, sel.durationMs)
                        exoPlayer.seekTo(localMs)
                    } else exoPlayer.seekTo(t)
                },
                onMoveClip = { id, ti, aud, ms ->
                    viewModel.ensureLayerExists(ti, aud)
                    viewModel.moveClip(id, ti, aud, ms)
                },
                onDragEnd = { viewModel.commitTrim() }
            )
        }

        // ═══ PLAYBACK CONTROLS ═══
        PlaybackControls(
            isPlaying = state.isPlaying,
            isMuted = state.isMuted,
            currentPosMs = state.currentPosMs,
            totalDurationMs = state.selectedClip?.durationMs ?: state.totalDurationMs,
            hasVideo = state.clips.isNotEmpty(),
            canUndo = state.canUndo,
            canRedo = state.canRedo,
            onPlayPause = {
                val clip = state.selectedClip
                if (exoPlayer.isPlaying) exoPlayer.pause()
                else {
                    val ok = TrimPlaybackEnforcer.prepareOnPlay(exoPlayer, clip)
                    if (ok) exoPlayer.play()
                }
            },
            onSplit = { viewModel.splitCurrentClip() },
            onDelete = { viewModel.deleteCurrentClip() },
            onDuplicate = { viewModel.duplicateCurrentClip() },
            onUndo = { viewModel.undo() },
            onRedo = { viewModel.redo() },
            onMuteToggle = { viewModel.toggleMute() }
        )

        // ═══ PANEL ROUTING ═══
        Box(modifier = Modifier.fillMaxWidth()) {
            when (activePanel) {
                null -> FeatureShelf(onFeatureSelected = { activePanel = it })

                "trim" -> TrimPanel(
                    clipName = state.selectedClip?.name ?: "",
                    clipStartMs = state.selectedClip?.timelineStartMs ?: 0L,
                    clipEndMs = state.selectedClip?.timelineEndMs ?: 0L,
                    playheadMs = state.currentPosMs,
                    sourceInMs = state.selectedClip?.sourceStartMs ?: 0L,
                    sourceOutMs = state.selectedClip?.sourceEndMs ?: 0L,
                    hasClipSelected = state.selectedClip != null,
                    playheadInsideClip = state.selectedClip?.let {
                        state.currentPosMs > it.timelineStartMs && state.currentPosMs < it.timelineEndMs
                    } ?: false,
                    onClose = { activePanel = null },
                    onTrimLeft = { viewModel.trimLeft() },
                    onTrimRight = { viewModel.trimRight() },
                    onSplit = { viewModel.splitCurrentClip() }
                )

                "speed" -> SpeedPanel(
                    clipName = state.selectedClip?.name ?: "",
                    baseDurationMs = viewModel.getSelectedBaseDurationMs(),
                    currentSpeed = state.selectedClip?.speed ?: 1.0f,
                    hasClipSelected = state.selectedClip != null,
                    onSpeedChanged = { viewModel.setSpeed(it) },
                    onReset = { viewModel.resetSpeed() },
                    onClose = { activePanel = null }
                )

                "text" -> TextPanel(
                    currentText = viewModel.getSelectedTextState(),
                    hasTextClipSelected = state.selectedTextId != null,
                    onTextChanged = { viewModel.updateSelectedText(it) },
                    onCreateNew = { viewModel.createTextClip() },
                    onRemove = { viewModel.removeSelectedText() },
                    onClose = { activePanel = null }
                )

                "animations" -> AnimationsPanel(
                    currentAnimation = state.selectedText?.state?.animation ?: "none",
                    currentDuration = state.selectedText?.state?.animationDuration ?: 0.6f,
                    hasTextClipSelected = state.selectedTextId != null,
                    onAnimationSelected = { viewModel.setTextAnimation(it) },
                    onDurationChanged = {
                        val st = viewModel.getSelectedTextState()
                        viewModel.updateSelectedText(st.copy(animationDuration = it))
                    },
                    onPreview = { exoPlayer.seekTo(0) },
                    onClose = { activePanel = null }
                )

                "filters" -> FiltersPanel(
                    current = state.selectedClip?.filters
                        ?: com.moody.moodyvideoeditor.data.FilterState(),
                    hasClipSelected = state.selectedClip != null,
                    onFilterChanged = { viewModel.updateFilters(it) },
                    onResetAll = { viewModel.resetFilters() },
                    onClose = { activePanel = null }
                )

                "effects" -> EffectsPanel(
                    currentEffectKey = state.selectedClip?.effectKeys?.firstOrNull(),
                    hasClipSelected = state.selectedClip != null,
                    onPresetSelected = { preset -> viewModel.applyEffectPreset(preset.key) },
                    onRemoveEffect = {
                        val k = state.selectedClip?.effectKeys?.firstOrNull()
                        if (k != null) viewModel.removeEffectPreset(k)
                    },
                    onClose = { activePanel = null }
                )

                "adjustments" -> AdjustmentsPanel(
                    adj = state.selectedClip?.adjustments ?: AdjustmentData(),
                    onAdjChanged = { viewModel.updateSelectedAdjustments(it) },
                    onReset = { viewModel.resetAdjustments() },
                    onClose = { activePanel = null }
                )

                "wheel" -> ColorWheelPanel(
                    state = state.selectedClip?.colorWheel
                        ?: com.moody.moodyvideoeditor.data.ColorWheelState(),
                    hasClipSelected = state.selectedClip != null,
                    onStateChanged = { viewModel.updateColorWheel(it) },
                    onRemove = { viewModel.resetColorWheel() },
                    onClose = { activePanel = null }
                )

                "stickers" -> StickersPanel(
                    current = viewModel.getSelectedStickerState(),
                    hasStickerSelected = state.selectedStickerId != null,
                    playheadMs = state.currentPosMs,
                    onStickerChanged = { viewModel.updateSelectedSticker(it) },
                    onEmojiTapped = { viewModel.addOrUpdateSticker(it) },
                    onRemove = { viewModel.removeSelectedSticker() },
                    onClose = { activePanel = null }
                )

                "overlays" -> OverlaysPanel(
                    current = state.selectedClip?.overlay
                        ?: com.moody.moodyvideoeditor.data.OverlayState(),
                    hasClipSelected = state.selectedClip != null,
                    onStateChanged = { viewModel.updateOverlay(it) },
                    onRemove = { viewModel.removeOverlay() },
                    onClose = { activePanel = null }
                )

                "transitions" -> TransitionsPanel(
                    current = "none",
                    onSelected = { },
                    onClose = { activePanel = null }
                )

                "chroma" -> ChromaKeyPanel(
                    chromaColor = state.chromaColor,
                    similarity = state.chromaSimilarity,
                    smoothness = state.chromaSmoothness,
                    spill = state.chromaSpill,
                    intensity = state.chromaIntensity,
                    onColorChanged = { viewModel.setChromaColor(it) },
                    onSimilarityChanged = { viewModel.setChromaSimilarity(it) },
                    onSmoothnessChanged = { viewModel.setChromaSmoothness(it) },
                    onSpillChanged = { viewModel.setChromaSpill(it) },
                    onIntensityChanged = { viewModel.setChromaIntensity(it) },
                    onClose = { activePanel = null }
                )

                "transform" -> TransformPanel(
                    currentScale = state.selectedClip?.scale ?: 1f,
                    currentRotation = state.selectedClip?.rotation ?: 0f,
                    currentOffsetX = state.selectedClip?.offsetX ?: 0f,
                    currentOffsetY = state.selectedClip?.offsetY ?: 0f,
                    onScaleChanged = { viewModel.setClipScale(it) },
                    onRotationChanged = { viewModel.setClipRotation(it) },
                    onOffsetChanged = { x, y -> viewModel.setClipOffset(x, y) },
                    onReset = {
                        viewModel.setClipScale(1f)
                        viewModel.setClipRotation(0f)
                        viewModel.setClipOffset(0f, 0f)
                    },
                    onClose = { activePanel = null }
                )

                "crop" -> CropPanel(
                    cropL = state.selectedClip?.cropL ?: 0f,
                    cropR = state.selectedClip?.cropR ?: 0f,
                    cropT = state.selectedClip?.cropT ?: 0f,
                    cropB = state.selectedClip?.cropB ?: 0f,
                    onCropChanged = { l, r, t, b -> viewModel.setCrop(l, r, t, b) },
                    onReset = { viewModel.setCrop(0f, 0f, 0f, 0f) },
                    onClose = { activePanel = null }
                )

                "volume" -> VolumePanel(
                    volume = state.volume,
                    isMuted = state.isMuted,
                    onVolumeChanged = { viewModel.setVolume(it) },
                    onMuteToggle = { viewModel.toggleMute() },
                    onClose = { activePanel = null }
                )

                "audiofx" -> AudioFxPanel(
                    current = state.audioFx,
                    onSelected = { viewModel.setAudioFx(it) },
                    onClose = { activePanel = null }
                )

                "soundfx" -> SoundFxPanel(
                    current = state.soundFx,
                    onSelected = { viewModel.setSoundFx(it) },
                    onClose = { activePanel = null }
                )

                "music" -> MusicPanel(onClose = { activePanel = null })
                "motion" -> MotionPanel(
                    current = "none",
                    onSelected = { },
                    onClose = { activePanel = null })

                "freeze" -> FreezePanel(onClose = { activePanel = null })
                "beats" -> BeatsPanel(
                    detected = state.beatsDetected,
                    count = state.beatsCount,
                    filter = state.beatsFilter,
                    onDetect = { viewModel.setBeats(120, it) },
                    onClear = { viewModel.clearBeats() },
                    onClose = { activePanel = null }
                )

                "ratio" -> AspectRatioPanel(
                    currentRatio = state.aspectRatio,
                    onRatioSelected = { viewModel.setAspectRatio(it) },
                    onClose = { activePanel = null }
                )

                "duplicate" -> {
                    viewModel.duplicateCurrentClip(); activePanel = null
                }

                "delete" -> {
                    viewModel.deleteCurrentClip(); activePanel = null
                }

                "export" -> ExportPanel(
                    isExporting = isExporting,
                    exportProgress = exportProgress,
                    exportMessage = exportMessage,
                    onStartExport = { startExport() },
                    onClose = { activePanel = null }
                )

                else -> FeatureShelf(onFeatureSelected = { activePanel = it })
            }
        }
    }
}