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
import com.moody.moodyvideoeditor.ui.features.*
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
    DisposableEffect(Unit) { onDispose { exoPlayer.release() } }

    LaunchedEffect(state.speed) { exoPlayer.setPlaybackSpeed(state.speed) }
    LaunchedEffect(state.volume, state.isMuted) {
        exoPlayer.volume = if (state.isMuted) 0f else state.volume
    }

    LaunchedEffect(state.currentIndex, state.clips.size) {
        val clip = state.currentClip ?: return@LaunchedEffect
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

    LaunchedEffect(exoPlayer) {
        while (true) {
            viewModel.setCurrentPos(exoPlayer.currentPosition)
            viewModel.setPlaying(exoPlayer.isPlaying)
            if (!exoPlayer.isPlaying && exoPlayer.playbackState == ExoPlayer.STATE_ENDED) {
                val next = state.currentIndex + 1
                if (next < state.clips.size) {
                    viewModel.setCurrentIndex(next)
                    exoPlayer.play()
                }
            }
            delay(50)
        }
    }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? -> uri?.let { pendingUri = it } }

    LaunchedEffect(pendingUri) {
        val uri = pendingUri ?: return@LaunchedEffect
        try {
            val result = withContext(Dispatchers.IO) {
                Pair(
                    VideoUtils.getFileName(context, uri),
                    VideoUtils.getVideoDuration(context, uri)
                )
            }
            viewModel.addClip(uri, result.first, result.second)
        } catch (_: Exception) {
        } finally {
            pendingUri = null
        }
    }

    fun startExport() {
        if (state.clips.isEmpty()) {
            exportMessage = "❌ No clips to export"; return
        }
        isExporting = true
        exportProgress = 0f
        exportMessage = "Starting…"
        val exporter = VideoExporter(
            context = context,
            onProgress = { p -> exportProgress = p },
            onSuccess = { isExporting = false; exportProgress = 1f; exportMessage = "✅ Saved" },
            onError = { msg -> isExporting = false; exportMessage = "❌ $msg" }
        )
        exporter.export(state.clips, "MoodyExport_${System.currentTimeMillis()}")
    }

    Column(Modifier
        .fillMaxSize()
        .background(Color(0xFF121212))) {
        // HEADER
        Row(
            Modifier
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
                    .pointerInput(Unit) { detectTapGestures { activePanel = "export" } }
            )
        }

        // PREVIEW
        Box(Modifier
            .fillMaxWidth()
            .weight(1f)) {
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
                stickerY = state.stickerY
            )
        }

        ControlBar(onMediaClick = { picker.launch("video/*") })

        Box(Modifier
            .fillMaxWidth()
            .height(140.dp)) {
            Timeline(
                clips = state.clips,
                currentIndex = state.currentIndex,
                currentPosMs = state.currentPosMs,
                onClipTapped = { idx, pos ->
                    viewModel.setCurrentIndex(idx)
                    viewModel.setCurrentPos(pos)
                    exoPlayer.seekTo(pos)
                }
            )
        }

        PlaybackControls(
            isPlaying = state.isPlaying, isMuted = state.isMuted,
            currentPosMs = state.currentPosMs,
            totalDurationMs = state.clips.getOrNull(state.currentIndex)?.durationMs ?: 0L,
            hasVideo = state.clips.isNotEmpty(),
            canUndo = state.canUndo, canRedo = state.canRedo,
            onPlayPause = { if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play() },
            onSplit = { viewModel.splitCurrentClip() },
            onDelete = { viewModel.deleteCurrentClip() },
            onDuplicate = { viewModel.duplicateCurrentClip() },
            onUndo = { viewModel.undo() },
            onRedo = { viewModel.redo() },
            onMuteToggle = { viewModel.toggleMute() }
        )

        // PANEL AREA
        Box(Modifier.fillMaxWidth()) {
            when (activePanel) {
                null -> FeatureShelf(onFeatureSelected = { activePanel = it })
                "trim" -> TrimPanel(
                    { activePanel = null },
                    { viewModel.trimLeft() },
                    { viewModel.trimRight() },
                    { viewModel.splitCurrentClip() })

                "speed" -> SpeedPanel(
                    state.speed,
                    { viewModel.setSpeed(it) },
                    { activePanel = null })

                "text" -> TextPanel(
                    state.text, Color(state.textColor), state.textSize,
                    { t, c, s ->
                        viewModel.setText(t); viewModel.setTextColor(c.toArgb()); viewModel.setTextSize(
                        s
                    )
                    },
                    { viewModel.setText("") }, { activePanel = null }
                )

                "animations" -> TextAnimationsPanel(
                    state.textAnimation,
                    { viewModel.setTextAnimation(it) },
                    { activePanel = null })

                "filters" -> FiltersPanel(
                    state.filter,
                    { viewModel.setFilter(it) },
                    { activePanel = null })

                "effects" -> EffectsPanel(
                    state.effect,
                    { viewModel.setEffect(it) },
                    { activePanel = null })

                "adjustments" -> AdjustmentsPanel(
                    state.brightness,
                    state.contrast,
                    state.saturation,
                    state.exposure,
                    state.temperature,
                    state.tint,
                    state.vignette,
                    state.grain,
                    { viewModel.setBrightness(it) },
                    { viewModel.setContrast(it) },
                    { viewModel.setSaturation(it) },
                    { viewModel.setExposure(it) },
                    { viewModel.setTemperature(it) },
                    { viewModel.setTint(it) },
                    { viewModel.setVignette(it) },
                    { viewModel.setGrain(it) },
                    { viewModel.resetAdjustments() },
                    { activePanel = null }
                )

                "wheel" -> ColorWheelPanel(
                    state.shadowsHue,
                    state.shadowsSat,
                    state.midtonesHue,
                    state.midtonesSat,
                    state.highlightsHue,
                    state.highlightsSat,
                    state.hdrWhite,
                    { h, s -> viewModel.setShadows(h, s) },
                    { h, s -> viewModel.setMidtones(h, s) },
                    { h, s -> viewModel.setHighlights(h, s) },
                    { viewModel.setHdrWhite(it) },
                    { activePanel = null }
                )

                "stickers" -> StickersPanel(
                    state.sticker,
                    { viewModel.setSticker(it) },
                    { viewModel.setSticker("") },
                    { activePanel = null })

                "overlays" -> OverlaysPanel(
                    state.overlay,
                    { viewModel.setOverlay(it) },
                    { activePanel = null })

                "transitions" -> TransitionsPanel(
                    state.transition,
                    { viewModel.setTransition(it) },
                    { activePanel = null })

                "chroma" -> ChromaKeyPanel(
                    state.chromaColor, state.chromaSimilarity, state.chromaSmoothness,
                    state.chromaSpill, state.chromaIntensity,
                    { viewModel.setChromaColor(it) }, { viewModel.setChromaSimilarity(it) },
                    { viewModel.setChromaSmoothness(it) }, { viewModel.setChromaSpill(it) },
                    { viewModel.setChromaIntensity(it) }, { activePanel = null }
                )

                "transform" -> TransformPanel(
                    state.currentClip?.scale ?: 1f, state.currentClip?.rotation ?: 0f,
                    state.currentClip?.offsetX ?: 0f, state.currentClip?.offsetY ?: 0f,
                    { viewModel.setClipScale(it) }, { viewModel.setClipRotation(it) },
                    { x, y -> viewModel.setClipOffset(x, y) },
                    {
                        viewModel.setClipScale(1f); viewModel.setClipRotation(0f); viewModel.setClipOffset(
                        0f,
                        0f
                    )
                    },
                    { activePanel = null }
                )

                "crop" -> CropPanel(
                    state.currentClip?.cropL ?: 0f, state.currentClip?.cropR ?: 0f,
                    state.currentClip?.cropT ?: 0f, state.currentClip?.cropB ?: 0f,
                    { l, r, t, b -> viewModel.setCrop(l, r, t, b) },
                    { viewModel.setCrop(0f, 0f, 0f, 0f) }, { activePanel = null }
                )

                "volume" -> VolumePanel(
                    state.volume,
                    state.isMuted,
                    { viewModel.setVolume(it) },
                    { viewModel.toggleMute() },
                    { activePanel = null })

                "audiofx" -> AudioFxPanel(
                    state.audioFx,
                    { viewModel.setAudioFx(it) },
                    { activePanel = null })

                "soundfx" -> SoundFxPanel(
                    state.soundFx,
                    { viewModel.setSoundFx(it) },
                    { activePanel = null })

                "music" -> MusicPanel({ activePanel = null })
                "beats" -> BeatsPanel(
                    state.beatsDetected,
                    state.beatsCount,
                    state.beatsFilter,
                    { viewModel.setBeats(120, it) },
                    { viewModel.clearBeats() },
                    { activePanel = null })

                "motion" -> MotionPanel(
                    state.motion,
                    { viewModel.setMotion(it) },
                    { activePanel = null })

                "freeze" -> FreezePanel({ activePanel = null })
                "ratio" -> AspectRatioPanel(
                    state.aspectRatio,
                    { viewModel.setAspectRatio(it) },
                    { activePanel = null })

                "duplicate" -> {
                    viewModel.duplicateCurrentClip(); activePanel = null
                }

                "delete" -> {
                    viewModel.deleteCurrentClip(); activePanel = null
                }

                "export" -> ExportPanel(
                    isExporting,
                    exportProgress,
                    exportMessage,
                    { startExport() },
                    { activePanel = null })

                else -> FeatureShelf(onFeatureSelected = { activePanel = it })
            }
        }
    }
}