@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.moody.moodyvideoeditor.ui.screens
import com.moody.moodyvideoeditor.data.AdjustmentData
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.moody.moodyvideoeditor.ui.components.ControlBar
import com.moody.moodyvideoeditor.ui.components.FeatureShelf
import com.moody.moodyvideoeditor.ui.components.PlaybackControls
import com.moody.moodyvideoeditor.ui.components.PreviewCanvas
import com.moody.moodyvideoeditor.ui.components.Timeline
import com.moody.moodyvideoeditor.ui.features.AdjustmentsPanel
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
import com.moody.moodyvideoeditor.ui.features.TextAnimationsPanel
import com.moody.moodyvideoeditor.ui.features.TextPanel
import com.moody.moodyvideoeditor.ui.features.TransformPanel
import com.moody.moodyvideoeditor.ui.features.TransitionsPanel
import com.moody.moodyvideoeditor.ui.features.TrimPanel
import com.moody.moodyvideoeditor.ui.features.VolumePanel
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

    // Apply speed + volume
    LaunchedEffect(state.speed) {
        exoPlayer.setPlaybackSpeed(state.speed)
    }
    LaunchedEffect(state.volume, state.isMuted) {
        exoPlayer.volume = if (state.isMuted) 0f else state.volume
    }

    // Load clip when selected
    LaunchedEffect(state.selectedClipId) {
        val clip = state.selectedClip ?: return@LaunchedEffect
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
        exoPlayer.seekTo(0)
        exoPlayer.setPlaybackSpeed(state.speed)
        exoPlayer.volume = if (state.isMuted) 0f else state.volume
    }

    // Tick loop
    LaunchedEffect(exoPlayer) {
        while (true) {
            viewModel.setCurrentPos(exoPlayer.currentPosition)
            viewModel.setPlaying(exoPlayer.isPlaying)
            delay(50)
        }
    }

    // Media picker
    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { pendingUri = it }
    }

    LaunchedEffect(pendingUri) {
        val uri = pendingUri ?: return@LaunchedEffect
        try {
            val result = withContext(Dispatchers.IO) {
                Pair(VideoUtils.getFileName(context, uri), VideoUtils.getVideoDuration(context, uri))
            }
            viewModel.addClip(uri, result.first, result.second)
        } catch (_: Exception) {
        } finally {
            pendingUri = null
        }
    }

    fun startExport() {
        if (state.clips.isEmpty()) {
            exportMessage = "❌ No clips to export"
            return
        }
        isExporting = true
        exportProgress = 0f
        exportMessage = "Starting FFmpeg…"

        val exporter = VideoExporter(
            context = context,
            onProgress = { p ->
                exportProgress = p
                exportMessage = "Processing… ${(p * 100).toInt()}%"
            },
            onSuccess = {
                isExporting = false
                exportProgress = 1f
                exportMessage = "✅ Saved to Movies/MoodyEditor"
            },
            onError = { msg ->
                isExporting = false
                exportMessage = "❌ $msg"
            }
        )
       exporter.export(
    clips = state.clips,
    fileName = "MoodyExport_${System.currentTimeMillis()}",
    adjustments = state.selectedClip?.adjustments ?: com.moody.moodyvideoeditor.data.AdjustmentData()
)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
    ) {
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
                Icon(Icons.Filled.ArrowBack, "Back", tint = Color.White)
            }
            Text(
                "Editor",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Text(
                "Export",
                color = Color(0xFF7C3AED),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .padding(end = 12.dp)
                    .pointerInput(Unit) {
                        detectTapGestures { activePanel = "export" }
                    }
            )
        }

        // ═══ PREVIEW ═══
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            PreviewCanvas(
    exoPlayer = exoPlayer,
    hasVideo = state.clips.isNotEmpty(),
    rotation = state.rotation,
    aspectMode = state.aspectMode,
    text = state.text,
    textColor = Color(state.textColor),
    textSize = state.textSize,
    sticker = state.sticker,
    stickerX = state.stickerX,
    stickerY = state.stickerY,
    adjustments = state.selectedClip?.adjustments ?: AdjustmentData()
)
        }

        // ═══ CONTROL BAR ═══
        ControlBar(
            onMediaClick = { picker.launch("video/*") },
            onAddVisualLayer = { viewModel.addVisualLayer() },
            onAddAudioLayer = { viewModel.addAudioLayer() }
        )

        // ═══ TIMELINE ═══
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
        ) {
            Timeline(
                state = state,
                onClipTapped = { clip ->
                    viewModel.selectClip(clip)
                    exoPlayer.seekTo(clip.sourceStartMs)
                }
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
                if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
            },
            onSplit = { viewModel.splitCurrentClip() },
            onDelete = { viewModel.deleteCurrentClip() },
            onDuplicate = { viewModel.duplicateCurrentClip() },
            onUndo = { viewModel.undo() },
            onRedo = { viewModel.redo() },
            onMuteToggle = { viewModel.toggleMute() }
        )

        // ═══ FEATURE PANEL / SHELF ═══
        Box(modifier = Modifier.fillMaxWidth()) {
            when (activePanel) {
                null -> FeatureShelf(onFeatureSelected = { key -> activePanel = key })

                "trim" -> TrimPanel(
                    onClose = { activePanel = null },
                    onTrimLeft = { viewModel.trimLeft() },
                    onTrimRight = { viewModel.trimRight() },
                    onSplit = { viewModel.splitCurrentClip() }
                )

                "speed" -> SpeedPanel(
                    currentSpeed = state.speed,
                    onSpeedChanged = { viewModel.setSpeed(it) },
                    onClose = { activePanel = null }
                )

                "text" -> TextPanel(
                    currentText = state.text,
                    currentColor = Color(state.textColor),
                    currentSize = state.textSize,
                    onTextChanged = { t, c, s ->
                        viewModel.setText(t)
                        viewModel.setTextColor(c.toArgb())
                        viewModel.setTextSize(s)
                    },
                    onClear = { viewModel.setText("") },
                    onClose = { activePanel = null }
                )

                "animations" -> TextAnimationsPanel(
                    current = state.textAnimation,
                    onSelected = { viewModel.setTextAnimation(it) },
                    onClose = { activePanel = null }
                )

                "filters" -> FiltersPanel(
                    currentFilter = state.filter,
                    onFilterSelected = { viewModel.setFilter(it) },
                    onClose = { activePanel = null }
                )

                "effects" -> EffectsPanel(
                    currentEffect = state.effect,
                    onEffectSelected = { viewModel.setEffect(it) },
                    onClose = { activePanel = null }
                )

                "adjustments" -> AdjustmentsPanel(
    adj = state.selectedClip?.adjustments ?: AdjustmentData(),
    onAdjChanged = { newAdj ->
        // Update selected clip's adjustments
        viewModel.updateSelectedAdjustments(newAdj)
    },
    onReset = { viewModel.resetAdjustments() },
    onClose = { activePanel = null }
)

               "wheel" -> ColorWheelPanel(
    shadowsHue = state.shadowsHue,
    shadowsSat = state.shadowsSat,
    midtonesHue = state.midtonesHue,
    midtonesSat = state.midtonesSat,
    highlightsHue = state.highlightsHue,
    highlightsSat = state.highlightsSat,
    hdrWhite = state.hdrWhite,
    onShadowsChanged = { h, s -> viewModel.setShadowsHue(h, s) },
    onMidtonesChanged = { h, s -> viewModel.setMidtonesHue(h, s) },
    onHighlightsChanged = { h, s -> viewModel.setHighlightsHue(h, s) },
    onHdrChanged = { viewModel.setHdrWhite(it) },
    onClose = { activePanel = null }
)
                "stickers" -> StickersPanel(
                    currentSticker = state.sticker,
                    onStickerSelected = { viewModel.setSticker(it) },
                    onClear = { viewModel.setSticker("") },
                    onClose = { activePanel = null }
                )

                "overlays" -> OverlaysPanel(
                    currentOverlay = state.overlay,
                    onOverlaySelected = { viewModel.setOverlay(it) },
                    onClose = { activePanel = null }
                )

                "transitions" -> TransitionsPanel(
                    current = state.transition,
                    onSelected = { viewModel.setTransition(it) },
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

                "beats" -> BeatsPanel(
                    detected = state.beatsDetected,
                    count = state.beatsCount,
                    filter = state.beatsFilter,
                    onDetect = { viewModel.setBeats(120, it) },
                    onClear = { viewModel.clearBeats() },
                    onClose = { activePanel = null }
                )

                "motion" -> MotionPanel(
                    current = state.motion,
                    onSelected = { viewModel.setMotion(it) },
                    onClose = { activePanel = null }
                )

                "freeze" -> FreezePanel(onClose = { activePanel = null })

                "ratio" -> AspectRatioPanel(
                    currentRatio = state.aspectRatio,
                    onRatioSelected = { viewModel.setAspectRatio(it) },
                    onClose = { activePanel = null }
                )

                "duplicate" -> {
                    viewModel.duplicateCurrentClip()
                    activePanel = null
                }

                "delete" -> {
                    viewModel.deleteCurrentClip()
                    activePanel = null
                }

                "export" -> ExportPanel(
                    isExporting = isExporting,
                    exportProgress = exportProgress,
                    exportMessage = exportMessage,
                    onStartExport = { startExport() },
                    onClose = { activePanel = null }
                )

                else -> FeatureShelf(onFeatureSelected = { key -> activePanel = key })
            }
        }
    }
}