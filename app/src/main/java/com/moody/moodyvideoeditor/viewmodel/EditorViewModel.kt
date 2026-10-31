package com.moody.moodyvideoeditor.viewmodel

import com.moody.moodyvideoeditor.data.OverlayConfig
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import com.moody.moodyvideoeditor.data.AdjustmentData
import com.moody.moodyvideoeditor.data.BeatsState
import com.moody.moodyvideoeditor.data.ColorMatteDefaults
import com.moody.moodyvideoeditor.data.ColorMatteMode
import com.moody.moodyvideoeditor.data.ColorMatteStyle
import com.moody.moodyvideoeditor.data.ChromaKeyState
import com.moody.moodyvideoeditor.data.ColorWheelState
import com.moody.moodyvideoeditor.data.EditorClip
import com.moody.moodyvideoeditor.data.EditorState
import com.moody.moodyvideoeditor.data.EffectLibrary
import com.moody.moodyvideoeditor.data.EffectState
import com.moody.moodyvideoeditor.data.FilterState
import com.moody.moodyvideoeditor.data.Keyframe
import com.moody.moodyvideoeditor.data.KeyframeLibrary
import com.moody.moodyvideoeditor.data.LayerShadowState
import com.moody.moodyvideoeditor.data.MaskAnimatableProps
import com.moody.moodyvideoeditor.data.MaskLibrary
import com.moody.moodyvideoeditor.data.MaskPoint
import com.moody.moodyvideoeditor.data.MaskState
import com.moody.moodyvideoeditor.data.MaskType
import com.moody.moodyvideoeditor.data.OverlayState
import com.moody.moodyvideoeditor.data.RatioState
import com.moody.moodyvideoeditor.data.SpotlightState
import com.moody.moodyvideoeditor.data.TextState
import com.moody.moodyvideoeditor.data.TransitionLibrary
import com.moody.moodyvideoeditor.data.TransitionState
import com.moody.moodyvideoeditor.data.VisualizerState
import com.moody.moodyvideoeditor.service.ExportService
import com.moody.moodyvideoeditor.service.ExportServiceConfig
import com.moody.moodyvideoeditor.service.ExportStateHolder
import com.moody.moodyvideoeditor.service.ExportUiState
import com.moody.moodyvideoeditor.utils.BeatsEngine
import com.moody.moodyvideoeditor.utils.DeleteEngine
import com.moody.moodyvideoeditor.utils.DuplicateEngine
import com.moody.moodyvideoeditor.utils.FreezeEngine
import com.moody.moodyvideoeditor.utils.HistoryManager
import com.moody.moodyvideoeditor.utils.KeyframeStore
import com.moody.moodyvideoeditor.utils.TypographyAnimationPresetLibrary
import com.moody.moodyvideoeditor.utils.MaskEngine
import com.moody.moodyvideoeditor.utils.SettingsConsumer
import com.moody.moodyvideoeditor.utils.SpotlightAnimation
import com.moody.moodyvideoeditor.utils.SpeedEngine
import com.moody.moodyvideoeditor.utils.TimelineEngine
import com.moody.moodyvideoeditor.utils.TimelineTools
import com.moody.moodyvideoeditor.utils.TimelineZoom
import com.moody.moodyvideoeditor.utils.TransformApplier
import com.moody.moodyvideoeditor.utils.TypographyTemplates
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.viewModelScope
import java.util.UUID
import kotlin.math.abs
import kotlin.math.cos

import kotlin.math.sin

data class TimedTextClip(
    val textState: TextState,
    val startMs: Long,
    val endMs: Long
)

private const val DEFAULT_APPLIED_LAYER_DURATION_MS = 3000L

class EditorViewModel : ViewModel() {

    private val _state = MutableStateFlow(EditorState())
    val state: StateFlow<EditorState> = _state.asStateFlow()

    private val history = HistoryManager()
    private var clipboardClips: List<EditorClip> = emptyList()
    private var clipboardIsCut = false

    val exportState: StateFlow<ExportUiState> = ExportStateHolder.state

    // ═══════════════════════════════════════════════════════════
    //  EXPORT
    // ═══════════════════════════════════════════════════════════

    fun startExport(context: Context, config: ExportUiStateBundle) {
        try {
            ExportStateHolder.reset()

            val serviceConfig = ExportServiceConfig(
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
                folderUri = config.folderUri,
                startMs = config.startMs,
                endMs = config.endMs,
                totalDurationMs = config.totalDurationMs,
                clipsJson = ""
            )

            ExportClipsHolder.clips = config.clips

            val startIntent = ExportService.buildStartIntent(context, serviceConfig)

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(context, startIntent)
            } else {
                context.startService(startIntent)
            }
        } catch (t: Throwable) {
            android.util.Log.e("EditorViewModel", "startExport failed", t)
            ExportStateHolder.fail("Could not start export: ${t.message}")
        }
    }

    fun cancelExport(context: Context) {
        try {
            val cancelIntent = ExportService.buildCancelIntent(context)
            context.startService(cancelIntent)
        } catch (t: Throwable) {
            android.util.Log.e("EditorViewModel", "cancelExport failed", t)
        }
    }

    fun clearExportState() {
        ExportStateHolder.reset()
    }

    private var currentProjectId: String? = null
    private var currentProjectName: String = "Untitled"

    fun getProjectId(): String? = currentProjectId
    fun getProjectName(): String = currentProjectName

    fun setProjectMeta(id: String, name: String) {
        currentProjectId = id
        currentProjectName = name
    }

    fun createNewProject(name: String = "Untitled Project"): String {
        val id = UUID.randomUUID().toString()
        currentProjectId = id
        currentProjectName = name
        history.clear()
        _state.value = EditorState(timelineLockedMs = 0L) // 🆕 Reset

        if (SettingsConsumer.freeLayerEnabled) {
            _state.update {
                it.copy(
                    visualLayerCount = maxOf(it.visualLayerCount, 3),
                    audioLayerCount = maxOf(it.audioLayerCount, 2)
                )
            }
        }

        updateHistoryFlags()
        return id
    }

    fun saveCurrentProject(context: Context): Boolean {
        val id = currentProjectId ?: return false
        val s = _state.value
        val now = System.currentTimeMillis()

        val meta = com.moody.moodyvideoeditor.data.ProjectMeta(
            id = id,
            name = currentProjectName,
            createdAt = now,
            updatedAt = now,
            thumbnailPath = null,
            clipCount = s.clips.size,
            durationMs = s.totalDurationMs
        )

        val existing = com.moody.moodyvideoeditor.data.ProjectRepository
            .listProjects(context)
            .firstOrNull { it.id == id }
        val finalMeta = if (existing != null) {
            meta.copy(createdAt = existing.createdAt)
        } else meta

        return com.moody.moodyvideoeditor.data.ProjectRepository
            .saveProject(context, finalMeta, s)
    }

    fun loadProject(context: Context, projectId: String): Boolean {
        val loaded = com.moody.moodyvideoeditor.data.ProjectRepository
            .loadProject(context, projectId) ?: return false

        val fixedClips = loaded.clips.map { clip ->
            if (clip.isTextClip ||
                clip.type.startsWith("image/")
            ) {
                clip.copy(sourceTotalMs = Long.MAX_VALUE)
            } else clip
        }
        val fixedState = loaded.copy(clips = fixedClips)

        val meta = com.moody.moodyvideoeditor.data.ProjectRepository
            .listProjects(context)
            .firstOrNull { it.id == projectId }

        currentProjectId = projectId
        currentProjectName = meta?.name ?: "Untitled"

        history.clear()
        _state.value = fixedState
        updateHistoryFlags()
        return true
    }

    fun renameCurrentProject(context: Context, newName: String) {
        val id = currentProjectId ?: return
        currentProjectName = newName
        com.moody.moodyvideoeditor.data.ProjectRepository
            .renameProject(context, id, newName)
    }

    // ═══════════════════════════════════════════════════════════
    //  TEMPLATE LOADING (🆕 ADDED)
    // ═══════════════════════════════════════════════════════════

    fun loadFromTemplate(context: Context, templateId: String): Boolean {
        val loaded = com.moody.moodyvideoeditor.data.SavedTemplateRepository
            .load(context, templateId) ?: return false

        val fixedClips = loaded.clips.map { clip ->
            if (clip.isTextClip ||
                clip.isEffectClip || clip.isAdjustmentClip ||
                clip.isOverlayClip ||
                clip.isColorMatteClip || clip.isFilterLayerClip ||
                clip.isVisualizerClip
            ) {
                clip
            } else if (clip.type.startsWith("image/") &&
                clip.uri.toString().isBlank()
            ) {
                clip.copy(sourceTotalMs = Long.MAX_VALUE)
            } else clip
        }

        val fixedState = loaded.copy(clips = fixedClips)

        history.clear()
        _state.value = fixedState
        updateHistoryFlags()
        return true
    }

    // ═══════════════════════════════════════════════════════════
    //  REPLACE CLIP URI (🆕 ADDED)
    // ═══════════════════════════════════════════════════════════

    fun replaceClipUri(clipId: String, newUri: Uri) {
        val clip = _state.value.clips.firstOrNull { it.id == clipId } ?: return
        pushHistory()
        updateClipDirect(clipId) {
            it.copy(uri = newUri, sourceTotalMs = Long.MAX_VALUE)
        }
        updateHistoryFlags()
    }

    private fun pushHistory() {
        history.push(_state.value.clips)
        updateHistoryFlags()
    }

    private fun updateHistoryFlags() {
        _state.update {
            it.copy(canUndo = history.canUndo(), canRedo = history.canRedo())
        }
    }

    private fun cleanupStaleBeats() {
        val s = _state.value
        val hasSourceAudio = s.clips.any {
            it.isAudio && !it.isAudioEffectClip &&
                    it.uri.toString().isNotBlank() &&
                    it.uri != Uri.EMPTY
        }
        val hasVisualizerWithBeats = s.clips.any {
            it.isVisualizerClip &&
                    (it.visualizer?.beatTimesMs?.isNotEmpty() == true)
        }

        if (!hasSourceAudio && !hasVisualizerWithBeats) {
            _state.update {
                it.copy(
                    beatsDetected = false,
                    beatsCount = 0,
                    beatsFilter = "all",
                    beatTimesMs = emptyList(),
                    beatStrengths = emptyList()
                )
            }
        }
    }

    private fun updateClipDirect(clipId: String, transform: (EditorClip) -> EditorClip) {
        val list = _state.value.clips.toMutableList()
        val idx = list.indexOfFirst { it.id == clipId }
        if (idx < 0) return
        list[idx] = transform(list[idx])
        _state.update { it.copy(clips = list) }
    }

    private fun forEachSelectedClip(transform: (EditorClip) -> EditorClip) {
        val s = _state.value
        val targetIds: Set<String> = if (s.multiSelectedIds.isNotEmpty()) {
            s.multiSelectedIds
        } else {
            setOfNotNull(s.selectedClipId)
        }
        if (targetIds.isEmpty()) return

        val list = s.clips.toMutableList()
        var changed = false
        for (i in list.indices) {
            if (list[i].id in targetIds) {
                val updated = transform(list[i])
                if (updated != list[i]) {
                    list[i] = updated
                    changed = true
                }
            }
        }
        if (changed) {
            _state.update { it.copy(clips = list) }
        }
    }

    private var dragBaseline: List<EditorClip>? = null
    private var keyframeGraphBaseline: List<EditorClip>? = null

    fun beginDrag() { dragBaseline = _state.value.clips }

    fun cancelDrag() {
        dragBaseline?.let { baseline ->
            _state.update { s -> s.copy(clips = baseline) }
        }
        dragBaseline = null
        updateHistoryFlags()
    }

    fun commitDrag() {
        val baseline = dragBaseline
        if (baseline != null && baseline != _state.value.clips) {
            history.push(baseline)
        }
        dragBaseline = null
        updateHistoryFlags()
    }

    private data class ClipSnapshot(
        val posX: Float, val posY: Float,
        val scale: Float, val rotation: Float
    )

    private var groupGestureBaseline: Map<String, ClipSnapshot>? = null
    private var groupGestureHistoryBaseline: List<EditorClip>? = null

    fun beginGroupGesture() {
        val s = _state.value
        groupGestureHistoryBaseline = s.clips.toList()
        val ids: Set<String> = if (s.multiSelectedIds.isNotEmpty()) {
            s.multiSelectedIds
        } else {
            setOfNotNull(s.selectedClipId)
        }
        val map = mutableMapOf<String, ClipSnapshot>()
        s.clips.forEach { c ->
            if (c.id in ids) {
                val pos = getClipPos(c)
                map[c.id] = ClipSnapshot(
                    posX = pos.first, posY = pos.second,
                    scale = getClipScale(c), rotation = getClipRotation(c)
                )
            }
        }
        groupGestureBaseline = map
    }

    fun endGroupGesture() {
        groupGestureBaseline = null
        val baseline = groupGestureHistoryBaseline
        if (baseline != null && baseline != _state.value.clips) {
            history.push(baseline)
        }
        groupGestureHistoryBaseline = null
        updateHistoryFlags()
    }

    fun applyGroupTransform(
        anchorId: String, newX: Float, newY: Float,
        newScale: Float, newRotation: Float
    ) {
        val baseline = groupGestureBaseline
        val anchorBase = baseline?.get(anchorId)

        if (baseline == null || anchorBase == null) {
            val s = _state.value
            val isMulti = s.multiSelectedIds.size > 1 && anchorId in s.multiSelectedIds
            if (isMulti) {
                updateSelectedPositionBulk(anchorId, newX, newY)
                updateSelectedTransformBulk(anchorId, newScale, newRotation)
            } else {
                val clip = s.clips.firstOrNull { it.id == anchorId } ?: return
                val currentTimeSec = KeyframeStore.clipLocalTimeSeconds(
                    s.currentPosMs, clip.timelineStartMs, clip.durationMs
                )
                updateClipDirect(anchorId) { current ->
                    setClipTransformWithAutoKeyframes(
                        current, newX, newY, newScale, newRotation, currentTimeSec
                    )
                }
            }
            return
        }

        val deltaScale = if (abs(anchorBase.scale) < 0.001f) 1f
        else (newScale / anchorBase.scale)
        val deltaRot = newRotation - anchorBase.rotation

        var cx = 0f
        var cy = 0f
        var n = 0
        baseline.forEach { (_, snap) -> cx += snap.posX; cy += snap.posY; n++ }
        if (n == 0) return
        cx /= n; cy /= n

        val cosR = cos(deltaRot * Math.PI / 180.0).toFloat()
        val sinR = sin(deltaRot * Math.PI / 180.0).toFloat()

        val axRel = anchorBase.posX - cx
        val ayRel = anchorBase.posY - cy
        val aRotX = axRel * cosR - ayRel * sinR
        val aRotY = axRel * sinR + ayRel * cosR
        val aScaledX = aRotX * deltaScale
        val aScaledY = aRotY * deltaScale
        val anchorAfterX = cx + aScaledX
        val anchorAfterY = cy + aScaledY

        val tx = newX - anchorAfterX
        val ty = newY - anchorAfterY

        val list = _state.value.clips.toMutableList()
        for (i in list.indices) {
            val snap = baseline[list[i].id] ?: continue
            val relX = snap.posX - cx
            val relY = snap.posY - cy
            val rotX = relX * cosR - relY * sinR
            val rotY = relX * sinR + relY * cosR
            val scaledX = rotX * deltaScale
            val scaledY = rotY * deltaScale
            val finalX = cx + scaledX + tx
            val finalY = cy + scaledY + ty
            val newClipScale = snap.scale * deltaScale
            val newClipRot = snap.rotation + deltaRot

            val clip = list[i]
            val currentTimeSec = KeyframeStore.clipLocalTimeSeconds(
                _state.value.currentPosMs, clip.timelineStartMs, clip.durationMs
            )
            list[i] = setClipTransformWithAutoKeyframes(
                clip, finalX, finalY, newClipScale, newClipRot, currentTimeSec
            )
        }
        _state.update { it.copy(clips = list) }
    }

    fun addVisualLayer() =
        _state.update { it.copy(visualLayerCount = it.visualLayerCount + 1) }

    fun addAudioLayer() =
        _state.update { it.copy(audioLayerCount = it.audioLayerCount + 1) }

    fun selectTrack(trackIndex: Int, isAudio: Boolean) {
        _state.update {
            it.copy(
                selectedTrackIndex = trackIndex,
                selectedIsAudio = isAudio,
                selectedClipId = null,
                multiSelectedIds = emptySet()
            )
        }
    }

    fun ensureLayerExists(trackIndex: Int, isAudio: Boolean) {
        _state.update { s ->
            if (isAudio && trackIndex >= s.audioLayerCount)
                s.copy(audioLayerCount = trackIndex + 1)
            else if (!isAudio && trackIndex >= s.visualLayerCount)
                s.copy(visualLayerCount = trackIndex + 1)
            else s
        }
    }

    fun setTimelineZoom(slider: Float) =
        _state.update {
            it.copy(
                timelineZoom = slider.coerceIn(
                    TimelineZoom.SLIDER_MIN, TimelineZoom.SLIDER_MAX
                )
            )
        }

    fun setExportResolution(res: String) = _state.update { it.copy(exportResolution = res) }
    fun setExportFps(fps: Int) = _state.update { it.copy(exportFps = fps) }
    fun setExportBitrate(kbps: Int) =
        _state.update { it.copy(exportBitrateKbps = kbps.coerceIn(1000, 50000)) }
    fun setExportFormat(fmt: String) = _state.update { it.copy(exportFormat = fmt) }
    fun setExportFolderUri(uri: String?) = _state.update { it.copy(exportFolderUri = uri) }

    fun toggleVisualTrackVisibility(trackIndex: Int) {
        _state.update { s ->
            val new = s.hiddenVisualTracks.toMutableSet()
            if (trackIndex in new) new.remove(trackIndex) else new.add(trackIndex)
            s.copy(hiddenVisualTracks = new)
        }
    }

    fun toggleAudioTrackMute(trackIndex: Int) {
        _state.update { s ->
            val new = s.mutedAudioTracks.toMutableSet()
            if (trackIndex in new) new.remove(trackIndex) else new.add(trackIndex)
            s.copy(mutedAudioTracks = new)
        }
    }

    fun addClipWithSource(uri: Uri, name: String, duration: Long, sourceTotalMs: Long) {
        pushHistory()
        val linkId = "lk-${System.currentTimeMillis()}"
        val isImage = name.lowercase().let {
            it.endsWith(".png") || it.endsWith(".jpg") ||
                    it.endsWith(".jpeg") || it.endsWith(".webp")
        }

        val imageDur = SettingsConsumer.defaultImageDurationMs.toLong()

        val videoClip = EditorClip(
            uri = uri, name = name, type = "video/mp4",
            sourceStartMs = 0L,
            sourceEndMs = if (isImage) imageDur else duration,
            timelineStartMs = 0L, trackIndex = 0, isAudio = false,
            sourceTotalMs = if (isImage) Long.MAX_VALUE else sourceTotalMs,
            linkedId = linkId
        )
        val audioClip = videoClip.copy(
            id = UUID.randomUUID().toString(),
            name = "$name (audio)", type = "audio/mpeg",
            trackIndex = 0, isAudio = true
        )

        // 🆕 FIX: List variable correctly calculate karo
        val updatedClips = _state.value.clips + videoClip + audioClip

        // 🆕 LOCK timeline if not locked
        val shouldLock = _state.value.timelineLockedMs == 0L
        val lockedMs = if (shouldLock) {
            val maxEnd = updatedClips.maxOfOrNull { it.timelineEndMs } ?: 5000L
            maxEnd + 5_000L
        } else {
            _state.value.timelineLockedMs
        }

        _state.update { st ->
            st.copy(
                clips = updatedClips,
                currentIndex = 0,
                currentPosMs = 0L,
                selectedClipId = videoClip.id,
                selectedTrackIndex = 0,
                selectedIsAudio = false,
                multiSelectedIds = setOf(videoClip.id, audioClip.id),
                visualLayerCount = maxOf(st.visualLayerCount, 3),
                audioLayerCount = maxOf(st.audioLayerCount, 2),
                timelineLockedMs = lockedMs // 🆕
            )
        }
        updateHistoryFlags()
    }

    fun addClipSmart(
        uri: Uri,
        name: String,
        durationMs: Long,
        mediaType: String = "video/mp4",
        hasAudioTrack: Boolean = mediaType.startsWith("audio/")
    ) {
        val s = _state.value

        val hasAnyVisualizer = s.clips.any { it.isVisualizerClip }
        if (!hasAnyVisualizer && s.beatsDetected) {
            _state.update {
                it.copy(
                    beatsDetected = false,
                    beatsCount = 0,
                    beatTimesMs = emptyList(),
                    beatStrengths = emptyList()
                )
            }
        }

        val durMs = if (durationMs < 100L) 5_000L else durationMs.coerceAtLeast(1_000L)
        val isImage = mediaType.startsWith("image/")
        val isAudioFile = mediaType.startsWith("audio/")

        val settingsDefaultImg = SettingsConsumer.defaultImageDurationMs.toLong()
        val finalDurMs = if (isImage && durationMs < 100L) settingsDefaultImg else durMs

        if (isAudioFile) {
            val audioPlacement = TimelineTools.findPlacement(
                visualTracks = s.timelineAudioList(),
                visualLayerCount = s.audioLayerCount,
                playheadMs = s.currentPosMs,
                durMs = finalDurMs
            )
            pushHistory()
            val clip = EditorClip(
                uri = uri, name = name, type = mediaType,
                sourceStartMs = 0L, sourceEndMs = finalDurMs,
                timelineStartMs = s.currentPosMs,
                trackIndex = audioPlacement.trackIndex,
                isAudio = true, sourceTotalMs = finalDurMs, linkedId = null
            )
            val newAudioList = s.timelineAudioList().toMutableList()
            while (newAudioList.size <= audioPlacement.trackIndex)
                newAudioList.add(emptyList())
            newAudioList[audioPlacement.trackIndex] =
                newAudioList[audioPlacement.trackIndex] + clip

            val updatedClips = s.timelineVisualList().flatten() + newAudioList.flatten()

            // 🆕 LOCK timeline
            val shouldLock = s.timelineLockedMs == 0L
            val lockedMs = if (shouldLock) {
                val maxEnd = updatedClips.maxOfOrNull { it.timelineEndMs } ?: 5000L
                maxEnd + 5_000L
            } else s.timelineLockedMs

            _state.update { st ->
                st.copy(
                    clips = updatedClips,
                    audioLayerCount = maxOf(
                        st.audioLayerCount, audioPlacement.trackIndex + 1
                    ),
                    selectedClipId = clip.id,
                    selectedTrackIndex = audioPlacement.trackIndex,
                    selectedIsAudio = true,
                    multiSelectedIds = emptySet(),
                    timelineLockedMs = lockedMs
                )
            }
            updateHistoryFlags()
            return
        }

        val visualPlacement = TimelineTools.findPlacement(
            visualTracks = s.timelineVisualList(),
            visualLayerCount = s.visualLayerCount,
            playheadMs = s.currentPosMs,
            durMs = finalDurMs
        )
        val audioPlacement = TimelineTools.findPlacement(
            visualTracks = s.timelineAudioList(),
            visualLayerCount = s.audioLayerCount,
            playheadMs = s.currentPosMs,
            durMs = finalDurMs
        )

        pushHistory()
        val linkId = "lk-${System.currentTimeMillis()}"

        val videoClip = EditorClip(
            uri = uri, name = name, type = mediaType,
            sourceStartMs = 0L, sourceEndMs = finalDurMs,
            timelineStartMs = s.currentPosMs,
            trackIndex = visualPlacement.trackIndex,
            isAudio = false,
            sourceTotalMs = if (isImage) Long.MAX_VALUE else finalDurMs,
            linkedId = if (isImage) null else linkId
        )

        val audioClip = if (isImage || !hasAudioTrack) null else videoClip.copy(
            id = UUID.randomUUID().toString(),
            name = "$name (audio)", type = "audio/mpeg",
            trackIndex = audioPlacement.trackIndex,
            isAudio = true
        )

        val newVisualList = s.timelineVisualList().toMutableList()
        while (newVisualList.size <= visualPlacement.trackIndex)
            newVisualList.add(emptyList())
        newVisualList[visualPlacement.trackIndex] =
            newVisualList[visualPlacement.trackIndex] + videoClip

        val newAudioList = if (audioClip != null) {
            val list = s.timelineAudioList().toMutableList()
            while (list.size <= audioPlacement.trackIndex) list.add(emptyList())
            list[audioPlacement.trackIndex] =
                list[audioPlacement.trackIndex] + audioClip
            list
        } else s.timelineAudioList()

        val updatedClips = newVisualList.flatten() + newAudioList.flatten()

        // 🆕 LOCK timeline
        val shouldLock = s.timelineLockedMs == 0L
        val lockedMs = if (shouldLock) {
            val maxEnd = updatedClips.maxOfOrNull { it.timelineEndMs } ?: 5000L
            maxEnd + 5_000L
        } else s.timelineLockedMs

        _state.update { st ->
            st.copy(
                clips = updatedClips,
                visualLayerCount = maxOf(st.visualLayerCount, visualPlacement.trackIndex + 1),
                audioLayerCount = maxOf(st.audioLayerCount, audioPlacement.trackIndex + 1),
                selectedClipId = videoClip.id,
                selectedTrackIndex = visualPlacement.trackIndex,
                selectedIsAudio = false,
                multiSelectedIds = audioClip?.let {
                    setOf(videoClip.id, it.id)
                } ?: emptySet(),
                timelineLockedMs = lockedMs
            )
        }
        updateHistoryFlags()
    }

    fun selectClip(clip: EditorClip) {
        _state.update {
            val linkedIds = it.clips.asSequence()
                .filter { candidate ->
                    (clip.linkedId != null && candidate.linkedId == clip.linkedId) ||
                            (clip.linkGroupId != null &&
                                    candidate.linkGroupId == clip.linkGroupId)
                }
                .map { candidate -> candidate.id }
                .toSet() + clip.id
            it.copy(
                selectedClipId = clip.id,
                selectedTrackIndex = clip.trackIndex,
                selectedIsAudio = clip.isAudio,
                multiSelectedIds = linkedIds.takeIf { ids -> ids.size > 1 } ?: emptySet(),
                currentIndex = it.clips.indexOf(clip).coerceAtLeast(0)
            )
        }
    }

    fun clearAllSelection() {
        _state.update {
            it.copy(selectedClipId = null, multiSelectedIds = emptySet())
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  PREVIEW FILTERS + EFFECTS (🆕 ADDED)
    // ═══════════════════════════════════════════════════════════

    fun setPreviewFilters(filters: FilterState?) {
        _state.update { it.copy(previewFilters = filters) }
    }

    fun clearPreviewFilters() {
        _state.update { it.copy(previewFilters = null) }
    }

    fun setPreviewEffect(presetKey: String?, intensity: Float) {
        if (presetKey == null) {
            _state.update { it.copy(previewEffectState = null) }
            return
        }
        val preset = EffectLibrary.findByKey(presetKey) ?: return
        val scaled = buildScaledEffectState(preset, intensity)
        _state.update { it.copy(previewEffectState = scaled) }
    }

    fun setPreviewEffectFull(state: EffectState?) {
        _state.update { it.copy(previewEffectState = state) }
    }

    fun clearPreviewEffect() {
        _state.update { it.copy(previewEffectState = null) }
    }

    // ═══════════════════════════════════════════════════════════
    //  CLIP MOVE
    // ═══════════════════════════════════════════════════════════

    fun moveClip(clipId: String, targetTrackIndex: Int, newIsAudio: Boolean, newTimelineMs: Long) {
        val s = _state.value
        val clip = s.clips.firstOrNull { it.id == clipId } ?: return
        val targetTrack = targetTrackIndex.coerceAtLeast(0)
        val targetStart = newTimelineMs.coerceAtLeast(0L)

        val isMulti = s.multiSelectedIds.size > 1 && clipId in s.multiSelectedIds
        if (isMulti) {
            moveSelectedClipsBulk(clipId, targetTrackIndex, newTimelineMs)
            return
        }

        if (clip.isAudio && !clip.isAudioEffectClip) {
            val movedDelta = targetStart - clip.timelineStartMs
            if (movedDelta != 0L) {
                val list = _state.value.clips.toMutableList()
                var changed = false
                for (i in list.indices) {
                    val c = list[i]
                    if (c.isVisualizerClip &&
                        c.visualizer?.linkedAudioClipId == clip.id
                    ) {
                        list[i] = c.copy(
                            timelineStartMs = (c.timelineStartMs + movedDelta)
                                .coerceAtLeast(0L)
                        )
                        changed = true
                    }
                }
                if (changed) _state.update { it.copy(clips = list) }
            }
        }

        val groupId = clip.linkGroupId
        if (groupId != null) {
            moveLinkedGroup(clipId, targetTrack, newIsAudio, targetStart)
            return
        }

        val sourceTrack = clip.trackIndex
        val isAudio = clip.isAudio

        if (targetTrack != sourceTrack) {
            val draggedEnd = targetStart + clip.durationMs
            val targetClip = s.clips.firstOrNull { other ->
                other.id != clipId &&
                        other.isAudio == isAudio &&
                        other.trackIndex == targetTrack &&
                        other.timelineStartMs < draggedEnd &&
                        targetStart < other.timelineEndMs
            }

            if (targetClip != null) {
                val overlaps = targetClip.timelineStartMs < draggedEnd &&
                        targetStart < targetClip.timelineEndMs

                if (overlaps) {
                    if (clip.durationMs <= targetClip.durationMs) {
                        val list = s.clips.toMutableList()
                        val draggedIdx = list.indexOfFirst { it.id == clipId }
                        val targetIdx = list.indexOfFirst { it.id == targetClip.id }

                        if (draggedIdx >= 0 && targetIdx >= 0) {
                            list[draggedIdx] = clip.copy(trackIndex = targetClip.trackIndex)
                            list[targetIdx] = targetClip.copy(trackIndex = sourceTrack)

                            val draggedLink = clip.linkedId
                            if (draggedLink != null) {
                                val pIdx = list.indexOfFirst {
                                    it.linkedId == draggedLink && it.id != clipId
                                }
                                if (pIdx >= 0) {
                                    list[pIdx] = list[pIdx].copy(trackIndex = sourceTrack)
                                }
                            }

                            val targetLink = targetClip.linkedId
                            if (targetLink != null) {
                                val pIdx = list.indexOfFirst {
                                    it.linkedId == targetLink && it.id != targetClip.id
                                }
                                if (pIdx >= 0) {
                                    list[pIdx] = list[pIdx].copy(
                                        trackIndex = targetClip.trackIndex
                                    )
                                }
                            }

                            _state.update {
                                it.copy(
                                    clips = list,
                                    selectedClipId = clipId,
                                    selectedTrackIndex = targetClip.trackIndex,
                                    selectedIsAudio = isAudio,
                                    multiSelectedIds = emptySet()
                                )
                            }
                            updateHistoryFlags()
                            return
                        }
                    } else {
                        val freeTrack = findFreeTrackForClip(
                            clips = s.clips, excludeClipId = clipId,
                            isAudio = isAudio, startMs = targetStart,
                            endMs = draggedEnd, preferredTrack = targetTrack,
                            sourceTrack = sourceTrack,
                            visualLayerCount = s.visualLayerCount,
                            audioLayerCount = s.audioLayerCount
                        )

                        if (freeTrack >= 0) {
                            val list = s.clips.toMutableList()
                            val idx = list.indexOfFirst { it.id == clipId }
                            if (idx >= 0) {
                                list[idx] = clip.copy(
                                    trackIndex = freeTrack,
                                    timelineStartMs = targetStart
                                )
                                val linkId = clip.linkedId
                                if (linkId != null) {
                                    val pIdx = list.indexOfFirst {
                                        it.linkedId == linkId && it.id != clipId
                                    }
                                    if (pIdx >= 0) {
                                        list[pIdx] = list[pIdx].copy(trackIndex = freeTrack)
                                    }
                                }
                                val maxVisual = list.filter { !it.isAudio }
                                    .maxOfOrNull { it.trackIndex } ?: 0
                                val maxAudio = list.filter { it.isAudio }
                                    .maxOfOrNull { it.trackIndex } ?: 0

                                _state.update {
                                    it.copy(
                                        clips = list,
                                        selectedClipId = clipId,
                                        selectedTrackIndex = freeTrack,
                                        selectedIsAudio = isAudio,
                                        visualLayerCount = maxOf(
                                            it.visualLayerCount, maxVisual + 1
                                        ),
                                        audioLayerCount = maxOf(
                                            it.audioLayerCount, maxAudio + 1
                                        ),
                                        multiSelectedIds = emptySet()
                                    )
                                }
                                updateHistoryFlags()
                                return
                            }
                        }
                    }
                }
            }
        }

        val list = s.clips.toMutableList()
        val idx = list.indexOfFirst { it.id == clipId }
        if (idx < 0) return

        val trackDelta = targetTrack - clip.trackIndex
        val timeDelta = targetStart - clip.timelineStartMs

        list[idx] = list[idx].copy(
            trackIndex = targetTrack,
            timelineStartMs = targetStart
        )

        val linkId = clip.linkedId
        if (linkId != null) {
            for (i in list.indices) {
                val c = list[i]
                if (c.linkedId != linkId || c.id == clipId) continue
                val newLinkedTrack = if (c.isAudio || c.isVisualizerClip) {
                    (c.trackIndex + trackDelta).coerceAtLeast(0)
                } else c.trackIndex
                val newLinkedStart = (c.timelineStartMs + timeDelta).coerceAtLeast(0L)
                list[i] = c.copy(
                    trackIndex = newLinkedTrack,
                    timelineStartMs = newLinkedStart
                )
            }
        }
        val maxVisual = list.filter { !it.isAudio }.maxOfOrNull { it.trackIndex } ?: 0
        val maxAudio = list.filter { it.isAudio }.maxOfOrNull { it.trackIndex } ?: 0

        _state.update {
            it.copy(
                clips = list,
                selectedClipId = clipId,
                selectedTrackIndex = targetTrack,
                selectedIsAudio = isAudio,
                visualLayerCount = maxOf(it.visualLayerCount, maxVisual + 1),
                audioLayerCount = maxOf(it.audioLayerCount, maxAudio + 1)
            )
        }
        updateHistoryFlags()
    }

    private fun moveLinkedGroup(
        movedClipId: String, targetTrackIndex: Int,
        newIsAudio: Boolean, newTimelineMs: Long
    ) {
        val s = _state.value
        val movedClip = s.clips.firstOrNull { it.id == movedClipId } ?: return
        val groupId = movedClip.linkGroupId ?: return
        val group = s.clips.filter { it.linkGroupId == groupId }
        if (group.isEmpty()) return
        val targetTrack = targetTrackIndex.coerceAtLeast(0)
        val targetStart = newTimelineMs.coerceAtLeast(0L)

        val anchor = group.maxByOrNull { it.linkAnchorPriority } ?: movedClip
        val anchorIsMoved = anchor.id == movedClipId

        val list = s.clips.toMutableList()

        if (anchorIsMoved) {
            val earliestStart = group.minOf { it.timelineStartMs }
            val timeDelta = (targetStart - anchor.timelineStartMs)
                .coerceAtLeast(-earliestStart)
            group.forEach { c ->
                val idx = list.indexOfFirst { it.id == c.id }
                if (idx < 0) return@forEach
                if (c.id == anchor.id) {
                    list[idx] = c.copy(
                        trackIndex = targetTrack,
                        timelineStartMs = (c.timelineStartMs + timeDelta).coerceAtLeast(0L)
                    )
                } else {
                    val newStart = (c.timelineStartMs + timeDelta).coerceAtLeast(0L)
                    list[idx] = c.copy(timelineStartMs = newStart)
                }
            }
        } else {
            val idx = list.indexOfFirst { it.id == movedClipId }
            if (idx >= 0) {
                list[idx] = movedClip.copy(
                    trackIndex = targetTrack,
                    timelineStartMs = targetStart
                )
            }
        }

        val maxVisual = list.filter { !it.isAudio }.maxOfOrNull { it.trackIndex } ?: 0
        val maxAudio = list.filter { it.isAudio }.maxOfOrNull { it.trackIndex } ?: 0

        _state.update {
            it.copy(
                clips = list,
                selectedClipId = movedClipId,
                selectedTrackIndex = targetTrack,
                selectedIsAudio = newIsAudio,
                visualLayerCount = maxOf(it.visualLayerCount, maxVisual + 1),
                audioLayerCount = maxOf(it.audioLayerCount, maxAudio + 1)
            )
        }
        updateHistoryFlags()
    }

    private fun findFreeTrackForClip(
        clips: List<EditorClip>, excludeClipId: String, isAudio: Boolean,
        startMs: Long, endMs: Long, preferredTrack: Int, sourceTrack: Int,
        visualLayerCount: Int, audioLayerCount: Int
    ): Int {
        val maxLayers = if (isAudio) audioLayerCount else visualLayerCount
        val searchLimit = maxLayers + 10
        for (t in preferredTrack until searchLimit) {
            if (t == sourceTrack) continue
            val hasOverlap = clips.any { c ->
                c.id != excludeClipId && c.isAudio == isAudio &&
                        c.trackIndex == t &&
                        c.timelineStartMs < endMs && startMs < c.timelineEndMs
            }
            if (!hasOverlap) return t
        }
        return maxLayers
    }

    private fun moveSelectedClipsBulk(
        anchorId: String, newTrackIndex: Int, newTimelineMs: Long
    ) {
        val s = _state.value
        val anchor = s.clips.firstOrNull { it.id == anchorId } ?: return

        val targetIds = s.multiSelectedIds.toMutableSet()
        s.clips.filter { it.id in targetIds && it.linkedId != null }.forEach { c ->
            s.clips.filter { it.linkedId == c.linkedId }.forEach { targetIds.add(it.id) }
        }

        val trackDelta = newTrackIndex - anchor.trackIndex
        val earliestStart = s.clips
            .filter { it.id in targetIds }
            .minOfOrNull { it.timelineStartMs } ?: anchor.timelineStartMs
        val timeDelta = (newTimelineMs - anchor.timelineStartMs)
            .coerceAtLeast(-earliestStart)

        val list = s.clips.toMutableList()
        for (i in list.indices) {
            if (list[i].id !in targetIds) continue
            val newTrack = (list[i].trackIndex + trackDelta).coerceAtLeast(0)
            val newStart = (list[i].timelineStartMs + timeDelta).coerceAtLeast(0L)
            list[i] = list[i].copy(trackIndex = newTrack, timelineStartMs = newStart)
        }

        val maxVisual = list.filter { !it.isAudio }.maxOfOrNull { it.trackIndex } ?: 0
        val maxAudio = list.filter { it.isAudio }.maxOfOrNull { it.trackIndex } ?: 0

        _state.update {
            it.copy(
                clips = list,
                visualLayerCount = maxOf(it.visualLayerCount, maxVisual + 1),
                audioLayerCount = maxOf(it.audioLayerCount, maxAudio + 1)
            )
        }
        updateHistoryFlags()
    }

    fun swapTracks(fromTrack: Int, toTrack: Int, isAudio: Boolean) {
        if (fromTrack == toTrack) return
        val list = _state.value.clips.toMutableList()
        val newTrackOf = mutableMapOf<Int, Int>()
        if (fromTrack < toTrack) {
            newTrackOf[fromTrack] = toTrack
            for (t in fromTrack + 1..toTrack) newTrackOf[t] = t - 1
        } else {
            newTrackOf[fromTrack] = toTrack
            for (t in toTrack until fromTrack) newTrackOf[t] = t + 1
        }
        pushHistory()
        for (i in list.indices) {
            if (list[i].isAudio != isAudio) continue
            val old = list[i].trackIndex
            val new = newTrackOf[old] ?: old
            if (new != old) list[i] = list[i].copy(trackIndex = new)
        }
        _state.update { it.copy(clips = list) }
        updateHistoryFlags()
    }

    fun setCurrentPos(ms: Long) = _state.update { it.copy(currentPosMs = ms) }
    fun setPlaying(playing: Boolean) = _state.update { it.copy(isPlaying = playing) }

    private fun findOrCreateVisualTrack(
        preferredTrack: Int, startMs: Long, durMs: Long
    ): Int {
        val s = _state.value
        val endMs = startMs + durMs
        val startFrom = preferredTrack.coerceAtLeast(0)
        for (t in startFrom until s.visualLayerCount) {
            val hasOverlap = s.clips.any { c ->
                !c.isAudio && c.trackIndex == t &&
                        c.timelineStartMs < endMs && startMs < c.timelineEndMs
            }
            if (!hasOverlap) return t
        }
        val newIdx = maxOf(s.visualLayerCount, startFrom)
        _state.update { it.copy(visualLayerCount = newIdx + 1) }
        return newIdx
    }

    private fun addClipOnNewLayer(clip: EditorClip, preferredTrack: Int): EditorClip {
        val trackIdx = findOrCreateVisualTrack(
            preferredTrack, clip.timelineStartMs, clip.durationMs
        )
        val finalClip = clip.copy(trackIndex = trackIdx)
        val list = _state.value.clips.toMutableList()
        list.add(finalClip)
        _state.update {
            it.copy(
                clips = list,
                selectedClipId = finalClip.id,
                selectedTrackIndex = trackIdx,
                selectedIsAudio = false
            )
        }
        return finalClip
    }

    private fun findFreeAudioTrack(startMs: Long, durMs: Long): Int {
        val s = _state.value
        val endMs = startMs + durMs
        for (t in 0 until s.audioLayerCount) {
            val hasSourceAudio = s.clips.any { c ->
                c.isAudio && !c.isAudioEffectClip && c.trackIndex == t
            }
            if (hasSourceAudio) continue
            val hasOverlap = s.clips.any { c ->
                c.isAudio && c.trackIndex == t &&
                        c.timelineStartMs < endMs && startMs < c.timelineEndMs
            }
            if (!hasOverlap) return t
        }
        val newIdx = s.audioLayerCount
        _state.update { it.copy(audioLayerCount = newIdx + 1) }
        return newIdx
    }

    fun closeGapsOnSelectedTrack() {
        val s = _state.value
        val selectedClip = s.selectedClip
        val trackIndex = selectedClip?.trackIndex ?: s.selectedTrackIndex
        val isAudio = selectedClip?.isAudio ?: s.selectedIsAudio
        val list = if (isAudio) s.timelineAudioList() else s.timelineVisualList()
        val track = list.getOrNull(trackIndex) ?: return
        val updated = TimelineTools.closeGapsOnTrack(track)
        val sameTrackChanged = updated.associate { it.id to it.timelineStartMs }
            .filter { (id, startMs) ->
                track.firstOrNull { it.id == id }?.timelineStartMs != startMs
            }
        val changedStarts = sameTrackChanged
        if (changedStarts.isEmpty()) return

        pushHistory()
        val movedDeltas = changedStarts.mapNotNull { (id, startMs) ->
            val original = s.clips.firstOrNull { it.id == id } ?: return@mapNotNull null
            val delta = startMs - original.timelineStartMs
            if (original.linkedId == null || delta == 0L) null
            else original.linkedId to delta
        }.toMap()

        _state.update { st ->
            val updates = changedStarts.toMutableMap()
            val linkedClips = st.clips.filter { it.linkedId in movedDeltas.keys }
            linkedClips.forEach { linked ->
                val delta = movedDeltas[linked.linkedId] ?: return@forEach
                updates.putIfAbsent(linked.id, (linked.timelineStartMs + delta).coerceAtLeast(0L))
            }
            st.copy(
                clips = st.clips.map { clip ->
                    updates[clip.id]?.let { clip.copy(timelineStartMs = it) } ?: clip
                }
            )
        }
        updateHistoryFlags()
    }

    fun selectForward() {
        val sel = _state.value.selectedClip ?: return
        val s = _state.value
        val list = if (sel.isAudio) s.timelineAudioList() else s.timelineVisualList()
        val track = list.getOrNull(sel.trackIndex) ?: return
        val ids = TimelineTools.selectForwardOnLayer(track, sel)
        _state.update { it.copy(multiSelectedIds = ids) }
    }

    fun selectBackward() {
        val sel = _state.value.selectedClip ?: return
        val s = _state.value
        val list = if (sel.isAudio) s.timelineAudioList() else s.timelineVisualList()
        val track = list.getOrNull(sel.trackIndex) ?: return
        val ids = TimelineTools.selectBackwardOnLayer(track, sel)
        _state.update { it.copy(multiSelectedIds = ids) }
    }

    fun clearMultiSelect() = _state.update { it.copy(multiSelectedIds = emptySet()) }
    fun deselectAll() = _state.update { it.copy(multiSelectedIds = emptySet()) }

    fun selectAllClips() {
        val all = _state.value.clips.map { it.id }.toSet()
        _state.update { it.copy(multiSelectedIds = all) }
    }

    fun selectAllOnCurrentTrack() {
        val s = _state.value
        val ids = s.clips
            .filter { it.trackIndex == s.selectedTrackIndex && it.isAudio == s.selectedIsAudio }
            .map { it.id }.toSet()
        _state.update { it.copy(multiSelectedIds = ids) }
    }

    fun selectAllTextClips() {
        val ids = _state.value.clips.filter { it.isTextClip }.map { it.id }.toSet()
        _state.update { it.copy(multiSelectedIds = ids) }
    }

    fun toggleMultiSelect(clipId: String) {
        _state.update { s ->
            val new = s.multiSelectedIds.toMutableSet()
            if (clipId in new) new.remove(clipId) else new.add(clipId)
            s.copy(multiSelectedIds = new)
        }
    }

    fun deleteSelectedClips() {
        val s = _state.value
        val targetIds = if (s.multiSelectedIds.isNotEmpty()) s.multiSelectedIds
        else setOfNotNull(s.selectedClipId)
        if (targetIds.isEmpty()) return

        pushHistory()
        val linkedIds = mutableSetOf<String>()
        s.clips.filter { it.id in targetIds }.forEach { clip ->
            if (clip.linkedId != null) {
                s.clips.filter { it.linkedId == clip.linkedId }
                    .forEach { linkedIds.add(it.id) }
            }
            if (clip.linkGroupId != null) {
                s.clips.filter { it.linkGroupId == clip.linkGroupId }
                    .forEach { linkedIds.add(it.id) }
            }
        }
        val allIds = targetIds + linkedIds
        val newList = s.clips.filter { it.id !in allIds }
        _state.update {
            it.copy(clips = newList, multiSelectedIds = emptySet(), selectedClipId = null)
        }
        cleanupStaleBeats()
        updateHistoryFlags()
    }

    fun linkSelectedClips(): String? {
        val s = _state.value
        val ids = if (s.multiSelectedIds.size >= 2) s.multiSelectedIds
        else setOfNotNull(s.selectedClipId)
        if (ids.size < 2) return null
        val clips = s.clips.filter { it.id in ids }
        if (clips.size < 2) return null

        val newGroupId = "lg-${System.currentTimeMillis()}"
        pushHistory()
        val list = s.clips.toMutableList()
        list.replaceAll { c ->
            if (c.id in ids) c.copy(linkGroupId = newGroupId) else c
        }
        _state.update { it.copy(clips = list) }
        updateHistoryFlags()
        return newGroupId
    }

    fun unlinkClip(clipId: String) {
        val s = _state.value
        val clip = s.clips.firstOrNull { it.id == clipId } ?: return
        val gid = clip.linkGroupId
        val linkedId = clip.linkedId
        if (gid == null && linkedId == null) return

        pushHistory()
        val list = s.clips.toMutableList()
        if (linkedId != null) {
            list.replaceAll { current ->
                if (current.linkedId == linkedId) current.copy(linkedId = null)
                else current
            }
        }

        if (gid != null) {
            val idx = list.indexOfFirst { it.id == clipId }
            if (idx >= 0) list[idx] = list[idx].copy(linkGroupId = null)

            val remaining = list.filter { it.linkGroupId == gid }
            if (remaining.size == 1) {
                val rIdx = list.indexOfFirst { it.linkGroupId == gid }
                if (rIdx >= 0) list[rIdx] = list[rIdx].copy(linkGroupId = null)
            }
        }
        _state.update {
            it.copy(clips = list, multiSelectedIds = emptySet())
        }
        updateHistoryFlags()
    }

    fun unlinkGroup(groupId: String) {
        pushHistory()
        val list = _state.value.clips.toMutableList()
        list.replaceAll { c ->
            if (c.linkGroupId == groupId) c.copy(linkGroupId = null) else c
        }
        _state.update { it.copy(clips = list) }
        updateHistoryFlags()
    }

    fun getLinkedClips(clipId: String): List<EditorClip> {
        val clip = _state.value.clips.firstOrNull { it.id == clipId } ?: return emptyList()
        val gid = clip.linkGroupId ?: return listOf(clip)
        return _state.value.clips.filter { it.linkGroupId == gid }
    }

    fun getLinkAnchor(clipId: String): EditorClip? {
        val group = getLinkedClips(clipId)
        return group.maxByOrNull { it.linkAnchorPriority }
    }

    private fun getClipPos(clip: EditorClip): Pair<Float, Float> = when {
        clip.isTextClip && clip.textState != null ->
            clip.textState.positionX to clip.textState.positionY
        clip.isVisualizerClip && clip.visualizer != null ->
            (clip.visualizer.positionX * 100f) to (clip.visualizer.positionY * 100f)
        else ->
            ((clip.offsetX * 100f) + 50f) to ((clip.offsetY * 100f) + 50f)
    }

    private fun getClipScale(clip: EditorClip): Float = when {
        clip.isTextClip && clip.textState != null -> clip.textState.scale
        clip.isVisualizerClip && clip.visualizer != null ->
            clip.visualizer.size / 0.32f * 100f
        else -> clip.scale * 100f
    }

    private fun getClipRotation(clip: EditorClip): Float = when {
        clip.isTextClip && clip.textState != null -> clip.textState.rotation
        clip.isVisualizerClip && clip.visualizer != null -> clip.visualizer.rotation
        else -> clip.rotation
    }

    private fun setClipPos(clip: EditorClip, x: Float, y: Float): EditorClip = when {
        clip.isTextClip && clip.textState != null ->
            clip.copy(textState = clip.textState.copy(positionX = x, positionY = y))
        clip.isVisualizerClip && clip.visualizer != null ->
            clip.copy(
                visualizer = clip.visualizer.copy(
                    positionX = x / 100f,
                    positionY = y / 100f
                )
            )
        else -> clip.copy(offsetX = (x - 50f) / 100f, offsetY = (y - 50f) / 100f)
    }

    private fun setClipScale(clip: EditorClip, scale: Float): EditorClip = when {
        clip.isTextClip && clip.textState != null ->
            clip.copy(textState = clip.textState.copy(scale = scale))
        clip.isVisualizerClip && clip.visualizer != null ->
            clip.copy(
                visualizer = clip.visualizer.copy(
                    size = scale / 100f * 0.32f
                )
            )
        else -> clip.copy(scale = scale / 100f)
    }

    private fun setClipRotation(clip: EditorClip, rotation: Float): EditorClip = when {
        clip.isTextClip && clip.textState != null ->
            clip.copy(textState = clip.textState.copy(rotation = rotation))
        clip.isVisualizerClip && clip.visualizer != null ->
            clip.copy(visualizer = clip.visualizer.copy(rotation = rotation))
        else -> clip.copy(rotation = rotation)
    }

    private fun setClipTransformWithAutoKeyframes(
        clip: EditorClip,
        x: Float, y: Float, scale: Float, rotation: Float, timeSec: Float
    ): EditorClip {
        var updated = setClipRotation(setClipScale(setClipPos(clip, x, y), scale), rotation)
        if (!KeyframeStore.hasAnyKeyframes(clip.keyframes)) return updated

        var keyframes = clip.keyframes
        keyframes = KeyframeStore.autoKeyframeIfActive(keyframes, "x", timeSec, x)
        keyframes = KeyframeStore.autoKeyframeIfActive(keyframes, "y", timeSec, y)
        keyframes = KeyframeStore.autoKeyframeIfActive(keyframes, "scale", timeSec, scale)
        keyframes = KeyframeStore.autoKeyframeIfActive(
            keyframes, "rotation", timeSec, rotation
        )
        updated = updated.copy(keyframes = keyframes)
        return updated
    }

    fun updateSelectedPositionBulk(anchorId: String, newX: Float, newY: Float) {
        val s = _state.value
        val anchor = s.clips.firstOrNull { it.id == anchorId } ?: return
        val isMulti = s.multiSelectedIds.size > 1 && anchorId in s.multiSelectedIds

        if (!isMulti) {
            val currentTimeSec = KeyframeStore.clipLocalTimeSeconds(
                s.currentPosMs, anchor.timelineStartMs, anchor.durationMs
            )
            val hasAnyKf = KeyframeStore.hasAnyKeyframes(anchor.keyframes)

            updateClipDirect(anchorId) { c ->
                var updated = setClipPos(c, newX, newY)
                if (hasAnyKf) {
                    var kf = c.keyframes
                    kf = KeyframeStore.autoKeyframeIfActive(kf, "x", currentTimeSec, newX)
                    kf = KeyframeStore.autoKeyframeIfActive(kf, "y", currentTimeSec, newY)
                    updated = updated.copy(keyframes = kf)
                }
                updated
            }
            return
        }

        val (ax, ay) = getClipPos(anchor)
        val dx = newX - ax
        val dy = newY - ay

        val list = s.clips.toMutableList()
        for (i in list.indices) {
            if (list[i].id in s.multiSelectedIds) {
                val (cx, cy) = getClipPos(list[i])
                list[i] = setClipPos(list[i], cx + dx, cy + dy)
            }
        }
        _state.update { it.copy(clips = list) }
    }

    fun updateSelectedTransformBulk(anchorId: String, newScale: Float, newRotation: Float) {
        val s = _state.value
        val anchor = s.clips.firstOrNull { it.id == anchorId } ?: return
        val isMulti = s.multiSelectedIds.size > 1 && anchorId in s.multiSelectedIds

        if (!isMulti) {
            val currentTimeSec = KeyframeStore.clipLocalTimeSeconds(
                s.currentPosMs, anchor.timelineStartMs, anchor.durationMs
            )
            val hasAnyKf = KeyframeStore.hasAnyKeyframes(anchor.keyframes)

            updateClipDirect(anchorId) { c ->
                var updated = setClipRotation(setClipScale(c, newScale), newRotation)
                if (hasAnyKf) {
                    var kf = c.keyframes
                    kf = KeyframeStore.autoKeyframeIfActive(
                        kf, "scale", currentTimeSec, newScale
                    )
                    kf = KeyframeStore.autoKeyframeIfActive(
                        kf, "rotation", currentTimeSec, newRotation
                    )
                    updated = updated.copy(keyframes = kf)
                }
                updated
            }
            return
        }

        val anchorScale = getClipScale(anchor)
        val anchorRot = getClipRotation(anchor)
        val scaleFactor = if (abs(anchorScale) > 0.001f) newScale / anchorScale else 1f
        val rotDelta = newRotation - anchorRot

        val list = s.clips.toMutableList()
        for (i in list.indices) {
            if (list[i].id in s.multiSelectedIds) {
                val cs = getClipScale(list[i])
                val cr = getClipRotation(list[i])
                val newS = cs * scaleFactor
                val newR = cr + rotDelta
                list[i] = setClipRotation(setClipScale(list[i], newS), newR)
            }
        }
        _state.update { it.copy(clips = list) }
    }

    fun resetAllSelectedTransforms() {
        val s = _state.value
        val isMulti = s.multiSelectedIds.size > 1
        if (!isMulti) {
            resetAllTransform()
            return
        }
        pushHistory()
        val list = s.clips.toMutableList()
        for (i in list.indices) {
            if (list[i].id in s.multiSelectedIds) {
                list[i] = setClipRotation(
                    setClipScale(setClipPos(list[i], 50f, 50f), 100f),
                    0f
                ).copy(keyframes = emptyMap())
            }
        }
        _state.update { it.copy(clips = list) }
        updateHistoryFlags()
    }

    fun updateTextPositionBulk(anchorId: String, newX: Float, newY: Float) =
        updateSelectedPositionBulk(anchorId, newX, newY)

    fun updateTextTransformBulk(anchorId: String, newScale: Float, newRot: Float) =
        updateSelectedTransformBulk(anchorId, newScale, newRot)

    private fun syncLinkedTrim(primary: EditorClip) {
        val linkId = primary.linkedId ?: return
        val list = _state.value.clips.toMutableList()
        for (i in list.indices) {
            val c = list[i]
            if (c.linkedId != linkId || c.id == primary.id) continue
            if (c.isVisualizerClip) {
                val durMs = (primary.sourceEndMs - primary.sourceStartMs)
                    .coerceAtLeast(100L)
                list[i] = c.copy(
                    timelineStartMs = primary.timelineStartMs,
                    sourceStartMs = 0L, sourceEndMs = durMs
                )
            } else {
                list[i] = c.copy(
                    sourceStartMs = primary.sourceStartMs,
                    sourceEndMs = primary.sourceEndMs,
                    timelineStartMs = primary.timelineStartMs
                )
            }
        }
        _state.update { it.copy(clips = list) }
    }

    fun trimClipLeft(newSourceStartMs: Long) {
        val sel = _state.value.selectedClip ?: return
        val maxStart = (sel.sourceEndMs - EditorClip.MIN_DURATION_MS).coerceAtLeast(0L)
        val clampedSourceStart = newSourceStartMs.coerceIn(0L, maxStart)
        val delta = clampedSourceStart - sel.sourceStartMs
        if (delta == 0L) return
        updateClipDirect(sel.id) { clip ->
            clip.copy(
                sourceStartMs = clampedSourceStart,
                timelineStartMs = (clip.timelineStartMs + delta).coerceAtLeast(0L)
            )
        }
        if (sel.linkedId != null) {
            val linkedClips = _state.value.clips.filter {
                it.linkedId == sel.linkedId && it.id != sel.id
            }
            linkedClips.forEach { linked ->
                updateClipDirect(linked.id) { clip ->
                    if (clip.isVisualizerClip) {
                        val newDur = (sel.sourceEndMs - clampedSourceStart)
                            .coerceAtLeast(100L)
                        clip.copy(
                            timelineStartMs = (clip.timelineStartMs + delta)
                                .coerceAtLeast(0L),
                            sourceStartMs = 0L, sourceEndMs = newDur
                        )
                    } else {
                        clip.copy(
                            sourceStartMs = clampedSourceStart,
                            timelineStartMs = (clip.timelineStartMs + delta)
                                .coerceAtLeast(0L)
                        )
                    }
                }
            }
        }
    }

    fun trimClipRight(newSourceEndMs: Long) {
        val sel = _state.value.selectedClip ?: return
        val minEnd = sel.sourceStartMs + EditorClip.MIN_DURATION_MS
        val maxEnd = if (sel.sourceTotalMs != Long.MAX_VALUE) sel.sourceTotalMs
        else Long.MAX_VALUE
        val clampedEnd = newSourceEndMs.coerceIn(minEnd, maxEnd)
        if (clampedEnd == sel.sourceEndMs) return
        updateClipDirect(sel.id) { it.copy(sourceEndMs = clampedEnd) }
        if (sel.linkedId != null) {
            val linkedClips = _state.value.clips.filter {
                it.linkedId == sel.linkedId && it.id != sel.id
            }
            linkedClips.forEach { linked ->
                updateClipDirect(linked.id) { clip ->
                    if (clip.isVisualizerClip) {
                        val newDur = (clampedEnd - sel.sourceStartMs)
                            .coerceAtLeast(100L)
                        clip.copy(sourceStartMs = 0L, sourceEndMs = newDur)
                    } else {
                        clip.copy(sourceEndMs = clampedEnd)
                    }
                }
            }
        }
    }

    fun trimLeft() {
        val sel = _state.value.selectedClip ?: return
        val localOffsetMs = (_state.value.currentPosMs - sel.timelineStartMs).coerceAtLeast(0L)
        if (localOffsetMs <= 0L) return

        val sourceCutMs = (localOffsetMs * sel.speed).toLong()
        val newSourceStart = sel.sourceStartMs + sourceCutMs
        if (newSourceStart >= sel.sourceEndMs - EditorClip.MIN_DURATION_MS) return

        pushHistory()
        updateClipDirect(sel.id) {
            it.copy(
                sourceStartMs = newSourceStart,
                timelineStartMs = it.timelineStartMs + localOffsetMs
            )
        }
        if (sel.linkedId != null) {
            val linkedClips = _state.value.clips.filter {
                it.linkedId == sel.linkedId && it.id != sel.id
            }
            linkedClips.forEach { linked ->
                updateClipDirect(linked.id) { clip ->
                    if (clip.isVisualizerClip) {
                        val newDur = (sel.sourceEndMs - newSourceStart).coerceAtLeast(100L)
                        clip.copy(
                            timelineStartMs = (clip.timelineStartMs + localOffsetMs).coerceAtLeast(0L),
                            sourceStartMs = 0L, sourceEndMs = newDur
                        )
                    } else {
                        clip.copy(
                            sourceStartMs = newSourceStart,
                            timelineStartMs = (clip.timelineStartMs + localOffsetMs).coerceAtLeast(0L)
                        )
                    }
                }
            }
        }
        updateHistoryFlags()
    }

    fun trimRight() {
        val sel = _state.value.selectedClip ?: return
        val localOffsetMs = (_state.value.currentPosMs - sel.timelineStartMs).coerceAtLeast(0L)
        if (localOffsetMs >= sel.durationMs) return

        val newSourceEnd = sel.sourceStartMs + (localOffsetMs * sel.speed).toLong()
        if (newSourceEnd <= sel.sourceStartMs + EditorClip.MIN_DURATION_MS) return

        pushHistory()
        updateClipDirect(sel.id) { it.copy(sourceEndMs = newSourceEnd) }
        if (sel.linkedId != null) {
            val linkedClips = _state.value.clips.filter {
                it.linkedId == sel.linkedId && it.id != sel.id
            }
            linkedClips.forEach { linked ->
                updateClipDirect(linked.id) { clip ->
                    if (clip.isVisualizerClip) {
                        val newDur = (newSourceEnd - sel.sourceStartMs).coerceAtLeast(100L)
                        clip.copy(sourceStartMs = 0L, sourceEndMs = newDur)
                    } else {
                        clip.copy(sourceEndMs = newSourceEnd)
                    }
                }
            }
        }
        updateHistoryFlags()
    }

    fun splitCurrentClip() {
        val sel = _state.value.selectedClip ?: return
        val s = _state.value

        val localOffsetMs = (s.currentPosMs - sel.timelineStartMs).coerceAtLeast(0L)
        if (localOffsetMs <= EditorClip.MIN_DURATION_MS ||
            localOffsetMs >= sel.durationMs - EditorClip.MIN_DURATION_MS
        ) return

        pushHistory()
        val groupId = sel.linkGroupId
        val list = s.clips.toMutableList()

        fun splitSingleClip(
            source: EditorClip,
            splitSourceMs: Long,
            newGroupA: String?,
            newGroupB: String?,
            keepAId: String,
            keepBId: String
        ): Pair<EditorClip, EditorClip> {
            val cutLocalSec = (splitSourceMs - source.sourceStartMs) /
                    source.speed.coerceAtLeast(0.01f) / 1000f

            val aKeyframes = source.keyframes.mapNotNull { (prop, track) ->
                val kept = track.filter { it.time <= cutLocalSec }
                if (kept.isEmpty()) null else prop to kept
            }.toMap()

            val aMask = MaskEngine.trimToTime(source.mask, 0L)

            val bKeyframes = source.keyframes.mapNotNull { (prop, track) ->
                val kept = track.filter { it.time > cutLocalSec }
                    .map { it.copy(time = (it.time - cutLocalSec).coerceAtLeast(0f)) }
                if (kept.isEmpty()) null else prop to kept
            }.toMap()

            val cutMs = (cutLocalSec * 1000f).toLong()
            val bMask = MaskEngine.trimToTime(source.mask, cutMs)

            val first = source.copy(
                id = keepAId,
                sourceEndMs = splitSourceMs,
                linkGroupId = newGroupA,
                keyframes = aKeyframes,
                mask = aMask
            )

            val second = source.copy(
                id = keepBId,
                sourceStartMs = splitSourceMs,
                linkedId = null,
                linkGroupId = newGroupB,
                timelineStartMs = s.currentPosMs,
                keyframes = bKeyframes,
                mask = bMask
            )

            return first to second
        }

        if (groupId != null) {
            val group = s.clips.filter { it.linkGroupId == groupId }
            val groupA = "lgA-${System.currentTimeMillis()}"
            val groupB = "lgB-${System.currentTimeMillis()}"
            var firstIdOfSel: String? = null

            group.forEach { c ->
                val idx = list.indexOfFirst { it.id == c.id }
                if (idx < 0) return@forEach
                if (s.currentPosMs <= c.timelineStartMs ||
                    s.currentPosMs >= c.timelineEndMs
                ) return@forEach

                val localSplitMs = c.sourceStartMs +
                        ((s.currentPosMs - c.timelineStartMs) * c.speed).toLong()
                if (localSplitMs <= c.sourceStartMs + EditorClip.MIN_DURATION_MS ||
                    localSplitMs >= c.sourceEndMs - EditorClip.MIN_DURATION_MS
                ) return@forEach

                val (first, second) = splitSingleClip(
                    source = c,
                    splitSourceMs = localSplitMs,
                    newGroupA = groupA,
                    newGroupB = groupB,
                    keepAId = "${c.id}-A",
                    keepBId = "${c.id}-B"
                )

                if (c.id == sel.id) firstIdOfSel = first.id
                list.removeAt(idx)
                list.add(idx, second)
                list.add(idx, first)
            }

            _state.update {
                it.copy(
                    clips = list,
                    selectedClipId = firstIdOfSel ?: list.firstOrNull {
                        it.id.startsWith("${sel.id}-A")
                    }?.id
                )
            }
        } else {
            val splitMs = sel.sourceStartMs + (localOffsetMs * sel.speed).toLong()
            if (splitMs <= sel.sourceStartMs + EditorClip.MIN_DURATION_MS ||
                splitMs >= sel.sourceEndMs - EditorClip.MIN_DURATION_MS
            ) return

            val linkedPartner = sel.linkedId?.let { lid ->
                s.clips.firstOrNull { it.linkedId == lid && it.id != sel.id }
            }

            val linkIdA = "lk-A-${System.currentTimeMillis()}"
            val linkIdB = "lk-B-${System.currentTimeMillis()}"

            val (firstVideo, secondVideo) = splitSingleClip(
                source = sel,
                splitSourceMs = splitMs,
                newGroupA = null,
                newGroupB = null,
                keepAId = "${sel.id}-A",
                keepBId = "${sel.id}-B"
            )

            val first = firstVideo.copy(linkedId = linkIdA)
            val second = secondVideo.copy(linkedId = linkIdB)

            val idx = list.indexOfFirst { it.id == sel.id }
            list.removeAt(idx)
            list.add(idx, second)
            list.add(idx, first)

            if (linkedPartner != null) {
                val partnerIdx = list.indexOfFirst { it.id == linkedPartner.id }
                if (partnerIdx >= 0) {
                    val partnerSplitMs = linkedPartner.sourceStartMs +
                            ((s.currentPosMs - linkedPartner.timelineStartMs) *
                                    linkedPartner.speed).toLong()

                    val safePartnerSplit = partnerSplitMs
                        .coerceIn(
                            linkedPartner.sourceStartMs + EditorClip.MIN_DURATION_MS,
                            linkedPartner.sourceEndMs - EditorClip.MIN_DURATION_MS
                        )

                    if (safePartnerSplit > linkedPartner.sourceStartMs + EditorClip.MIN_DURATION_MS &&
                        safePartnerSplit < linkedPartner.sourceEndMs - EditorClip.MIN_DURATION_MS
                    ) {
                        val (firstPartner, secondPartner) = splitSingleClip(
                            source = linkedPartner,
                            splitSourceMs = safePartnerSplit,
                            newGroupA = null,
                            newGroupB = null,
                            keepAId = "${linkedPartner.id}-A",
                            keepBId = "${linkedPartner.id}-B"
                        )

                        val linkedA = firstPartner.copy(linkedId = linkIdA)
                        val linkedB = secondPartner.copy(linkedId = linkIdB)

                        list.removeAt(partnerIdx)
                        list.add(partnerIdx, linkedB)
                        list.add(partnerIdx, linkedA)
                    }
                }
            }

            _state.update { it.copy(clips = list, selectedClipId = first.id) }
        }
        updateHistoryFlags()
    }

    fun commitTrim() = pushHistory()

    fun deleteCurrentClip() {
        val sel = _state.value.selectedClip ?: return
        pushHistory()
        val ids = DeleteEngine.collectForDeletion(sel, _state.value.clips).toMutableSet()
        val groupId = sel.linkGroupId
        if (groupId != null) {
            _state.value.clips
                .filter { it.linkGroupId == groupId }
                .forEach { ids.add(it.id) }
        }
        val newList = DeleteEngine.applyDeletion(_state.value.clips, ids)
        _state.update {
            it.copy(clips = newList, selectedClipId = null, currentPosMs = 0L)
        }
        cleanupStaleBeats()
        updateHistoryFlags()
    }

    fun deleteClipWithoutLinked() {
        val sel = _state.value.selectedClip ?: return
        pushHistory()
        val list = _state.value.clips.toMutableList()
        list.removeAll { it.id == sel.id }
        _state.update { it.copy(clips = list, selectedClipId = null) }
        cleanupStaleBeats()
        updateHistoryFlags()
    }

    private fun selectedClipboardIds(state: EditorState): Set<String> {
        val ids = if (state.multiSelectedIds.isNotEmpty()) {
            state.multiSelectedIds.toMutableSet()
        } else {
            setOfNotNull(state.selectedClipId).toMutableSet()
        }
        var changed: Boolean
        do {
            changed = false
            state.clips.forEach { clip ->
                if (clip.id !in ids) return@forEach
                val relatedIds = state.clips.asSequence()
                    .filter { related ->
                        (clip.linkedId != null && related.linkedId == clip.linkedId) ||
                                (clip.linkGroupId != null &&
                                        related.linkGroupId == clip.linkGroupId)
                    }
                    .map { it.id }
                relatedIds.forEach { if (ids.add(it)) changed = true }
            }
        } while (changed)
        return ids
    }

    fun copySelectedClips() {
        val s = _state.value
        val ids = selectedClipboardIds(s)
        val selected = s.clips.filter { it.id in ids }
        if (selected.isEmpty()) return
        clipboardClips = selected
        clipboardIsCut = false
        _state.update { it.copy(clipboardAction = "paste") }
    }

    fun cutSelectedClips() {
        val s = _state.value
        val ids = selectedClipboardIds(s)
        val selected = s.clips.filter { it.id in ids }
        if (selected.isEmpty()) return

        clipboardClips = selected
        clipboardIsCut = true
        pushHistory()
        _state.update {
            it.copy(
                clips = it.clips.filterNot { clip -> clip.id in ids },
                selectedClipId = null,
                multiSelectedIds = emptySet(),
                clipboardAction = "paste"
            )
        }
        cleanupStaleBeats()
        updateHistoryFlags()
    }

    fun pasteClipsAtPlayhead() {
        val s = _state.value
        if (clipboardClips.isEmpty()) {
            _state.update { it.copy(clipboardAction = null) }
            return
        }

        val anchorStartMs = clipboardClips.minOf { it.timelineStartMs }
        val maxTrackByType = s.clips.groupBy { it.isAudio }
            .mapValues { (_, clips) -> clips.maxOfOrNull { it.trackIndex } ?: -1 }
        val placedTracks = mutableListOf<EditorClip>()

        fun findPasteTrack(source: EditorClip, startMs: Long): Int {
            val duration = source.durationMs
            val highestTrack = maxOf(
                maxTrackByType[source.isAudio] ?: -1,
                placedTracks.filter { it.isAudio == source.isAudio }
                    .maxOfOrNull { it.trackIndex } ?: -1
            )
            for (track in source.trackIndex..(highestTrack + 1)) {
                val occupied = s.clips.any {
                    it.isAudio == source.isAudio && it.trackIndex == track &&
                            it.timelineStartMs < startMs + duration &&
                            startMs < it.timelineEndMs
                } || placedTracks.any {
                    it.isAudio == source.isAudio && it.trackIndex == track &&
                            it.timelineStartMs < startMs + duration &&
                            startMs < it.timelineEndMs
                }
                if (!occupied) return track
            }
            return maxOf(source.trackIndex, highestTrack + 1)
        }

        val linkedIds = clipboardClips.mapNotNull { it.linkedId }.distinct()
            .associateWith { "lk-${UUID.randomUUID()}" }
        val groupIds = clipboardClips.mapNotNull { it.linkGroupId }.distinct()
            .associateWith { "lg-${UUID.randomUUID()}" }

        clipboardClips.forEach { source ->
            val startMs = (s.currentPosMs + source.timelineStartMs - anchorStartMs)
                .coerceAtLeast(0L)
            val pasted = source.copy(
                id = UUID.randomUUID().toString(),
                name = if (clipboardIsCut) source.name else "${source.name} copy",
                timelineStartMs = startMs,
                trackIndex = findPasteTrack(source, startMs),
                linkedId = source.linkedId?.let { linkedIds[it] },
                linkGroupId = source.linkGroupId?.let { groupIds[it] }
            )
            placedTracks.add(pasted)
        }
        if (placedTracks.isEmpty()) return

        pushHistory()
        val allClips = s.clips + placedTracks
        val primary = placedTracks.firstOrNull { !it.isAudio } ?: placedTracks.first()
        _state.update {
            it.copy(
                clips = allClips,
                selectedClipId = primary.id,
                selectedTrackIndex = primary.trackIndex,
                selectedIsAudio = primary.isAudio,
                multiSelectedIds = placedTracks.map { clip -> clip.id }.toSet(),
                visualLayerCount = maxOf(
                    it.visualLayerCount,
                    (allClips.filterNot { clip -> clip.isAudio }
                        .maxOfOrNull { clip -> clip.trackIndex } ?: -1) + 1
                ),
                audioLayerCount = maxOf(
                    it.audioLayerCount,
                    (allClips.filter { clip -> clip.isAudio }
                        .maxOfOrNull { clip -> clip.trackIndex } ?: -1) + 1
                ),
                clipboardAction = null
            )
        }
        clipboardClips = emptyList()
        clipboardIsCut = false
        updateHistoryFlags()
    }

    fun duplicateCurrentClip() {
        val sel = _state.value.selectedClip ?: return
        val s = _state.value
        pushHistory()
        val copies = DuplicateEngine.duplicateAt(
            source = sel, allClips = s.clips, playheadMs = s.currentPosMs
        )
        if (copies.isEmpty()) return
        val list = s.clips.toMutableList()
        list.addAll(copies)
        val maxVisual = list.filter { !it.isAudio }.maxOfOrNull { it.trackIndex } ?: 0
        val maxAudio = list.filter { it.isAudio }.maxOfOrNull { it.trackIndex } ?: 0
        val primary = copies.first()
        _state.update {
            it.copy(
                clips = list,
                selectedClipId = primary.id,
                selectedTrackIndex = primary.trackIndex,
                selectedIsAudio = primary.isAudio,
                visualLayerCount = maxOf(it.visualLayerCount, maxVisual + 1),
                audioLayerCount = maxOf(it.audioLayerCount, maxAudio + 1)
            )
        }
        updateHistoryFlags()
    }

    fun addFreezeFrame(
        context: Context,
        durationMs: Long,
        onError: (String) -> Unit = {}
    ) {
        val stateAtRequest = _state.value
        val source = stateAtRequest.selectedClip?.takeIf {
            it.isVisualClip && it.type.startsWith("video/")
        } ?: return
        val freezeAtMs = stateAtRequest.currentPosMs
        if (freezeAtMs !in source.timelineStartMs until source.timelineEndMs ||
            durationMs <= 0L
        ) return

        viewModelScope.launch {
            val freezeClip = try {
                withContext(Dispatchers.IO) {
                    FreezeEngine.captureFreezeClip(context, source, freezeAtMs, durationMs)
                }
            } catch (error: Exception) {
                Log.e("EditorViewModel", "Failed to create freeze frame", error)
                onError(error.message ?: "Could not create freeze frame.")
                return@launch
            }

            val currentState = _state.value
            val currentSource = currentState.clips.firstOrNull { it.id == source.id }
            if (currentSource == null ||
                currentSource.uri != source.uri ||
                currentSource.timelineStartMs != source.timelineStartMs ||
                currentSource.sourceStartMs != source.sourceStartMs ||
                currentSource.sourceEndMs != source.sourceEndMs
            ) {
                freezeClip.uri.path?.let { java.io.File(it).delete() }
                onError("Video or playhead changed before the freeze frame was ready. Try again.")
                return@launch
            }

            val freezeEndMs = freezeAtMs + durationMs
            val list = currentState.clips.toMutableList()
            val sourceIndex = list.indexOfFirst { it.id == source.id }
            if (sourceIndex < 0) return@launch
            val pairedAudio = source.linkedId?.let { linkId ->
                list.firstOrNull {
                    it.linkedId == linkId && it.isAudio && !it.isAudioEffectClip
                }
            }
            val splitAudio = pairedAudio?.let {
                FreezeEngine.splitLinkedAudioAroundFreeze(it, freezeAtMs, durationMs)
            }.orEmpty()
            val tailLinkedId = if (splitAudio.size > 1) {
                "lk-${UUID.randomUUID()}"
            } else null
            list.removeAt(sourceIndex)
            list.addAll(
                FreezeEngine.splitAroundFreeze(
                    source,
                    freezeClip,
                    freezeAtMs,
                    tailLinkedId
                )
            )

            if (pairedAudio != null && splitAudio.isNotEmpty()) {
                val audioIndex = list.indexOfFirst { it.id == pairedAudio.id }
                if (audioIndex >= 0) {
                    list.removeAt(audioIndex)
                    list.addAll(
                        splitAudio.mapIndexed { index, clip ->
                            if (index == splitAudio.lastIndex && splitAudio.size > 1) {
                                clip.copy(linkedId = tailLinkedId)
                            } else clip
                        }
                    )
                }
            }

            for (index in list.indices) {
                val clip = list[index]
                if (clip.id != source.id &&
                    clip.trackIndex == source.trackIndex &&
                    !clip.isAudio &&
                    clip.timelineStartMs >= source.timelineEndMs
                ) {
                    list[index] = clip.copy(timelineStartMs = clip.timelineStartMs + durationMs)
                }
            }

            pushHistory()
            _state.update {
                it.copy(
                    clips = list,
                    selectedClipId = freezeClip.id,
                    selectedTrackIndex = freezeClip.trackIndex,
                    selectedIsAudio = false,
                    multiSelectedIds = emptySet()
                )
            }
            updateHistoryFlags()
        }
    }

    fun setSpeed(speed: Float) {
        val clamped = speed.coerceIn(SpeedEngine.MIN_SPEED, SpeedEngine.MAX_SPEED)
        val s = _state.value
        val targetIds: Set<String> = if (s.multiSelectedIds.isNotEmpty()) {
            s.multiSelectedIds
        } else setOfNotNull(s.selectedClipId)
        if (targetIds.isEmpty()) return

        val allIds = targetIds.toMutableSet()
        s.clips.filter { it.id in targetIds && it.linkedId != null }.forEach { c ->
            s.clips.filter { it.linkedId == c.linkedId }.forEach { allIds.add(it.id) }
        }
        val list = s.clips.toMutableList()
        for (i in list.indices) {
            if (list[i].id in allIds) list[i] = list[i].copy(speed = clamped)
        }
        _state.update { it.copy(clips = list, speed = clamped) }
    }

    fun resetSpeed() {
        pushHistory()
        setSpeed(1.0f)
        updateHistoryFlags()
    }

    fun setVolume(v: Float) {
        val clamped = com.moody.moodyvideoeditor.utils.VolumeEngine.clamp(v)
        forEachSelectedClip { it.copy(volume = clamped) }
        _state.update { it.copy(volume = clamped) }
    }

    fun getSelectedBaseDurationMs(): Long =
        _state.value.selectedClip?.let { SpeedEngine.getBaseDuration(it) } ?: 0L

    // ═══════════════════════════════════════════════════════════
    //  TEXT
    // ═══════════════════════════════════════════════════════════

    fun createTextClip(initial: TextState = TextState(content = "Text")) {
        val s = _state.value
        val clip = EditorClip(
            id = UUID.randomUUID().toString(), uri = Uri.EMPTY,
            name = "📝 ${initial.content.take(18).ifBlank { "Text" }}",
            type = "text/plain",
            sourceStartMs = 0L, sourceEndMs = 3000L,
            timelineStartMs = s.currentPosMs, trackIndex = 0,
            isAudio = false, sourceTotalMs = Long.MAX_VALUE,
            textState = initial
        )
        pushHistory()
        addClipOnNewLayer(clip, (s.selectedClip?.trackIndex ?: 0) + 1)
        updateHistoryFlags()
    }

    fun createTextClipAtTime(textState: TextState, startMs: Long, endMs: Long) {
        createTextClipsAtTimes(listOf(TimedTextClip(textState, startMs, endMs)))
    }

    fun createTextClipsAtTimes(textClips: List<TimedTextClip>) {
        if (textClips.isEmpty()) return

        val state = _state.value
        val clips = state.clips.toMutableList()
        var visualLayerCount = state.visualLayerCount
        var lastClip: EditorClip? = null

        textClips.forEach { request ->
            val durationMs = (request.endMs - request.startMs).coerceAtLeast(50L)
            var trackIndex = 0
            while (clips.any { clip ->
                    !clip.isAudio && clip.trackIndex == trackIndex &&
                            clip.timelineStartMs < request.startMs + durationMs &&
                            request.startMs < clip.timelineEndMs
                }
            ) {
                trackIndex++
            }

            val clip = EditorClip(
                id = UUID.randomUUID().toString(),
                uri = Uri.EMPTY,
                name = "📝 ${request.textState.content.take(18).ifBlank { "Text" }}",
                type = "text/plain",
                sourceStartMs = 0L,
                sourceEndMs = durationMs,
                timelineStartMs = request.startMs,
                trackIndex = trackIndex,
                isAudio = false,
                sourceTotalMs = Long.MAX_VALUE,
                textState = request.textState
            )
            clips.add(clip)
            visualLayerCount = maxOf(visualLayerCount, trackIndex + 1)
            lastClip = clip
        }

        val selectedClip = lastClip ?: return
        pushHistory()
        _state.update {
            it.copy(
                clips = clips,
                visualLayerCount = visualLayerCount,
                selectedClipId = selectedClip.id,
                selectedTrackIndex = selectedClip.trackIndex,
                selectedIsAudio = false
            )
        }
        updateHistoryFlags()
    }

    fun createSpotlightClip(): EditorClip {
        val s = _state.value
        val durationMs = 5_000L
        val clip = EditorClip(
            id = UUID.randomUUID().toString(),
            uri = Uri.EMPTY,
            name = "Spotlight",
            type = "spotlight/plain",
            sourceStartMs = 0L,
            sourceEndMs = durationMs,
            timelineStartMs = s.currentPosMs,
            trackIndex = 0,
            isAudio = false,
            sourceTotalMs = Long.MAX_VALUE,
            spotlight = SpotlightState()
        )
        pushHistory()
        val added = addClipOnNewLayer(clip, (s.selectedClip?.trackIndex ?: 0) + 1)
        updateHistoryFlags()
        return added
    }

    fun updateSpotlight(layerId: String, spotlight: SpotlightState) {
        val current = _state.value
        val clip = current.clips.firstOrNull { it.id == layerId && it.isSpotlightClip }
            ?: return
        val currentTimeSec = KeyframeStore.clipLocalTimeSeconds(
            current.currentPosMs, clip.timelineStartMs, clip.durationMs
        )
        updateClipDirect(layerId) { clip ->
            if (!clip.isSpotlightClip) return@updateClipDirect clip
            val base = SpotlightAnimation.sample(
                clip.spotlight ?: SpotlightState(),
                clip.keyframes,
                currentTimeSec
            )
            clip.copy(
                spotlight = spotlight,
                keyframes = SpotlightAnimation.updateAutoKeyframes(
                    base,
                    spotlight,
                    clip.keyframes,
                    currentTimeSec
                )
            )
        }

    }

    fun removeSpotlight(layerId: String) {
        val clip = _state.value.clips.firstOrNull { it.id == layerId } ?: return
        if (!clip.isSpotlightClip) return
        pushHistory()
        val list = _state.value.clips.toMutableList()
        list.removeAll { it.id == layerId }
        _state.update { it.copy(clips = list, selectedClipId = null) }
        updateHistoryFlags()
    }

    fun updateLayerShadow(layerId: String, shadow: LayerShadowState) {
        updateClipDirect(layerId) { clip ->
            if (clip.isAudio) clip else clip.copy(layerShadow = shadow)
        }
    }

    fun updateClipTextState(clipId: String, newState: TextState) {
        updateClipDirect(clipId) { clip ->
            if (clip.isTextClip) {
                clip.copy(
                    textState = newState,
                    name = "📝 ${newState.content.take(18).ifBlank { "Text" }}"
                )
            } else clip
        }
    }

    fun updateClipTextTexture(clipId: String, newState: TextState) {
        val state = _state.value
        val clip = state.clips.firstOrNull { it.id == clipId } ?: return
        if (!clip.isTextClip) return

        val oldState = clip.textState ?: return
        val localTimeSec = KeyframeStore.clipLocalTimeSeconds(
            state.currentPosMs, clip.timelineStartMs, clip.durationMs
        )
        var keyframes = clip.keyframes
        if (oldState.textureScale != newState.textureScale) {
            keyframes = KeyframeStore.autoKeyframeIfActive(
                keyframes, "textTexture.scale", localTimeSec, newState.textureScale
            )
        }
        if (oldState.textureOffsetX != newState.textureOffsetX) {
            keyframes = KeyframeStore.autoKeyframeIfActive(
                keyframes, "textTexture.x", localTimeSec, newState.textureOffsetX
            )
        }
        if (oldState.textureOffsetY != newState.textureOffsetY) {
            keyframes = KeyframeStore.autoKeyframeIfActive(
                keyframes, "textTexture.y", localTimeSec, newState.textureOffsetY
            )
        }
        if (oldState.textureRotation != newState.textureRotation) {
            keyframes = KeyframeStore.autoKeyframeIfActive(
                keyframes, "textTexture.rotation", localTimeSec, newState.textureRotation
            )
        }
        updateClipDirect(clipId) { current ->
            if (current.isTextClip) current.copy(textState = newState, keyframes = keyframes)
            else current
        }
    }

    fun updateTextGradientPoint(
        clipId: String,
        isStartPoint: Boolean,
        x: Float,
        y: Float
    ) {
        updateClipDirect(clipId) { clip ->
            val textState = clip.textState ?: return@updateClipDirect clip
            val updatedTextState = if (isStartPoint) {
                textState.copy(
                    gradientStartX = x.coerceIn(0f, 1f),
                    gradientStartY = y.coerceIn(0f, 1f)
                )
            } else {
                textState.copy(
                    gradientEndX = x.coerceIn(0f, 1f),
                    gradientEndY = y.coerceIn(0f, 1f)
                )
            }
            clip.copy(textState = updatedTextState)
        }
    }

    fun updateSelectedText(newState: TextState) {
        val s = _state.value
        val targetIds = if (s.multiSelectedIds.isNotEmpty()) {
            s.multiSelectedIds
        } else {
            setOfNotNull(s.selectedClipId)
        }
        val selectedTextClips = s.clips.filter { it.id in targetIds && it.isTextClip }
        if (selectedTextClips.isEmpty()) return

        val baseState = selectedTextClips
            .firstOrNull { it.id == s.selectedClipId }
            ?.textState ?: selectedTextClips.first().textState ?: TextState()
        val isMultiTextSelection = selectedTextClips.size > 1
        val updatedClips = s.clips.map { clip ->
            val currentText = clip.textState
            if (clip.id !in targetIds || !clip.isTextClip || currentText == null) {
                clip
            } else {
                val updatedText = if (isMultiTextSelection) {
                    currentText.applyChangedTextProperties(baseState, newState)
                } else {
                    newState
                }
                val localTimeSec = KeyframeStore.clipLocalTimeSeconds(
                    s.currentPosMs, clip.timelineStartMs, clip.durationMs
                )
                var keyframes = clip.keyframes
                val animatedValues = listOf(
                    KeyframeLibrary.TEXT_SIZE to
                            (currentText.fontSize.toFloat() to updatedText.fontSize.toFloat()),
                    KeyframeLibrary.TEXT_LETTER_SPACING to
                            (currentText.letterSpacing to updatedText.letterSpacing),
                    KeyframeLibrary.TEXT_OPACITY to
                            (currentText.opacity to updatedText.opacity)
                )
                animatedValues.forEach { (property, values) ->
                    if (values.first != values.second) {
                        keyframes = KeyframeStore.autoKeyframeIfActive(
                            keyframes, property, localTimeSec, values.second
                        )
                    }
                }
                clip.copy(
                    textState = updatedText,
                    keyframes = keyframes,
                    name = "📝 ${updatedText.content.take(18).ifBlank { "Text" }}"
                )
            }
        }
        if (updatedClips == s.clips) return
        pushHistory()
        _state.update { it.copy(clips = updatedClips) }
        updateHistoryFlags()
    }

    fun applySelectedTextTypographyPreset(presetId: String) {
        val state = _state.value
        val clip = state.selectedClip?.takeIf { it.isTextClip } ?: return
        val text = clip.textState ?: return
        val preset = TypographyAnimationPresetLibrary.find(presetId) ?: return
        val tracks = TypographyAnimationPresetLibrary.keyframesFor(
            preset = preset,
            baseSize = text.fontSize,
            baseLetterSpacing = text.letterSpacing,
            durationSec = clip.durationMs / 1000f
        )
        val keyframes = clip.keyframes +
                (KeyframeLibrary.TEXT_SIZE to tracks.getValue(KeyframeLibrary.TEXT_SIZE)) +
                (KeyframeLibrary.TEXT_LETTER_SPACING to
                        tracks.getValue(KeyframeLibrary.TEXT_LETTER_SPACING))
        if (keyframes == clip.keyframes) return
        pushHistory()
        updateClipDirect(clip.id) { it.copy(keyframes = keyframes) }
        updateHistoryFlags()
    }

    fun updateSelectedTextAlignment(alignment: String) {
        val normalizedAlignment = alignment.lowercase()
        if (normalizedAlignment !in setOf("left", "center", "right")) return
        val s = _state.value
        val targetIds = if (s.multiSelectedIds.isNotEmpty()) {
            s.multiSelectedIds
        } else {
            setOfNotNull(s.selectedClipId)
        }
        val updatedClips = s.clips.map { clip ->
            val textState = clip.textState
            if (clip.id in targetIds && clip.isTextClip && textState != null) {
                clip.copy(textState = textState.copy(alignment = normalizedAlignment))
            } else {
                clip
            }
        }
        if (updatedClips == s.clips) return
        pushHistory()
        _state.update { it.copy(clips = updatedClips) }
        updateHistoryFlags()
    }

    fun getSelectedTextState(): TextState {
        val s = _state.value
        return s.selectedClip?.textState
            ?: s.clips.firstOrNull {
                it.id in s.multiSelectedIds && it.isTextClip
            }?.textState
            ?: TextState()
    }

    private fun TextState.applyChangedTextProperties(
        base: TextState,
        updated: TextState
    ): TextState = copy(
        fontFamily = if (updated.fontFamily != base.fontFamily) updated.fontFamily else fontFamily,
        fontSize = if (updated.fontSize != base.fontSize) updated.fontSize else fontSize,
        fontWeight = if (updated.fontWeight != base.fontWeight) updated.fontWeight else fontWeight,
        fontStyle = if (updated.fontStyle != base.fontStyle) updated.fontStyle else fontStyle,
        color = if (updated.color != base.color) updated.color else color,
        strokeEnabled = if (updated.strokeEnabled != base.strokeEnabled) updated.strokeEnabled else strokeEnabled,
        strokeWidth = if (updated.strokeWidth != base.strokeWidth) updated.strokeWidth else strokeWidth,
        strokeColor = if (updated.strokeColor != base.strokeColor) updated.strokeColor else strokeColor,
        glowEnabled = if (updated.glowEnabled != base.glowEnabled) updated.glowEnabled else glowEnabled,
        glowColor = if (updated.glowColor != base.glowColor) updated.glowColor else glowColor,
        glowRadius = if (updated.glowRadius != base.glowRadius) updated.glowRadius else glowRadius,
        gradientEnabled = if (updated.gradientEnabled != base.gradientEnabled) updated.gradientEnabled else gradientEnabled,
        gradientColor1 = if (updated.gradientColor1 != base.gradientColor1) updated.gradientColor1 else gradientColor1,
        gradientColor2 = if (updated.gradientColor2 != base.gradientColor2) updated.gradientColor2 else gradientColor2,
        gradientAngle = if (updated.gradientAngle != base.gradientAngle) updated.gradientAngle else gradientAngle,
        gradientStartX = if (updated.gradientStartX != base.gradientStartX) updated.gradientStartX else gradientStartX,
        gradientStartY = if (updated.gradientStartY != base.gradientStartY) updated.gradientStartY else gradientStartY,
        gradientEndX = if (updated.gradientEndX != base.gradientEndX) updated.gradientEndX else gradientEndX,
        gradientEndY = if (updated.gradientEndY != base.gradientEndY) updated.gradientEndY else gradientEndY,
        shadowEnabled = if (updated.shadowEnabled != base.shadowEnabled) updated.shadowEnabled else shadowEnabled,
        shadowColor = if (updated.shadowColor != base.shadowColor) updated.shadowColor else shadowColor,
        shadowBlur = if (updated.shadowBlur != base.shadowBlur) updated.shadowBlur else shadowBlur,
        shadowOffsetX = if (updated.shadowOffsetX != base.shadowOffsetX) updated.shadowOffsetX else shadowOffsetX,
        shadowOffsetY = if (updated.shadowOffsetY != base.shadowOffsetY) updated.shadowOffsetY else shadowOffsetY,
        alignment = if (updated.alignment != base.alignment) updated.alignment else alignment,
        letterSpacing = if (updated.letterSpacing != base.letterSpacing) updated.letterSpacing else letterSpacing,
        lineHeight = if (updated.lineHeight != base.lineHeight) updated.lineHeight else lineHeight,
        tracking = if (updated.tracking != base.tracking) updated.tracking else tracking,
        positionX = if (updated.positionX != base.positionX) updated.positionX else positionX,
        positionY = if (updated.positionY != base.positionY) updated.positionY else positionY,
        anchorX = if (updated.anchorX != base.anchorX) updated.anchorX else anchorX,
        anchorY = if (updated.anchorY != base.anchorY) updated.anchorY else anchorY,
        maxWidth = if (updated.maxWidth != base.maxWidth) updated.maxWidth else maxWidth,
        scale = if (updated.scale != base.scale) updated.scale else scale,
        rotation = if (updated.rotation != base.rotation) updated.rotation else rotation,
        opacity = if (updated.opacity != base.opacity) updated.opacity else opacity,
        animation = if (updated.animation != base.animation) updated.animation else animation,
        animationDuration = if (updated.animationDuration != base.animationDuration) updated.animationDuration else animationDuration,
        templateId = if (updated.templateId != base.templateId) updated.templateId else templateId,
        backgroundEnabled = if (updated.backgroundEnabled != base.backgroundEnabled) updated.backgroundEnabled else backgroundEnabled,
        backgroundType = if (updated.backgroundType != base.backgroundType) updated.backgroundType else backgroundType,
        backgroundColor1 = if (updated.backgroundColor1 != base.backgroundColor1) updated.backgroundColor1 else backgroundColor1,
        backgroundColor2 = if (updated.backgroundColor2 != base.backgroundColor2) updated.backgroundColor2 else backgroundColor2,
        backgroundGradientEnabled = if (updated.backgroundGradientEnabled != base.backgroundGradientEnabled) updated.backgroundGradientEnabled else backgroundGradientEnabled,
        backgroundGradientAngle = if (updated.backgroundGradientAngle != base.backgroundGradientAngle) updated.backgroundGradientAngle else backgroundGradientAngle,
        backgroundOpacity = if (updated.backgroundOpacity != base.backgroundOpacity) updated.backgroundOpacity else backgroundOpacity,
        backgroundPaddingX = if (updated.backgroundPaddingX != base.backgroundPaddingX) updated.backgroundPaddingX else backgroundPaddingX,
        backgroundPaddingY = if (updated.backgroundPaddingY != base.backgroundPaddingY) updated.backgroundPaddingY else backgroundPaddingY,
        backgroundCornerRadius = if (updated.backgroundCornerRadius != base.backgroundCornerRadius) updated.backgroundCornerRadius else backgroundCornerRadius,
        backgroundBrushSeed = if (updated.backgroundBrushSeed != base.backgroundBrushSeed) updated.backgroundBrushSeed else backgroundBrushSeed,
        captionPresetId = if (updated.captionPresetId != base.captionPresetId) updated.captionPresetId else captionPresetId,
        captionHighlightColor = if (updated.captionHighlightColor != base.captionHighlightColor) updated.captionHighlightColor else captionHighlightColor,
        captionPresetAnimation = if (updated.captionPresetAnimation != base.captionPresetAnimation) updated.captionPresetAnimation else captionPresetAnimation,
        captionPresetAnimationDuration = if (updated.captionPresetAnimationDuration != base.captionPresetAnimationDuration) updated.captionPresetAnimationDuration else captionPresetAnimationDuration
    )

    fun removeSelectedText() {
        val sel = _state.value.selectedClip ?: return
        if (!sel.isTextClip) return
        pushHistory()
        val list = _state.value.clips.toMutableList()
        list.removeAll { it.id == sel.id }
        _state.update { it.copy(clips = list, selectedClipId = null) }
        updateHistoryFlags()
    }

    fun getSelectedAnimation(): String {
        val sel = _state.value.selectedClip ?: return "none"
        return when {
            sel.isTextClip -> sel.textState?.animation ?: "none"
            else -> "none"
        }
    }

    fun getSelectedAnimationDuration(): Float {
        val sel = _state.value.selectedClip ?: return 0.6f
        return when {
            sel.isTextClip -> sel.textState?.animationDuration ?: 0.6f
            else -> 0.6f
        }
    }

    fun setSelectedAnimation(animation: String) {
        forEachSelectedClip { clip ->
            when {
                clip.isTextClip && clip.textState != null ->
                    clip.copy(textState = clip.textState.copy(animation = animation))
                else -> clip
            }
        }
    }

    fun setSelectedAnimationDuration(duration: Float) {
        forEachSelectedClip { clip ->
            when {
                clip.isTextClip && clip.textState != null ->
                    clip.copy(textState = clip.textState.copy(animationDuration = duration))
                else -> clip
            }
        }
    }

    fun setTextAnimation(animation: String) {
        forEachSelectedClip { clip ->
            if (clip.isTextClip && clip.textState != null) {
                clip.copy(textState = clip.textState.copy(animation = animation))
            } else clip
        }
    }

    fun updateTextPositionDirect(clipId: String, x: Float, y: Float) {
        val s = _state.value
        val clip = s.clips.firstOrNull { it.id == clipId } ?: return
        if (!clip.isTextClip) return
        val currentTimeSec = KeyframeStore.clipLocalTimeSeconds(
            s.currentPosMs, clip.timelineStartMs, clip.durationMs
        )
        val hasAnyKf = KeyframeStore.hasAnyKeyframes(clip.keyframes)
        updateClipDirect(clipId) { c ->
            val st = c.textState ?: return@updateClipDirect c
            var kf = c.keyframes
            if (hasAnyKf) {
                kf = KeyframeStore.autoKeyframeIfActive(kf, "x", currentTimeSec, x)
                kf = KeyframeStore.autoKeyframeIfActive(kf, "y", currentTimeSec, y)
                c.copy(textState = st.copy(positionX = x, positionY = y), keyframes = kf)
            } else c.copy(textState = st.copy(positionX = x, positionY = y))
        }
    }

    fun updateTextTransformDirect(clipId: String, scale: Float, rotation: Float) {
        val s = _state.value
        val clip = s.clips.firstOrNull { it.id == clipId } ?: return
        if (!clip.isTextClip) return
        val currentTimeSec = KeyframeStore.clipLocalTimeSeconds(
            s.currentPosMs, clip.timelineStartMs, clip.durationMs
        )
        val hasAnyKf = KeyframeStore.hasAnyKeyframes(clip.keyframes)
        updateClipDirect(clipId) { c ->
            val st = c.textState ?: return@updateClipDirect c
            var kf = c.keyframes
            if (hasAnyKf) {
                kf = KeyframeStore.autoKeyframeIfActive(kf, "scale", currentTimeSec, scale)
                kf = KeyframeStore.autoKeyframeIfActive(
                    kf, "rotation", currentTimeSec, rotation
                )
                c.copy(
                    textState = st.copy(scale = scale, rotation = rotation),
                    keyframes = kf
                )
            } else c.copy(textState = st.copy(scale = scale, rotation = rotation))
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  MASK — PER-PROPERTY KEYFRAMES
    // ═══════════════════════════════════════════════════════════

    private fun currentMaskAtPlayhead(clip: EditorClip): MaskState {
        val timeSec = KeyframeStore.clipLocalTimeSeconds(
            _state.value.currentPosMs,
            clip.timelineStartMs,
            clip.durationMs
        )
        return MaskEngine.sampleAt(clip.mask, timeSec)
    }

    fun selectedMaskAtPlayhead(): MaskState {
        val clip = _state.value.selectedClip ?: return MaskState()
        return currentMaskAtPlayhead(clip)
    }

    private fun updateSelectedMask(transform: (MaskState, Float) -> MaskState) {
        _state.update { current ->
            val selectedId = current.selectedClipId ?: return@update current
            val index = current.clips.indexOfFirst { it.id == selectedId }
            if (index < 0) return@update current

            val clip = current.clips[index]
            val timeSec = KeyframeStore.clipLocalTimeSeconds(
                current.currentPosMs,
                clip.timelineStartMs,
                clip.durationMs
            )
            val clips = current.clips.toMutableList()
            clips[index] = clip.copy(mask = transform(clip.mask, timeSec))
            current.copy(clips = clips)
        }
    }

    private fun upsertMaskKeyframe(
        keyframes: List<Keyframe>,
        timeSec: Float,
        value: Float
    ): List<Keyframe> {
        val index = keyframes.indexOfFirst { it.time == timeSec }
        val updated = keyframes.toMutableList()
        if (index >= 0) {
            updated[index] = updated[index].copy(value = value)
        } else {
            updated.add(Keyframe(time = timeSec, value = value))
        }
        return updated.sortedBy { it.time }
    }

    fun updateMask(newMask: MaskState) {
        updateSelectedMask { _, _ -> newMask }
    }

    fun updateMaskProperty(prop: String, value: Float) {
        if (!value.isFinite()) return
        updateSelectedMask { currentMask, timeSec ->
            val track = currentMask.propertyKeyframes[prop].orEmpty()
            val updatedMask = currentMask.withValue(prop, value)
            val normalizedValue = updatedMask.valueOf(prop)
            if (track.isEmpty()) {
                updatedMask
            } else {
                updatedMask.copy(
                    propertyKeyframes = currentMask.propertyKeyframes +
                            (prop to upsertMaskKeyframe(track, timeSec, normalizedValue))
                )
            }
        }
    }

    fun toggleMaskPropertyKeyframe(prop: String) {
        updateSelectedMask { currentMask, timeSec ->
            val track = currentMask.propertyKeyframes[prop].orEmpty()
            val existingIndex = track.indexOfFirst { it.time == timeSec }
            val newTrack = if (existingIndex >= 0) {
                track.filterIndexed { index, _ -> index != existingIndex }
            } else {
                upsertMaskKeyframe(
                    track,
                    timeSec,
                    MaskEngine.sampleProperty(currentMask, prop, timeSec)
                )
            }
            val tracks = currentMask.propertyKeyframes.toMutableMap()
            if (newTrack.isEmpty()) tracks.remove(prop) else tracks[prop] = newTrack
            currentMask.copy(propertyKeyframes = tracks)
        }
    }

    fun hasMaskPropertyKeyframeAtPlayhead(prop: String): Boolean {
        val current = _state.value
        val clip = current.selectedClip ?: return false
        val timeSec = KeyframeStore.clipLocalTimeSeconds(
            current.currentPosMs,
            clip.timelineStartMs,
            clip.durationMs
        )
        return clip.mask.propertyKeyframes[prop].orEmpty().any { it.time == timeSec }
    }

    fun hasAnyMaskKeyframeForProp(prop: String): Boolean {
        val sel = _state.value.selectedClip ?: return false
        return sel.mask.propertyKeyframes[prop]?.isNotEmpty() == true
    }

    fun setMaskType(type: MaskType) {
        val sel = _state.value.selectedClip ?: return
        val oldMask = sel.mask
        val newBase = MaskLibrary.defaultFor(type)
        val merged = newBase.copy(
            isInverted = oldMask.isInverted,
            propertyKeyframes = oldMask.propertyKeyframes,
            customPoints = oldMask.customPoints,
            customClosed = oldMask.customClosed
        )
        updateClipDirect(sel.id) { it.copy(mask = merged) }
    }

    fun addMaskKeyframe() {
        updateSelectedMask { mask, timeSec ->
            if (!mask.isActive) return@updateSelectedMask mask
            val tracks = mask.propertyKeyframes.toMutableMap()
            MaskAnimatableProps.forType(mask.type).forEach { prop ->
                tracks[prop] = upsertMaskKeyframe(
                    tracks[prop].orEmpty(),
                    timeSec,
                    MaskEngine.sampleProperty(mask, prop, timeSec)
                )
            }
            mask.copy(propertyKeyframes = tracks)
        }
    }

    fun clearMaskKeyframes() {
        val sel = _state.value.selectedClip ?: return
        updateClipDirect(sel.id) {
            it.copy(
                mask = it.mask.copy(
                    propertyKeyframes = emptyMap(),
                    keyframes = emptyList()
                )
            )
        }
    }

    fun hasMaskKeyframeAtPlayhead(): Boolean {
        val current = _state.value
        val clip = current.selectedClip ?: return false
        val localTimeMs = (current.currentPosMs - clip.timelineStartMs)
            .coerceIn(0L, clip.durationMs)
        val timeSec = localTimeMs / 1000f
        return clip.mask.propertyKeyframes.values.any { track ->
            track.any { it.time == timeSec }
        } || clip.mask.keyframes.any {
            it.timeMs == localTimeMs
        }
    }

    fun moveMaskBy(deltaX: Float, deltaY: Float) {
        if (!deltaX.isFinite() || !deltaY.isFinite()) return
        val state = _state.value
        val clip = state.selectedClip ?: return
        val sampled = currentMaskAtPlayhead(clip)
        updateMaskProperty("positionX", sampled.positionX + deltaX)
        updateMaskProperty("positionY", sampled.positionY + deltaY)
    }

    fun addMaskPoint(x: Float, y: Float) {
        val sel = _state.value.selectedClip ?: return
        val m = currentMaskAtPlayhead(sel)
        val baseMask = if (m.type != MaskType.CUSTOM)
            MaskState(type = MaskType.CUSTOM)
        else m
        val newPoint = MaskPoint(
            x = x.coerceIn(0f, 1f),
            y = y.coerceIn(0f, 1f)
        )
        updateMask(
            baseMask.copy(
                type = MaskType.CUSTOM,
                customPoints = baseMask.customPoints + newPoint
            )
        )
    }

    fun moveMaskAnchor(index: Int, x: Float, y: Float) {
        val sel = _state.value.selectedClip ?: return
        val m = currentMaskAtPlayhead(sel)
        if (index !in m.customPoints.indices) return
        val old = m.customPoints[index]
        val updated = old.copy(
            x = x.coerceIn(0f, 1f),
            y = y.coerceIn(0f, 1f)
        )
        updateMask(MaskEngine.updatePoint(m, index, updated))
    }

    fun moveMaskHandle(index: Int, isIn: Boolean, dx: Float, dy: Float) {
        val sel = _state.value.selectedClip ?: return
        val m = currentMaskAtPlayhead(sel)
        if (index !in m.customPoints.indices) return
        val old = m.customPoints[index]
        val updated = if (isIn) {
            old.copy(
                handleInX = dx, handleInY = dy,
                handleOutX = -dx, handleOutY = -dy,
                hasHandles = true
            )
        } else {
            old.copy(
                handleOutX = dx, handleOutY = dy,
                handleInX = -dx, handleInY = -dy,
                hasHandles = true
            )
        }
        updateMask(MaskEngine.updatePoint(m, index, updated))
    }

    fun toggleMaskPointSmooth(index: Int) {
        val sel = _state.value.selectedClip ?: return
        val m = currentMaskAtPlayhead(sel)
        if (index !in m.customPoints.indices) return
        val old = m.customPoints[index]
        val updated = if (old.hasHandles) {
            old.copy(
                handleInX = 0f, handleInY = 0f,
                handleOutX = 0f, handleOutY = 0f,
                hasHandles = false
            )
        } else {
            val (hx, hy) = MaskEngine.autoHandleOffsets(
                m.customPoints, index, 0.25f
            )
            old.copy(
                handleInX = -hx, handleInY = -hy,
                handleOutX = hx, handleOutY = hy,
                hasHandles = true
            )
        }
        updateMask(MaskEngine.updatePoint(m, index, updated))
    }

    fun deleteMaskPoint(index: Int) {
        val sel = _state.value.selectedClip ?: return
        val m = currentMaskAtPlayhead(sel)
        if (index !in m.customPoints.indices) return
        updateMask(MaskEngine.removePoint(m, index))
    }

    fun removeLastMaskPoint() {
        val sel = _state.value.selectedClip ?: return
        val m = currentMaskAtPlayhead(sel)
        if (m.customPoints.isEmpty()) return
        updateMask(m.copy(customPoints = m.customPoints.dropLast(1)))
    }

    fun clearMaskPoints() {
        val sel = _state.value.selectedClip ?: return
        val m = currentMaskAtPlayhead(sel)
        updateMask(m.copy(customPoints = emptyList()))
    }

    fun setMaskClosed(closed: Boolean) {
        val sel = _state.value.selectedClip ?: return
        val m = currentMaskAtPlayhead(sel)
        updateMask(m.copy(customClosed = closed))
    }

    fun removeMask() {
        val sel = _state.value.selectedClip ?: return
        updateClipDirect(sel.id) { it.copy(mask = MaskState()) }
    }

    // ═══════════════════════════════════════════════════════════
    //  OVERLAY / EFFECT / ADJUSTMENT
    // ═══════════════════════════════════════════════════════════

    fun applyOverlay(newOverlay: OverlayState) {
        val s = _state.value
        val overlayStartMs = s.currentPosMs.coerceAtLeast(0L)
        val overlayDurationMs = DEFAULT_APPLIED_LAYER_DURATION_MS
        val baseClip = s.selectedClip
        val topVisualTrack = s.clips
            .filter {
                it.isVisualClip &&
                        overlayStartMs >= it.timelineStartMs &&
                        overlayStartMs < it.timelineEndMs
            }
            .maxOfOrNull { it.trackIndex }
            ?: baseClip?.trackIndex
            ?: 0
        val clip = EditorClip(
            id = UUID.randomUUID().toString(), uri = Uri.EMPTY,
            name = "🎬 ${newOverlay.type}", type = "overlay/plain",
            sourceStartMs = 0L, sourceEndMs = overlayDurationMs,
            timelineStartMs = overlayStartMs,
            trackIndex = 0, isAudio = false, sourceTotalMs = Long.MAX_VALUE,
            overlay = newOverlay
        )
        pushHistory()
        addClipOnNewLayer(clip, topVisualTrack + 1)
        updateHistoryFlags()
    }

    fun updateSelectedOverlay(newOverlay: OverlayState) {
        val s = _state.value
        val hasOverlaySelected = s.multiSelectedIds.isNotEmpty() &&
                s.clips.any { it.isOverlayClip && it.id in s.multiSelectedIds }
        if (hasOverlaySelected) {
            forEachSelectedClip { clip ->
                if (clip.isOverlayClip) clip.copy(overlay = newOverlay) else clip
            }
            return
        }
        val sel = s.selectedClip
        if (sel == null || !sel.isOverlayClip) {
            applyOverlay(newOverlay)
            return
        }
        updateClipDirect(sel.id) { it.copy(overlay = newOverlay) }
    }

    fun removeSelectedOverlay() {
        val sel = _state.value.selectedClip ?: return
        if (!sel.isOverlayClip) return
        pushHistory()
        val list = _state.value.clips.toMutableList()
        list.removeAll { it.id == sel.id }
        _state.update { it.copy(clips = list, selectedClipId = null) }
        updateHistoryFlags()
    }

    fun updateFilters(newFilters: FilterState) {
        forEachSelectedClip { clip ->
            if (clip.isVisualClip) clip.copy(filters = newFilters) else clip
        }
    }

    fun resetFilters() {
        forEachSelectedClip { clip ->
            if (clip.isVisualClip) clip.copy(filters = FilterState()) else clip
        }
    }

    fun createFilterLayer(
        filters: FilterState,
        durationMs: Long = DEFAULT_APPLIED_LAYER_DURATION_MS
    ) {
        val s = _state.value
        val effectiveDur = durationMs.coerceAtLeast(EditorClip.MIN_DURATION_MS)

        val topTrack = (s.clips
            .filter {
                !it.isAudio && s.currentPosMs >= it.timelineStartMs &&
                        s.currentPosMs < it.timelineEndMs
            }
            .maxOfOrNull { it.trackIndex } ?: 0) + 1

        val clip = EditorClip(
            id = UUID.randomUUID().toString(), uri = Uri.EMPTY,
            name = "🎨 Filter", type = "filter/plain",
            sourceStartMs = 0L, sourceEndMs = effectiveDur,
            timelineStartMs = s.currentPosMs, trackIndex = topTrack,
            isAudio = false, sourceTotalMs = Long.MAX_VALUE, filters = filters
        )
        pushHistory()
        val list = s.clips.toMutableList()
        list.add(clip)
        _state.update {
            it.copy(
                clips = list, selectedClipId = clip.id,
                selectedTrackIndex = topTrack, selectedIsAudio = false,
                visualLayerCount = maxOf(it.visualLayerCount, topTrack + 1),
                multiSelectedIds = emptySet()
            )
        }
        updateHistoryFlags()
    }

    fun updateFilterLayer(layerId: String, filters: FilterState) {
        updateClipDirect(layerId) { c ->
            if (c.isFilterLayerClip) c.copy(filters = filters) else c
        }
    }

    fun createColorMatte(
        color: Long = ColorMatteDefaults.DEFAULT_COLOR,
        opacity: Float = ColorMatteDefaults.DEFAULT_OPACITY,
        durationMs: Long = ColorMatteDefaults.DEFAULT_DURATION_MS,
        style: ColorMatteStyle = ColorMatteStyle(mode = ColorMatteMode.SOLID, solidColor = color)
    ) {
        val s = _state.value
        val baseClip = s.selectedClip
        val effectiveDur = durationMs.coerceIn(
            ColorMatteDefaults.MIN_DURATION_MS,
            ColorMatteDefaults.MAX_DURATION_MS
        )

        val targetTrack = (baseClip?.trackIndex ?: 0) + 1

        val matteFilters = FilterState(opacity = opacity.coerceIn(0f, 100f))

        val clip = EditorClip(
            id = UUID.randomUUID().toString(),
            uri = Uri.EMPTY,
            name = "🎨 Color Matte",
            type = "matte/plain",
            sourceStartMs = 0L,
            sourceEndMs = effectiveDur,
            timelineStartMs = s.currentPosMs,
            trackIndex = 0,
            isAudio = false,
            sourceTotalMs = Long.MAX_VALUE,
            filters = matteFilters,
            matteStyle = style
        )

        pushHistory()
        addClipOnNewLayer(clip, targetTrack)
        updateHistoryFlags()
    }

    fun updateColorMatte(
        layerId: String,
        style: ColorMatteStyle,
        opacity: Float,
        durationMs: Long
    ) {
        val clampedOpacity = opacity.coerceIn(0f, 100f)
        val clampedDur = durationMs.coerceIn(
            ColorMatteDefaults.MIN_DURATION_MS,
            ColorMatteDefaults.MAX_DURATION_MS
        )

        updateClipDirect(layerId) { clip ->
            if (!clip.isColorMatteClip) clip
            else clip.copy(
                matteStyle = style,
                filters = clip.filters.copy(opacity = clampedOpacity),
                sourceEndMs = clip.sourceStartMs + clampedDur
            )
        }
    }

    fun updateColorMatte(
        layerId: String,
        color: Long,
        opacity: Float,
        durationMs: Long
    ) {
        val clip = _state.value.clips.firstOrNull { it.id == layerId } ?: return
        if (!clip.isColorMatteClip) return
        val newStyle = clip.matteStyle.copy(
            mode = ColorMatteMode.SOLID,
            solidColor = color
        )
        updateColorMatte(layerId, newStyle, opacity, durationMs)
    }

    fun removeColorMatte(layerId: String) {
        val clip = _state.value.clips.firstOrNull { it.id == layerId } ?: return
        if (!clip.isColorMatteClip) return
        pushHistory()
        val list = _state.value.clips.toMutableList()
        list.removeAll { it.id == layerId }
        _state.update { it.copy(clips = list, selectedClipId = null) }
        updateHistoryFlags()
    }

    fun removeFilterLayer(layerId: String) {
        val clip = _state.value.clips.firstOrNull { it.id == layerId } ?: return
        if (!clip.isFilterLayerClip) return
        pushHistory()
        val list = _state.value.clips.toMutableList()
        list.removeAll { it.id == layerId }
        _state.update {
            it.copy(
                clips = list, selectedClipId = null,
                multiSelectedIds = emptySet(), previewFilters = null
            )
        }
        updateHistoryFlags()
    }

    fun updateColorWheel(newState: ColorWheelState) {
        forEachSelectedClip { clip ->
            if (clip.isVisualClip) clip.copy(colorWheel = newState) else clip
        }
    }

    fun resetColorWheel() {
        forEachSelectedClip { clip ->
            if (clip.isVisualClip) clip.copy(colorWheel = ColorWheelState()) else clip
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  🟩 CHROMA KEY
    // ═══════════════════════════════════════════════════════════

    fun updateChromaKey(newState: ChromaKeyState) {
        val sel = _state.value.selectedClip ?: return
        pushHistory()
        updateClipDirect(sel.id) { it.copy(chromaKey = newState) }
        updateHistoryFlags()
    }

    fun clearChromaKey() {
        val sel = _state.value.selectedClip ?: return
        pushHistory()
        updateClipDirect(sel.id) { it.copy(chromaKey = ChromaKeyState()) }
        updateHistoryFlags()
    }

    // ═══════════════════════════════════════════════════════════
    //  🟩 EYEDROPPER — "tap on preview to pick"
    // ═══════════════════════════════════════════════════════════

    private val _eyedropperActive = kotlinx.coroutines.flow.MutableStateFlow(false)
    val eyedropperActive: kotlinx.coroutines.flow.StateFlow<Boolean> =
        _eyedropperActive.asStateFlow()

    fun startEyedropper() {
        _eyedropperActive.value = true
    }

    fun cancelEyedropper() {
        _eyedropperActive.value = false
    }

    fun applyPickedColor(colorLong: Long) {
        val sel = _state.value.selectedClip ?: return
        val current = sel.chromaKey
        updateClipDirect(sel.id) {
            it.copy(
                chromaKey = current.copy(
                    enabled = true,
                    targetColor = colorLong,
                    hasColor = true
                )
            )
        }
        updateHistoryFlags()
        _eyedropperActive.value = false
    }

    fun updateRatio(state: RatioState) = _state.update {
        it.copy(aspectRatio = state.key, aspectMode = 0)
    }

    fun updateBeats(state: BeatsState) = _state.update {
        it.copy(
            beatsDetected = state.detected,
            beatsCount = state.count,
            beatsFilter = state.filter,
            beatTimesMs = state.beatTimesMs,
            beatStrengths = state.beatStrengths
        )
    }.also {
        val selectedAudioId = _state.value.selectedClip
            ?.takeIf { c -> c.isAudio && !c.isAudioEffectClip }
            ?.id
        syncBeatsToVisualizer(state, selectedAudioId)
    }

    fun clearBeats() = _state.update {
        it.copy(
            beatsDetected = false, beatsCount = 0,
            beatsFilter = "all", beatTimesMs = emptyList()
        )
    }

    fun toggleMute() = _state.update { it.copy(isMuted = !it.isMuted) }
    fun setRotation(deg: Int) = _state.update { it.copy(rotation = deg) }
    fun setAspectMode(mode: Int) = _state.update { it.copy(aspectMode = mode) }
    fun setAudioFx(fx: String) = _state.update { it.copy(audioFx = fx) }
    fun setSoundFx(fx: String) = _state.update { it.copy(soundFx = fx) }

    fun setClipAudioFx(clipId: String, fx: String) {
        pushHistory()
        updateClipDirect(clipId) { it.copy(audioFx = fx) }
        updateHistoryFlags()
    }

    fun setClipAudioFxIntensity(clipId: String, intensity: Float) {
        val clamped = intensity.coerceIn(0f, 200f)
        updateClipDirect(clipId) { it.copy(audioFxIntensity = clamped) }
    }

    fun setClipSoundFx(clipId: String, fx: String) {
        pushHistory()
        updateClipDirect(clipId) { it.copy(soundFx = fx) }
        updateHistoryFlags()
    }

    fun setClipSoundFxIntensity(clipId: String, intensity: Float) {
        val clamped = intensity.coerceIn(0f, 200f)
        updateClipDirect(clipId) { it.copy(soundFxIntensity = clamped) }
    }

    fun createAudioFxLayer(fx: String, intensity: Float, durationMs: Long = 3000L) {
        val s = _state.value
        val effectiveDur = durationMs.coerceAtLeast(500L)
        val trackIdx = findFreeAudioTrack(s.currentPosMs, effectiveDur)

        val label = com.moody.moodyvideoeditor.utils.AudioEngine
            .AUDIO_FX.firstOrNull { it.key == fx }?.label ?: fx

        val clip = EditorClip(
            id = UUID.randomUUID().toString(), uri = Uri.EMPTY,
            name = "🎙️ $label", type = "audiofx/plain",
            sourceStartMs = 0L, sourceEndMs = effectiveDur,
            timelineStartMs = s.currentPosMs, trackIndex = trackIdx,
            isAudio = true, sourceTotalMs = Long.MAX_VALUE,
            audioFx = fx, audioFxIntensity = intensity
        )
        pushHistory()
        val list = s.clips.toMutableList()
        list.add(clip)
        _state.update {
            it.copy(
                clips = list, selectedClipId = clip.id,
                selectedTrackIndex = trackIdx, selectedIsAudio = true,
                audioLayerCount = maxOf(it.audioLayerCount, trackIdx + 1),
                multiSelectedIds = emptySet()
            )
        }
        updateHistoryFlags()
    }

    fun createSoundFxLayer(fx: String, intensity: Float, durationMs: Long = 3000L) {
        val s = _state.value
        val effectiveDur = durationMs.coerceAtLeast(500L)
        val trackIdx = findFreeAudioTrack(s.currentPosMs, effectiveDur)

        val label = com.moody.moodyvideoeditor.utils.AudioEngine
            .SOUND_FX.firstOrNull { it.key == fx }?.label ?: fx

        val clip = EditorClip(
            id = UUID.randomUUID().toString(), uri = Uri.EMPTY,
            name = "🔔 $label", type = "soundfx/plain",
            sourceStartMs = 0L, sourceEndMs = effectiveDur,
            timelineStartMs = s.currentPosMs, trackIndex = trackIdx,
            isAudio = true, sourceTotalMs = Long.MAX_VALUE,
            soundFx = fx, soundFxIntensity = intensity
        )
        pushHistory()
        val list = s.clips.toMutableList()
        list.add(clip)
        _state.update {
            it.copy(
                clips = list, selectedClipId = clip.id,
                selectedTrackIndex = trackIdx, selectedIsAudio = true,
                audioLayerCount = maxOf(it.audioLayerCount, trackIdx + 1),
                multiSelectedIds = emptySet()
            )
        }
        updateHistoryFlags()
    }

    fun changeTransformProperty(prop: String, value: Float) {
        val sel = _state.value.selectedClip ?: return
        val currentTimeSec = KeyframeStore.clipLocalTimeSeconds(
            _state.value.currentPosMs, sel.timelineStartMs, sel.durationMs
        )

        val s = _state.value
        val isMulti = s.multiSelectedIds.size > 1 && sel.id in s.multiSelectedIds

        if (isMulti) {
            when (prop) {
                "x", "y" -> {
                    val (cx, cy) = getClipPos(sel)
                    val nx = if (prop == "x") value else cx
                    val ny = if (prop == "y") value else cy
                    updateSelectedPositionBulk(sel.id, nx, ny)
                    return
                }
                "scale" -> {
                    val cr = getClipRotation(sel)
                    updateSelectedTransformBulk(sel.id, value, cr)
                    return
                }
                "rotation" -> {
                    val cs = getClipScale(sel)
                    updateSelectedTransformBulk(sel.id, cs, value)
                    return
                }
            }
        }

        val updated = when (prop) {
            "x" -> if (sel.isTextClip && sel.textState != null)
                sel.copy(textState = sel.textState.copy(positionX = value))
            else if (sel.isVisualizerClip && sel.visualizer != null)
                sel.copy(visualizer = sel.visualizer.copy(positionX = value / 100f))
            else sel.copy(offsetX = (value - 50f) / 100f)

            "y" -> if (sel.isTextClip && sel.textState != null)
                sel.copy(textState = sel.textState.copy(positionY = value))
            else if (sel.isVisualizerClip && sel.visualizer != null)
                sel.copy(visualizer = sel.visualizer.copy(positionY = value / 100f))
            else sel.copy(offsetY = (value - 50f) / 100f)

            "scale" -> if (sel.isTextClip && sel.textState != null)
                sel.copy(textState = sel.textState.copy(scale = value))
            else if (sel.isVisualizerClip && sel.visualizer != null)
                sel.copy(visualizer = sel.visualizer.copy(size = value / 100f * 0.32f))
            else sel.copy(scale = value / 100f)

            "rotation" -> if (sel.isTextClip && sel.textState != null)
                sel.copy(textState = sel.textState.copy(rotation = value))
            else if (sel.isVisualizerClip && sel.visualizer != null)
                sel.copy(visualizer = sel.visualizer.copy(rotation = value))
            else sel.copy(rotation = value)

            "anchorX" -> if (sel.isTextClip && sel.textState != null)
                sel.copy(textState = sel.textState.copy(anchorX = value))
            else sel

            "anchorY" -> if (sel.isTextClip && sel.textState != null)
                sel.copy(textState = sel.textState.copy(anchorY = value))
            else sel

            "cropL" -> sel.copy(cropL = value)
            "cropR" -> sel.copy(cropR = value)
            "cropT" -> sel.copy(cropT = value)
            "cropB" -> sel.copy(cropB = value)
            "layer.opacity" -> sel.copy(layerOpacity = value.coerceIn(0f, 100f))
            else -> sel
        }

        val hasAnyKf = KeyframeStore.hasAnyKeyframes(updated.keyframes)
        val finalClip = if (hasAnyKf) {
            updated.copy(
                keyframes = KeyframeStore.autoKeyframeIfActive(
                    updated.keyframes, prop, currentTimeSec, value
                )
            )
        } else updated

        if (finalClip != sel) pushHistory()
        updateClipDirect(sel.id) { finalClip }
    }

    fun updateClipKeyframeValue(
        clipId: String,
        prop: String,
        timeSec: Float,
        value: Float
    ) {
        val s = _state.value
        val clip = s.clips.firstOrNull { it.id == clipId } ?: return

        val list = KeyframeStore.getKeyframes(clip.keyframes, prop).toMutableList()
        val idx = list.indexOfFirst { abs(it.time - timeSec) < 0.05f }

        if (idx >= 0) {
            list[idx] = list[idx].copy(value = value)
        } else {
            list.add(Keyframe(timeSec, value, Keyframe.DEFAULT_EASE))
            list.sortBy { it.time }
        }

        val newKeyframes = clip.keyframes + (prop to list)
        updateClipDirect(clipId) { it.copy(keyframes = newKeyframes) }
    }

    fun toggleKeyframeAtPlayhead(prop: String) {
        val sel = _state.value.selectedClip ?: return
        val currentTimeSec = KeyframeStore.clipLocalTimeSeconds(
            _state.value.currentPosMs, sel.timelineStartMs, sel.durationMs
        )
        val existing = KeyframeStore.hasKeyframeAt(sel.keyframes, prop, currentTimeSec)

        val updated = if (existing) {
            sel.copy(
                keyframes = KeyframeStore.removeKeyframe(
                    sel.keyframes, prop, currentTimeSec
                )
            )
        } else {
            val live = TransformApplier.resolveLive(sel, currentTimeSec)
            val currentValue = if (sel.isTextClip && sel.textState != null &&
                prop in setOf(
                    KeyframeLibrary.TEXT_SIZE,
                    KeyframeLibrary.TEXT_LETTER_SPACING,
                    KeyframeLibrary.TEXT_OPACITY
                )
            ) {
                val textState = sel.textState
                when (prop) {
                    KeyframeLibrary.TEXT_SIZE -> KeyframeStore.sample(
                        sel.keyframes, prop, currentTimeSec, textState.fontSize.toFloat()
                    )
                    KeyframeLibrary.TEXT_LETTER_SPACING -> KeyframeStore.sample(
                        sel.keyframes, prop, currentTimeSec, textState.letterSpacing
                    )
                    else -> KeyframeStore.sample(
                        sel.keyframes, prop, currentTimeSec, textState.opacity
                    )
                }
            } else if (prop == KeyframeLibrary.LAYER_OPACITY) {
                KeyframeStore.sample(
                    sel.keyframes, prop, currentTimeSec, sel.layerOpacity
                )
            } else if (sel.isSpotlightClip &&
                prop in SpotlightAnimation.properties
            ) {
                val sampled = SpotlightAnimation.sample(
                    sel.spotlight ?: SpotlightState(),
                    sel.keyframes,
                    currentTimeSec
                )
                SpotlightAnimation.value(sampled, prop)
            } else if (sel.isTextClip && sel.textState?.textureUri != null) {
                val textState = sel.textState!!
                when (prop) {
                    "textTexture.scale" -> KeyframeStore.sample(
                        sel.keyframes, prop, currentTimeSec, textState.textureScale
                    )
                    "textTexture.x" -> KeyframeStore.sample(
                        sel.keyframes, prop, currentTimeSec, textState.textureOffsetX
                    )
                    "textTexture.y" -> KeyframeStore.sample(
                        sel.keyframes, prop, currentTimeSec, textState.textureOffsetY
                    )
                    "textTexture.rotation" -> KeyframeStore.sample(
                        sel.keyframes, prop, currentTimeSec, textState.textureRotation
                    )
                    else -> when (prop) {
                        "x" -> live.x
                        "y" -> live.y
                        "scale" -> live.scale
                        "rotation" -> live.rotation
                        "anchorX" -> live.anchorX
                        "anchorY" -> live.anchorY
                        "cropL" -> live.cropL
                        "cropR" -> live.cropR
                        "cropT" -> live.cropT
                        "cropB" -> live.cropB
                        else -> 0f
                    }
                }
            } else when (prop) {
                "x" -> live.x
                "y" -> live.y
                "scale" -> live.scale
                "rotation" -> live.rotation
                "anchorX" -> live.anchorX
                "anchorY" -> live.anchorY
                "cropL" -> live.cropL
                "cropR" -> live.cropR
                "cropT" -> live.cropT
                "cropB" -> live.cropB
                else -> 0f
            }
            sel.copy(
                keyframes = KeyframeStore.setKeyframe(
                    sel.keyframes, prop, currentTimeSec, currentValue
                )
            )
        }
        if (updated != sel) pushHistory()
        updateClipDirect(sel.id) { updated }
    }

    fun toggleKeyframeAll() {
        val sel = _state.value.selectedClip ?: return
        val currentTimeSec = KeyframeStore.clipLocalTimeSeconds(
            _state.value.currentPosMs, sel.timelineStartMs, sel.durationMs
        )
        if (sel.isSpotlightClip) {
            val hasSpotlightKeyframe = SpotlightAnimation.properties.any {
                KeyframeStore.hasKeyframeAt(sel.keyframes, it, currentTimeSec)
            }
            val updatedKeyframes = if (hasSpotlightKeyframe) {
                SpotlightAnimation.properties.fold(sel.keyframes) { keyframes, property ->
                    KeyframeStore.removeKeyframe(keyframes, property, currentTimeSec)
                }
            } else {
                val sampled = SpotlightAnimation.sample(
                    sel.spotlight ?: SpotlightState(),
                    sel.keyframes,
                    currentTimeSec
                )
                SpotlightAnimation.properties.fold(sel.keyframes) { keyframes, property ->
                    KeyframeStore.setKeyframe(
                        keyframes,
                        property,
                        currentTimeSec,
                        SpotlightAnimation.value(sampled, property)
                    )
                }
            }
            val updated = sel.copy(keyframes = updatedKeyframes)
            if (updated != sel) pushHistory()
            updateClipDirect(sel.id) { updated }
            updateHistoryFlags()
            return
        }
        val textureProperties = if (sel.isTextClip && sel.textState?.textureUri != null) {
            listOf(
                "textTexture.scale", "textTexture.x", "textTexture.y",
                "textTexture.rotation"
            )
        } else emptyList()
        val hasAny = KeyframeStore.hasAnyKeyframeAt(sel.keyframes, currentTimeSec)

        val updated = if (hasAny) {
            sel.copy(
                keyframes = KeyframeStore.removeAllKeyframesAtTime(
                    sel.keyframes, currentTimeSec
                )
            )
        } else {
            var kf = sel.keyframes
            val base = TransformApplier.baseOf(sel)
            kf = KeyframeStore.setKeyframe(kf, "x", currentTimeSec, base.x)
            kf = KeyframeStore.setKeyframe(kf, "y", currentTimeSec, base.y)
            kf = KeyframeStore.setKeyframe(kf, "scale", currentTimeSec, base.scale)
            kf = KeyframeStore.setKeyframe(kf, "rotation", currentTimeSec, base.rotation)
            kf = KeyframeStore.setKeyframe(kf, "anchorX", currentTimeSec, base.anchorX)
            kf = KeyframeStore.setKeyframe(kf, "anchorY", currentTimeSec, base.anchorY)
            kf = KeyframeStore.setKeyframe(kf, "cropL", currentTimeSec, base.cropL)
            kf = KeyframeStore.setKeyframe(kf, "cropR", currentTimeSec, base.cropR)
            kf = KeyframeStore.setKeyframe(kf, "cropT", currentTimeSec, base.cropT)
            kf = KeyframeStore.setKeyframe(kf, "cropB", currentTimeSec, base.cropB)
            sel.textState?.takeIf { textureProperties.isNotEmpty() }?.let { text ->
                kf = KeyframeStore.setKeyframe(
                    kf, "textTexture.scale", currentTimeSec, text.textureScale
                )
                kf = KeyframeStore.setKeyframe(
                    kf, "textTexture.x", currentTimeSec, text.textureOffsetX
                )
                kf = KeyframeStore.setKeyframe(
                    kf, "textTexture.y", currentTimeSec, text.textureOffsetY
                )
                kf = KeyframeStore.setKeyframe(
                    kf, "textTexture.rotation", currentTimeSec, text.textureRotation
                )
            }
            sel.copy(keyframes = kf)
        }
        if (updated != sel) pushHistory()
        updateClipDirect(sel.id) { updated }
        updateHistoryFlags()
    }

    fun hasKeyframeAtPlayhead(): Boolean {
        val sel = _state.value.selectedClip ?: return false
        val currentTimeSec = KeyframeStore.clipLocalTimeSeconds(
            _state.value.currentPosMs, sel.timelineStartMs, sel.durationMs
        )
        return KeyframeStore.hasAnyKeyframeAt(sel.keyframes, currentTimeSec)
    }

    fun hasKeyframeForPropertyAtPlayhead(prop: String): Boolean {
        val sel = _state.value.selectedClip ?: return false
        val currentTimeSec = KeyframeStore.clipLocalTimeSeconds(
            _state.value.currentPosMs, sel.timelineStartMs, sel.durationMs
        )
        return KeyframeStore.hasKeyframeAt(sel.keyframes, prop, currentTimeSec)
    }

    fun setEaseAtPlayhead(ease: String) {
        val sel = _state.value.selectedClip ?: return
        val currentTimeSec = KeyframeStore.clipLocalTimeSeconds(
            _state.value.currentPosMs, sel.timelineStartMs, sel.durationMs
        )
        val updated = sel.copy(
            keyframes = KeyframeStore.setAllEasesAtTime(
                sel.keyframes, currentTimeSec, ease
            )
        )
        if (updated != sel) pushHistory()
        updateClipDirect(sel.id) { updated }
        updateHistoryFlags()
    }

    fun setTimelineKeyframeEases(ease: String) {
        updateKeyframeEases { clip ->
            clip.copy(
                keyframes = clip.keyframes.mapValues { (_, frames) ->
                    frames.map { it.copy(ease = ease) }
                }
            )
        }
    }

    private inline fun updateKeyframeEases(
        transform: (EditorClip) -> EditorClip
    ) {
        val current = _state.value
        val updated = current.clips.map(transform)
        if (updated == current.clips) return
        pushHistory()
        _state.update { it.copy(clips = updated) }
        updateHistoryFlags()
    }

    fun beginKeyframeGraphEdit() {
        if (keyframeGraphBaseline == null) {
            keyframeGraphBaseline = _state.value.clips.toList()
        }
    }

    fun commitKeyframeGraphEdit() {
        val baseline = keyframeGraphBaseline ?: return
        if (baseline != _state.value.clips) {
            history.push(baseline)
        }
        keyframeGraphBaseline = null
        updateHistoryFlags()
    }

    fun updateKeyframeInGraph(
        prop: String, oldTime: Float, newTime: Float, newValue: Float
    ) {
        val sel = _state.value.selectedClip ?: return
        val list = KeyframeStore.getKeyframes(sel.keyframes, prop).toMutableList()
        val idx = list.indexOfFirst { abs(it.time - oldTime) < 0.08f }
        if (idx < 0) return
        val clipDurSec = sel.durationMs / 1000f
        val t = newTime.coerceIn(0f, clipDurSec)
        val filtered = list.filterIndexed { i, kf ->
            i == idx || abs(kf.time - t) > 0.08f
        }.toMutableList()
        val realIdx = filtered.indexOfFirst { abs(it.time - oldTime) < 0.08f }
        if (realIdx >= 0) {
            filtered[realIdx] = Keyframe(t, newValue, list[idx].ease)
        } else {
            filtered.add(Keyframe(t, newValue, list[idx].ease))
        }
        filtered.sortBy { it.time }

        if (keyframeGraphBaseline == null) pushHistory()
        updateClipDirect(sel.id) {
            it.copy(keyframes = it.keyframes + (prop to filtered))
        }
        updateHistoryFlags()
    }

    fun deleteKeyframeFromGraph(prop: String, time: Float) {
        val sel = _state.value.selectedClip ?: return
        val updatedKeyframes = KeyframeStore.removeKeyframe(sel.keyframes, prop, time)
        if (updatedKeyframes == sel.keyframes) return
        pushHistory()
        updateClipDirect(sel.id) {
            it.copy(keyframes = updatedKeyframes)
        }
        updateHistoryFlags()
    }

    fun deleteKeyframesAtPlayhead(): Boolean {
        val sel = _state.value.selectedClip ?: return false
        val timeSec = KeyframeStore.clipLocalTimeSeconds(
            _state.value.currentPosMs, sel.timelineStartMs, sel.durationMs
        )
        val updatedKeyframes = KeyframeStore.removeAllKeyframesAtTime(
            sel.keyframes, timeSec
        )
        val updatedMaskProperties = sel.mask.propertyKeyframes.mapValues { (_, track) ->
            track.filter { abs(it.time - timeSec) > 0.05f }
        }.filterValues { it.isNotEmpty() }
        val timeMs = (timeSec * 1000f).toLong()
        val updatedMaskLegacy = sel.mask.keyframes.filter {
            abs(it.timeMs - timeMs) >= 50L
        }
        if (updatedKeyframes == sel.keyframes &&
            updatedMaskProperties == sel.mask.propertyKeyframes &&
            updatedMaskLegacy == sel.mask.keyframes
        ) return false

        pushHistory()
        updateClipDirect(sel.id) { clip ->
            clip.copy(
                keyframes = updatedKeyframes,
                mask = clip.mask.copy(
                    propertyKeyframes = updatedMaskProperties,
                    keyframes = updatedMaskLegacy
                )
            )
        }
        updateHistoryFlags()
        return true
    }

    fun resetAllTransform() {
        val sel = _state.value.selectedClip ?: return
        pushHistory()
        val updated = when {
            sel.isTextClip && sel.textState != null -> sel.copy(
                textState = sel.textState.copy(
                    positionX = 50f, positionY = 50f,
                    scale = 100f, rotation = 0f,
                    anchorX = 50f, anchorY = 50f
                ),
                keyframes = emptyMap()
            )

            sel.isVisualizerClip && sel.visualizer != null -> sel.copy(
                visualizer = sel.visualizer.copy(
                    positionX = 0.5f, positionY = 0.5f,
                    size = 0.32f, rotation = 0f
                ),
                keyframes = emptyMap()
            )

            else -> sel.copy(
                offsetX = 0f, offsetY = 0f, scale = 1f, rotation = 0f,
                cropL = 0f, cropR = 0f, cropT = 0f, cropB = 0f,
                keyframes = emptyMap()
            )
        }
        updateClipDirect(sel.id) { updated }
        updateHistoryFlags()
    }

    fun setClipScale(v: Float) {
        val clamped = v.coerceIn(0.1f, 5f)
        forEachSelectedClip { it.copy(scale = clamped) }
    }

    fun setClipRotation(v: Float) {
        forEachSelectedClip { it.copy(rotation = v) }
    }

    fun setClipOffset(x: Float, y: Float) {
        forEachSelectedClip { it.copy(offsetX = x, offsetY = y) }
    }

    fun setBlendMode(blendKey: String) {
        pushHistory()
        val targetIds: Set<String> = if (_state.value.multiSelectedIds.isNotEmpty()) {
            _state.value.multiSelectedIds
        } else setOfNotNull(_state.value.selectedClipId)
        if (targetIds.isEmpty()) return

        val list = _state.value.clips.toMutableList()
        for (i in list.indices) {
            if (list[i].id in targetIds) {
                list[i] = list[i].copy(blendMode = blendKey)
            }
        }
        _state.update { it.copy(clips = list) }
        updateHistoryFlags()
    }

    fun setBlendOpacity(opacity: Float) {
        val clamped = opacity.coerceIn(0f, 100f)
        val targetIds: Set<String> = if (_state.value.multiSelectedIds.isNotEmpty()) {
            _state.value.multiSelectedIds
        } else setOfNotNull(_state.value.selectedClipId)
        if (targetIds.isEmpty()) return

        val list = _state.value.clips.toMutableList()
        for (i in list.indices) {
            if (list[i].id in targetIds) {
                list[i] = list[i].copy(blendOpacity = clamped)
            }
        }
        _state.update { it.copy(clips = list) }
    }

    fun setCrop(l: Float, r: Float, t: Float, b: Float) {
        val clamped = com.moody.moodyvideoeditor.utils.CropHelper.clampAll(l, r, t, b)
        forEachSelectedClip {
            it.copy(
                cropL = clamped.l,
                cropR = clamped.r,
                cropT = clamped.t,
                cropB = clamped.b
            )
        }
    }

    fun setTransitionForClip(clipId: String, state: TransitionState) {
        updateClipDirect(clipId) { it.copy(transition = state) }
    }

    fun updateTransition(state: TransitionState) {
        forEachSelectedClip { it.copy(transition = state) }
    }

    fun removeTransition() {
        forEachSelectedClip { it.copy(transition = null) }
    }

    fun removeTransitionFor(clipId: String) {
        updateClipDirect(clipId) { it.copy(transition = null) }
    }

    fun setTransitionDuration(clipId: String, durationMs: Long) {
        updateClipDirect(clipId) { clip ->
            clip.transition?.let { t ->
                clip.copy(
                    transition = t.copy(durationMs = durationMs.coerceIn(200L, 3000L))
                )
            } ?: clip
        }
    }

    fun applyTransitionAll(key: String, durationSec: Float) {
        val durMs = (durationSec * 1000f).toLong().coerceIn(200L, 3000L)
        val list = _state.value.clips.toMutableList()
        for (i in list.indices) {
            val clip = list[i]
            if (!clip.isVisualClip) continue
            val hasLeft = list.any { other ->
                other.id != clip.id && other.isVisualClip &&
                        other.trackIndex == clip.trackIndex &&
                        abs(other.timelineEndMs - clip.timelineStartMs) < 100L
            }
            if (hasLeft) {
                list[i] = list[i].copy(
                    transition = TransitionState(key = key, durationMs = durMs)
                )
            }
        }
        pushHistory()
        _state.update { it.copy(clips = list) }
        updateHistoryFlags()
    }

    fun applyTransitionAt(key: String, timeSec: Float, durationSec: Float) {
        val durMs = (durationSec * 1000f).toLong().coerceIn(200L, 3000L)
        val targetMs = (timeSec * 1000f).toLong()
        val list = _state.value.clips.toMutableList()
        for (i in list.indices) {
            val clip = list[i]
            if (!clip.isVisualClip) continue
            val hasLeft = list.any { other ->
                other.id != clip.id && other.isVisualClip &&
                        other.trackIndex == clip.trackIndex &&
                        abs(other.timelineEndMs - clip.timelineStartMs) < 100L
            }
            if (hasLeft && abs(clip.timelineStartMs - targetMs) < 200L) {
                list[i] = list[i].copy(
                    transition = TransitionState(key = key, durationMs = durMs)
                )
            }
        }
        pushHistory()
        _state.update { it.copy(clips = list) }
        updateHistoryFlags()
    }

    fun applyLayerClipTransitions(
        layerNumber: Int, clipTransitionMap: Map<Int, String>
    ): Int {
        val trackIndex = layerNumber - 1
        if (trackIndex < 0) return 0
        val s = _state.value
        val layerClips = s.clips
            .filter { it.isVisualClip && !it.isAudio && it.trackIndex == trackIndex }
            .sortedBy { it.timelineStartMs }
        if (layerClips.isEmpty()) return 0

        pushHistory()
        val list = s.clips.toMutableList()
        var appliedCount = 0

        clipTransitionMap.forEach { (clipNum, transName) ->
            if (transName.equals("skip", ignoreCase = true) ||
                transName.equals("none", ignoreCase = true) ||
                transName.isBlank()
            ) return@forEach
            if (clipNum < 1 || clipNum > layerClips.size) return@forEach
            val targetClip = layerClips[clipNum - 1]
            val resolvedKey = resolveTransitionName(transName) ?: return@forEach
            val idx = list.indexOfFirst { it.id == targetClip.id }
            if (idx >= 0) {
                list[idx] = list[idx].copy(
                    transition = TransitionState(key = resolvedKey, durationMs = 500L)
                )
                appliedCount++
            }
        }

        _state.update { it.copy(clips = list) }
        updateHistoryFlags()
        return appliedCount
    }

    private fun resolveTransitionName(name: String): String? {
        val clean = name.trim().lowercase()
            .replace(" ", "").replace("_", "").replace("-", "")
        if (clean.isBlank()) return null

        val aliases = mapOf(
            "slide" to "slideLeft", "slideleft" to "slideLeft",
            "slideright" to "slideRight", "slideup" to "slideUp",
            "slidedown" to "slideDown", "push" to "slideLeft",
            "pushleft" to "slideLeft", "pushright" to "slideRight",
            "pushup" to "slideUp", "pushdown" to "slideDown",
            "panleft" to "panLeft", "panright" to "panRight",
            "panup" to "tiltUp", "pandown" to "tiltDown",
            "tiltup" to "tiltUp", "tiltdown" to "tiltDown",
            "fade" to "fade", "fadein" to "fade", "fadeout" to "fade",
            "dissolve" to "dissolve", "fadeblack" to "blinkFade",
            "fadewhite" to "whiteFlash", "zoom" to "zoomIn",
            "zoomin" to "zoomIn", "zoomout" to "zoomOut",
            "gaussianzoom" to "gaussianZoom", "wipe" to "wipeLeft",
            "wipeleft" to "wipeLeft", "wiperight" to "wipeRight",
            "wipeup" to "wipeUp", "wipedown" to "wipeDown",
            "linearwipe" to "linearWipe", "flash" to "whiteFlash",
            "flashwhite" to "whiteFlash", "whiteflash" to "whiteFlash",
            "glitch" to "glitch", "glitchfx" to "glitch",
            "rgbshift" to "rgbShift", "circle" to "circleMask",
            "circleopen" to "circleMask", "circleclose" to "circleMask",
            "circlemask" to "circleMask", "heart" to "heartPop",
            "heartpop" to "heartPop", "star" to "starBurst",
            "starburst" to "starBurst", "diamond" to "diamondReveal",
            "diamondreveal" to "diamondReveal", "clock" to "clockWipe",
            "clockwipe" to "clockWipe", "blur" to "gaussianZoom",
            "motionblur" to "motionWipeBlur"
        )

        aliases[clean]?.let { return it }
        TransitionLibrary.PRESETS.firstOrNull { it.key.lowercase() == clean }
            ?.let { return it.key }
        TransitionLibrary.PRESETS.firstOrNull {
            it.label.lowercase().replace(" ", "").replace("-", "") == clean
        }?.let { return it.key }
        TransitionLibrary.PRESETS.firstOrNull {
            val pClean = it.label.lowercase().replace(" ", "").replace("-", "")
            pClean.contains(clean) || clean.contains(pClean)
        }?.let { return it.key }
        return null
    }

    fun applyLayerTransitions(pattern: String, trackIdx: Int, isAudio: Boolean) {
        val parts = pattern.split(",").map { it.trim() }
        val patternList = parts.filter { it.lowercase() != "loop" }
        if (patternList.isEmpty()) return

        val list = _state.value.clips.toMutableList()
        val trackClips = list
            .filter { c -> c.trackIndex == trackIdx && c.isAudio == isAudio }
            .sortedBy { it.timelineStartMs }

        var patternIdx = 0
        for (i in 1 until trackClips.size) {
            val curr = trackClips[i]
            val prev = trackClips[i - 1]
            if (abs(prev.timelineEndMs - curr.timelineStartMs) >= 100L) continue

            val patternItem = patternList[patternIdx % patternList.size]
            if (patternItem.lowercase() != "null") {
                val key = patternItem.lowercase().replace(" ", "")
                val finalKey = when (key) {
                    "dissolve" -> "dissolve"
                    "slideleft" -> "slideLeft"
                    "slideright" -> "slideRight"
                    "zoomin" -> "zoomIn"
                    "zoomout" -> "zoomOut"
                    "fade" -> "fade"
                    else -> key
                }
                val globalIdx = list.indexOfFirst { it.id == curr.id }
                if (globalIdx >= 0) {
                    list[globalIdx] = list[globalIdx].copy(
                        transition = TransitionState(key = finalKey, durationMs = 500L)
                    )
                }
            }
            patternIdx++
        }

        pushHistory()
        _state.update { it.copy(clips = list) }
        updateHistoryFlags()
    }

    fun applyTemplate(
        templateId: String,
        customTexts: Map<String, String> = emptyMap(),
        startMs: Long = _state.value.currentPosMs,
        canvasWidthPx: Float = 720f
    ) {
        val template = TypographyTemplates.find(templateId) ?: return
        pushHistory()

        val list = _state.value.clips.toMutableList()
        var newLayerCount = _state.value.visualLayerCount

        for (node in template.nodes) {
            val state = TypographyTemplates.toTextState(
                node = node, canvasWidthPx = canvasWidthPx,
                customText = customTexts[node.nodeId]
            ).copy(templateId = templateId)

            val clipStart = startMs + node.timingOffsetMs
            val clipEnd = clipStart + node.durationMs

            val trackIdx = findOrCreateVisualTrack(
                preferredTrack = 0, startMs = clipStart,
                durMs = clipEnd - clipStart
            )

            val clip = EditorClip(
                id = UUID.randomUUID().toString(), uri = Uri.EMPTY,
                name = "📝 ${state.content.take(16).ifBlank { "Text" }}",
                type = "text/plain",
                sourceStartMs = 0L, sourceEndMs = clipEnd - clipStart,
                timelineStartMs = clipStart, trackIndex = trackIdx,
                isAudio = false, sourceTotalMs = Long.MAX_VALUE,
                textState = state
            )
            list.add(clip)
            newLayerCount = maxOf(newLayerCount, trackIdx + 1)
        }

        _state.update { it.copy(clips = list, visualLayerCount = newLayerCount) }
        updateHistoryFlags()
    }

    fun applyBeatAnimation(
        type: String, amount: Float, beatTimesMs: List<Long>
    ): Int {
        val sel = _state.value.selectedClip ?: return 0
        val clipStartMs = sel.timelineStartMs
        val clipEndMs = sel.timelineEndMs
        val clipDurSec = sel.durationMs / 1000f
        val base = TransformApplier.baseOf(sel)

        val decaySec = when (type) {
            "pulse", "pop" -> 0.15f
            "bounce" -> 0.25f
            "shake" -> 0.10f
            "scale", "rotate", "zoom" -> 0.20f
            else -> 0.15f
        }

        val newKf = mutableMapOf<String, MutableList<Keyframe>>()
        var appliedCount = 0

        beatTimesMs.forEach { beatMs ->
            if (beatMs < clipStartMs || beatMs > clipEndMs) return@forEach
            val localSec = (beatMs - clipStartMs) / 1000f
            if (localSec < 0f || localSec > clipDurSec) return@forEach

            val peakTime = localSec
            val returnTime = (localSec + decaySec).coerceAtMost(clipDurSec)

            when (type) {
                "pulse" -> {
                    val factor = 1f + (amount / 100f)
                    val peakScale = (base.scale * factor).coerceIn(10f, 500f)
                    addKf(newKf, "scale", peakTime, peakScale, "easeOut")
                    addKf(newKf, "scale", returnTime, base.scale, "easeOut")
                    appliedCount++
                }
                "scale" -> {
                    val peakScale = amount.coerceIn(10f, 500f)
                    addKf(newKf, "scale", peakTime, peakScale, "easeOut")
                    addKf(newKf, "scale", returnTime, base.scale, "easeOut")
                    appliedCount++
                }
                "bounce" -> {
                    val peakScale = (base.scale * (1f + amount / 100f)).coerceIn(10f, 500f)
                    addKf(newKf, "scale", peakTime, peakScale, "easeOutBack")
                    addKf(newKf, "scale", returnTime, base.scale, "easeOutBounce")
                    appliedCount++
                }
                "zoom" -> {
                    val factor = 1f + (amount / 200f)
                    val peakScale = (base.scale * factor).coerceIn(10f, 500f)
                    addKf(newKf, "scale", peakTime, peakScale, "easeOut")
                    addKf(newKf, "scale", returnTime, base.scale, "easeIn")
                    appliedCount++
                }
                "rotate" -> {
                    val peakRot = base.rotation + amount.coerceIn(-180f, 180f)
                    addKf(newKf, "rotation", peakTime, peakRot, "easeOut")
                    addKf(newKf, "rotation", returnTime, base.rotation, "easeOut")
                    appliedCount++
                }
                "shake" -> {
                    val amplitude = amount.coerceIn(5f, 200f) / 10f
                    val t1 = peakTime
                    val t2 = (peakTime + decaySec * 0.33f).coerceAtMost(clipDurSec)
                    val t3 = (peakTime + decaySec * 0.66f).coerceAtMost(clipDurSec)
                    val t4 = returnTime
                    addKf(newKf, "x", t1, base.x + amplitude, "linear")
                    addKf(newKf, "x", t2, base.x - amplitude, "linear")
                    addKf(newKf, "x", t3, base.x + amplitude * 0.5f, "linear")
                    addKf(newKf, "x", t4, base.x, "easeOut")
                    addKf(newKf, "y", t1, base.y - amplitude, "linear")
                    addKf(newKf, "y", t2, base.y + amplitude, "linear")
                    addKf(newKf, "y", t3, base.y - amplitude * 0.5f, "linear")
                    addKf(newKf, "y", t4, base.y, "easeOut")
                    appliedCount++
                }
                "shiftx" -> {
                    val peakX = base.x + amount.coerceIn(-50f, 50f)
                    addKf(newKf, "x", peakTime, peakX, "easeOut")
                    addKf(newKf, "x", returnTime, base.x, "easeOut")
                    appliedCount++
                }
                "shifty" -> {
                    val peakY = base.y + amount.coerceIn(-50f, 50f)
                    addKf(newKf, "y", peakTime, peakY, "easeOut")
                    addKf(newKf, "y", returnTime, base.y, "easeOut")
                    appliedCount++
                }
                "pop" -> {
                    val factor = 1f + (amount / 80f)
                    val peakScale = (base.scale * factor).coerceIn(10f, 500f)
                    addKf(newKf, "scale", peakTime, peakScale, "easeOutBack")
                    addKf(newKf, "scale", returnTime, base.scale, "easeOutElastic")
                    appliedCount++
                }
            }
        }

        if (appliedCount == 0) return 0

        val sortedKf = newKf.mapValues { (_, list) ->
            list.distinctBy { it.time }.sortedBy { it.time }
        }

        pushHistory()
        updateClipDirect(sel.id) { clip ->
            val merged = mutableMapOf<String, List<Keyframe>>()
            val allProps = (clip.keyframes.keys + sortedKf.keys).toSet()
            allProps.forEach { prop ->
                val old = clip.keyframes[prop] ?: emptyList()
                val new = sortedKf[prop] ?: emptyList()
                val combined = (old + new).distinctBy { it.time }.sortedBy { it.time }
                merged[prop] = combined
            }
            clip.copy(keyframes = merged)
        }
        updateHistoryFlags()
        return appliedCount
    }

    private fun addKf(
        map: MutableMap<String, MutableList<Keyframe>>,
        prop: String, time: Float, value: Float, ease: String
    ) {
        val list = map.getOrPut(prop) { mutableListOf() }
        list.add(Keyframe(time, value, ease))
    }

    fun undo() {
        val prev = history.undo(_state.value.clips) ?: return
        _state.update { it.copy(clips = prev) }
        updateHistoryFlags()
    }

    fun redo() {
        val next = history.redo(_state.value.clips) ?: return
        _state.update { it.copy(clips = next) }
        updateHistoryFlags()
    }

    fun saveAsTemplate(context: Context, name: String): Boolean {
        val s = _state.value
        if (s.clips.isEmpty()) return false

        val id = java.util.UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        val imageSlots = s.clips.count {
            it.type.startsWith("image/") && !it.isTextClip
        }
        val textSlots = s.clips.count { it.isTextClip }

        val meta = com.moody.moodyvideoeditor.data.SavedTemplateMeta(
            id = id,
            name = name.ifBlank { "Untitled Template" },
            createdAt = now,
            updatedAt = now,
            aspectRatio = s.aspectRatio,
            clipCount = s.clips.size,
            durationMs = s.totalDurationMs,
            imageSlotCount = imageSlots,
            textSlotCount = textSlots
        )

        return com.moody.moodyvideoeditor.data.SavedTemplateRepository
            .save(context, meta, s)
    }

    // ═══════════════════════════════════════════════════════════
    //  EFFECTS / ADJUSTMENTS / VISUALIZER
    // ═══════════════════════════════════════════════════════════

    fun applyEffectPreset(presetKey: String, presetLabel: String) {
        val preset = EffectLibrary.findByKey(presetKey) ?: return
        val s = _state.value
        val baseClip = s.selectedClip
        val startMs = s.currentPosMs.coerceAtLeast(0L)
        val durMs = DEFAULT_APPLIED_LAYER_DURATION_MS
        val baseTrack = baseClip?.trackIndex ?: 0

        val effectState = EffectState(
            kind = EffectState.KIND_EFFECT, presetKey = preset.key,
            filters = preset.filters, motion = preset.motion, overlay = preset.overlay
        )

        val clip = EditorClip(
            id = UUID.randomUUID().toString(), uri = Uri.EMPTY,
            name = "✨ $presetLabel", type = "effect/plain",
            sourceStartMs = 0L, sourceEndMs = durMs,
            timelineStartMs = startMs, trackIndex = 0, isAudio = false,
            sourceTotalMs = Long.MAX_VALUE, effectKeys = listOf(presetKey),
            effectState = effectState
        )
        pushHistory()
        addClipOnNewLayer(clip, baseTrack + 1)
        updateHistoryFlags()
    }

    fun createEffectLayerAt(presetKey: String, intensity: Float) {
        val preset = EffectLibrary.findByKey(presetKey) ?: return
        val s = _state.value
        val baseClip = s.selectedClip
        val startMs = s.currentPosMs.coerceAtLeast(0L)
        val durMs = DEFAULT_APPLIED_LAYER_DURATION_MS
        val baseTrack = baseClip?.trackIndex ?: 0
        val scaled = buildScaledEffectState(preset, intensity)

        val clip = EditorClip(
            id = UUID.randomUUID().toString(), uri = Uri.EMPTY,
            name = "✨ ${preset.label}", type = "effect/plain",
            sourceStartMs = 0L, sourceEndMs = durMs,
            timelineStartMs = startMs, trackIndex = 0, isAudio = false,
            sourceTotalMs = Long.MAX_VALUE, effectKeys = listOf(presetKey),
            effectState = scaled
        )
        pushHistory()
        addClipOnNewLayer(clip, baseTrack + 1)
        updateHistoryFlags()
    }

    fun createEffectLayerAtWithConfig(
        presetKey: String, intensity: Float, overlayConfig: OverlayConfig
    ) {
        val preset = EffectLibrary.findByKey(presetKey) ?: return
        val s = _state.value
        val baseClip = s.selectedClip
        val startMs = s.currentPosMs.coerceAtLeast(0L)
        val durMs = DEFAULT_APPLIED_LAYER_DURATION_MS
        val baseTrack = baseClip?.trackIndex ?: 0
        val scaled = buildScaledEffectState(preset, intensity)
            .copy(overlay = overlayConfig)

        val clip = EditorClip(
            id = UUID.randomUUID().toString(), uri = Uri.EMPTY,
            name = "✨ ${preset.label}", type = "effect/plain",
            sourceStartMs = 0L, sourceEndMs = durMs,
            timelineStartMs = startMs, trackIndex = 0, isAudio = false,
            sourceTotalMs = Long.MAX_VALUE, effectKeys = listOf(presetKey),
            effectState = scaled
        )
        pushHistory()
        addClipOnNewLayer(clip, baseTrack + 1)
        updateHistoryFlags()
    }

    fun updateEffectLayerIntensity(layerId: String, intensity: Float) {
        val clip = _state.value.clips.firstOrNull { it.id == layerId } ?: return
        if (!clip.isEffectClip) return
        val presetKey = clip.effectKeys.firstOrNull() ?: return
        val preset = EffectLibrary.findByKey(presetKey) ?: return
        val scaled = buildScaledEffectState(preset, intensity)
        updateClipDirect(layerId) {
            it.copy(effectState = scaled)
        }
    }

    fun updateClipEffectLightning(clipId: String, config: com.moody.moodyvideoeditor.data.LightningConfig) {
        updateClipDirect(clipId) { clip ->
            val eff = clip.effectState
            if (eff != null && eff.lightning != null) {
                clip.copy(effectState = eff.copy(lightning = config))
            } else clip
        }
    }

    fun updateClipEffectParticle(clipId: String, config: com.moody.moodyvideoeditor.data.ParticleConfig) {
        updateClipDirect(clipId) { clip ->
            val eff = clip.effectState
            if (eff != null && eff.particle != null) {
                clip.copy(effectState = eff.copy(particle = config))
            } else clip
        }
    }

    fun updateClipEffectFire(clipId: String, config: com.moody.moodyvideoeditor.data.FireConfig) {
        updateClipDirect(clipId) { clip ->
            val eff = clip.effectState
            if (eff != null && eff.fire != null) {
                clip.copy(effectState = eff.copy(fire = config))
            } else clip
        }
    }

    fun updateClipEffectDust(clipId: String, config: com.moody.moodyvideoeditor.data.DustConfig) {
        updateClipDirect(clipId) { clip ->
            val eff = clip.effectState
            if (eff != null && eff.dust != null) {
                clip.copy(effectState = eff.copy(dust = config))
            } else clip
        }
    }

    fun removeEffectLayer(layerId: String) {
        val clip = _state.value.clips.firstOrNull { it.id == layerId } ?: return
        if (!clip.isEffectClip) return
        pushHistory()
        val list = _state.value.clips.toMutableList()
        list.removeAll { it.id == layerId }
        _state.update {
            it.copy(
                clips = list, selectedClipId = null,
                multiSelectedIds = emptySet(), previewEffectState = null
            )
        }
        updateHistoryFlags()
    }

    private fun buildScaledEffectState(
        preset: com.moody.moodyvideoeditor.data.EffectPreset,
        intensity: Float
    ): EffectState {
        val t = (intensity / 100f).coerceIn(0f, 2f)

        val scaledMotion = preset.motion?.let { m ->
            m.copy(intensity = (m.intensity * t).coerceIn(0f, 500f))
        }

        val scaledFilters: com.moody.moodyvideoeditor.data.ColorFilterValues? =
            preset.filters?.let { f ->
                val s100: (Float) -> Float = { v ->
                    (100f + (v - 100f) * t).coerceIn(0f, 300f)
                }
                val s0: (Float) -> Float = { v ->
                    (v * t).coerceIn(0f, 300f)
                }
                f.copy(
                    brightness = s100(f.brightness),
                    contrast = s100(f.contrast),
                    saturation = s100(f.saturation),
                    hue = s0(f.hue).coerceIn(0f, 360f),
                    grayscale = s0(f.grayscale).coerceIn(0f, 100f),
                    sepia = s0(f.sepia).coerceIn(0f, 100f),
                    invert = s0(f.invert).coerceIn(0f, 100f),
                    blur = s0(f.blur),
                    opacity = s100(f.opacity).coerceIn(0f, 100f)
                )
            }

        val scaledAnimatedColor = preset.animatedColor?.let { ac ->
            ac.copy(intensity = (ac.intensity * t).coerceIn(0f, 200f))
        }
        val scaledDust = preset.dust?.let { dc ->
            dc.copy(masterIntensity = (dc.masterIntensity * (intensity / 100f)).coerceIn(0f, 200f))
        }

        val scaledBlur = preset.blur?.let { bc ->
            bc.copy(intensity = (bc.intensity * t).coerceIn(0f, 100f))
        }
        val scaledOverlay = preset.overlay?.let { o ->
            o.copy(intensity = (o.intensity * t).coerceIn(0f, 300f))
        }

        val scaledEdgeGlow = preset.edgeGlow?.let { g ->
            g.copy(intensity = (g.intensity * t).coerceIn(0f, 300f))
        }

        val scaledLightLeak = preset.lightLeak?.let { l ->
            l.copy(
                intensity = (l.intensity * t).coerceIn(0f, 300f),
                radius = (l.radius * (0.85f + 0.15f * t)).coerceIn(0.1f, 2f)
            )
        }

        val scaledLightning = preset.lightning?.let { lc ->
            lc.copy(intensity = (lc.intensity * (intensity / 100f)).coerceIn(0f, 200f))
        }
        val scaledParticle = preset.particle?.let { pc ->
            pc.copy(masterIntensity = (pc.masterIntensity * (intensity / 100f)).coerceIn(0f, 200f))
        }
        val scaledFire = preset.fire?.let { fc ->
            fc.copy(masterIntensity = (fc.masterIntensity * (intensity / 100f)).coerceIn(0f, 200f))
        }

        return EffectState(
            kind = EffectState.KIND_EFFECT,
            presetKey = preset.key,
            filters = scaledFilters,
            blur = scaledBlur,
            animatedColor = scaledAnimatedColor,
            motion = scaledMotion,
            overlay = scaledOverlay,
            edgeGlow = scaledEdgeGlow,
            lightLeak = scaledLightLeak,
            lightning = scaledLightning,
            particle = scaledParticle,
            fire = scaledFire,
            dust = scaledDust,
            masterIntensity = intensity
        )
    }

    fun removeSelectedEffect() {
        val sel = _state.value.selectedClip ?: return
        if (!sel.isEffectClip) return
        pushHistory()
        val list = _state.value.clips.toMutableList()
        list.removeAll { it.id == sel.id }
        _state.update { it.copy(clips = list, selectedClipId = null) }
        updateHistoryFlags()
    }

    fun applyAdjustment(newAdj: AdjustmentData) {
        val s = _state.value
        val baseClip = s.selectedClip
        val clip = EditorClip(
            id = UUID.randomUUID().toString(), uri = Uri.EMPTY,
            name = "🎚️ Adjust", type = "adjustment/plain",
            sourceStartMs = 0L, sourceEndMs = baseClip?.durationMs ?: 3000L,
            timelineStartMs = baseClip?.timelineStartMs ?: s.currentPosMs,
            trackIndex = 0, isAudio = false, sourceTotalMs = Long.MAX_VALUE,
            adjustments = newAdj
        )
        pushHistory()
        addClipOnNewLayer(clip, (baseClip?.trackIndex ?: 0) + 1)
        updateHistoryFlags()
    }

    fun prepareColorWheelLayer(): EditorClip {
        val state = _state.value
        val selectedWheel = state.selectedClip?.takeIf {
            it.isAdjustmentClip &&
                    it.effectState?.kind == EffectState.KIND_COLOR_WHEEL
        }
        if (selectedWheel != null) return selectedWheel

        val clip = EditorClip(
            uri = Uri.EMPTY,
            name = "🌈 Color Wheels",
            type = "adjustment/plain",
            effectState = EffectState(kind = EffectState.KIND_COLOR_WHEEL),
            sourceStartMs = 0L,
            sourceEndMs = 3_000L,
            timelineStartMs = state.currentPosMs,
            trackIndex = maxOf(
                state.visualLayerCount,
                (state.clips.filterNot { it.isAudio }.maxOfOrNull { it.trackIndex } ?: -1) + 1
            ),
            sourceTotalMs = Long.MAX_VALUE
        )
        pushHistory()
        val clips = state.clips + clip
        _state.update {
            it.copy(
                clips = clips,
                visualLayerCount = maxOf(it.visualLayerCount, clip.trackIndex + 1),
                selectedClipId = clip.id,
                selectedTrackIndex = clip.trackIndex,
                selectedIsAudio = false
            )
        }
        updateHistoryFlags()
        return clip
    }

    fun updateColorWheelLayer(clipId: String, newState: ColorWheelState) {
        updateClipDirect(clipId) { clip ->
            if (clip.isAdjustmentClip) clip.copy(colorWheel = newState) else clip
        }
    }

    fun removeSelectedColorWheelLayer() {
        val selected = _state.value.selectedClip ?: return
        if (!selected.isAdjustmentClip ||
            selected.effectState?.kind != EffectState.KIND_COLOR_WHEEL
        ) return
        pushHistory()
        _state.update {
            it.copy(
                clips = it.clips.filterNot { clip -> clip.id == selected.id },
                selectedClipId = null
            )
        }
        updateHistoryFlags()
    }

    fun updateSelectedAdjustment(newAdj: AdjustmentData) {
        val s = _state.value
        val adjClips = s.clips.filter { it.isAdjustmentClip && it.id in s.multiSelectedIds }
        if (adjClips.isNotEmpty()) {
            forEachSelectedClip { clip ->
                if (clip.isAdjustmentClip) clip.copy(adjustments = newAdj) else clip
            }
            return
        }
        val sel = s.selectedClip
        if (sel == null) {
            applyAdjustment(newAdj)
            return
        }
        if (sel.isAdjustmentClip) {
            updateClipDirect(sel.id) { it.copy(adjustments = newAdj) }
            return
        }
        val existingAdj = s.clips.firstOrNull {
            it.isAdjustmentClip &&
                    abs(it.timelineStartMs - sel.timelineStartMs) < 200
        }
        if (existingAdj != null) {
            updateClipDirect(existingAdj.id) { it.copy(adjustments = newAdj) }
        } else {
            applyAdjustment(newAdj)
        }
    }

    fun updateClipAdjustment(clipId: String, newAdj: AdjustmentData) {
        updateClipDirect(clipId) { it.copy(adjustments = newAdj) }
    }

    fun resetAdjustments() {
        forEachSelectedClip { clip ->
            if (clip.isAdjustmentClip) clip.copy(adjustments = AdjustmentData()) else clip
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  VISUALIZER
    // ═══════════════════════════════════════════════════════════

    suspend fun createVisualizerClipWithBeats(context: Context): Boolean {
        val s = _state.value
        val selectedAudio = resolveSelectedVisualizerAudio(s) ?: return false

        val existing = s.clips.firstOrNull {
            it.isVisualizerClip &&
                    it.visualizer?.linkedAudioClipId == selectedAudio.id
        }
        if (existing != null) return false

        val (beatTimes, beatStrengths) = try {
            val result = BeatsEngine.detect(
                context = context, uri = selectedAudio.uri, filter = "all"
            )
            if (result.detected && result.beatTimesMs.isNotEmpty()) {
                result.beatTimesMs to result.beatStrengths
            } else emptyList<Long>() to emptyList<Float>()
        } catch (e: Throwable) {
            emptyList<Long>() to emptyList<Float>()
        }

        val startMs = selectedAudio.timelineStartMs
        val durMs = selectedAudio.durationMs
        val linkId = selectedAudio.linkedId ?: "lk-viz-${System.currentTimeMillis()}"

        val lockedState = VisualizerState(
            linkedAudioClipId = selectedAudio.id,
            beatTimesMs = beatTimes,
            beatStrengths = beatStrengths,
            useBeatSync = beatTimes.isNotEmpty()
        )

        val clip = EditorClip(
            id = UUID.randomUUID().toString(), uri = Uri.EMPTY,
            name = "🎵 Visualizer", type = "visualizer/plain",
            sourceStartMs = 0L, sourceEndMs = durMs,
            timelineStartMs = startMs, trackIndex = 0,
            isAudio = false, sourceTotalMs = Long.MAX_VALUE,
            visualizer = lockedState, linkedId = linkId
        )

        pushHistory()
        val list = s.clips.toMutableList()
        val aIdx = list.indexOfFirst { it.id == selectedAudio.id }
        if (aIdx >= 0 && list[aIdx].linkedId != linkId) {
            list[aIdx] = list[aIdx].copy(linkedId = linkId)
        }
        _state.update { it.copy(clips = list) }

        addClipOnNewLayer(clip, s.visualLayerCount)

        _state.update {
            it.copy(
                beatsDetected = beatTimes.isNotEmpty(),
                beatsCount = beatTimes.size,
                beatTimesMs = beatTimes,
                beatStrengths = beatStrengths
            )
        }

        updateHistoryFlags()
        return true
    }

    fun createVisualizerClip(state: VisualizerState = VisualizerState()): Boolean {
        val s = _state.value
        val selectedAudio = resolveSelectedVisualizerAudio(s) ?: return false

        val existing = s.clips.firstOrNull {
            it.isVisualizerClip &&
                    it.visualizer?.linkedAudioClipId == selectedAudio.id
        }
        if (existing != null) return false

        val startMs = selectedAudio.timelineStartMs
        val durMs = selectedAudio.durationMs
        val linkId = selectedAudio.linkedId ?: "lk-viz-${System.currentTimeMillis()}"

        val lockedState = state.copy(
            linkedAudioClipId = selectedAudio.id,
            beatTimesMs = s.beatTimesMs,
            beatStrengths = s.beatStrengths,
            useBeatSync = s.beatsDetected && s.beatTimesMs.isNotEmpty()
        )

        val clip = EditorClip(
            id = UUID.randomUUID().toString(), uri = Uri.EMPTY,
            name = "🎵 Visualizer", type = "visualizer/plain",
            sourceStartMs = 0L, sourceEndMs = durMs,
            timelineStartMs = startMs, trackIndex = 0,
            isAudio = false, sourceTotalMs = Long.MAX_VALUE,
            visualizer = lockedState, linkedId = linkId
        )

        pushHistory()
        val list = s.clips.toMutableList()
        val aIdx = list.indexOfFirst { it.id == selectedAudio.id }
        if (aIdx >= 0 && list[aIdx].linkedId != linkId) {
            list[aIdx] = list[aIdx].copy(linkedId = linkId)
        }
        _state.update { it.copy(clips = list) }
        addClipOnNewLayer(clip, s.visualLayerCount)
        updateHistoryFlags()
        return true
    }

    private fun resolveSelectedVisualizerAudio(state: EditorState): EditorClip? {
        val selected = state.selectedClip ?: return null
        if (selected.isAudio && !selected.isAudioEffectClip) return selected
        val linkId = selected.linkedId ?: return null
        if (!selected.isVisualClip) return null
        return state.clips.firstOrNull {
            it.isAudio && !it.isAudioEffectClip && it.linkedId == linkId
        }
    }

    fun syncBeatsToVisualizer(beats: BeatsState, audioClipId: String? = null) {
        val s = _state.value
        val list = s.clips.toMutableList()
        var changed = false
        for (i in list.indices) {
            val c = list[i]
            if (!c.isVisualizerClip) continue
            val viz = c.visualizer ?: continue
            if (audioClipId != null && viz.linkedAudioClipId != audioClipId) continue
            list[i] = c.copy(
                visualizer = viz.copy(
                    beatTimesMs = beats.beatTimesMs,
                    beatStrengths = beats.beatStrengths,
                    useBeatSync = beats.detected && beats.beatTimesMs.isNotEmpty()
                )
            )
            changed = true
        }
        if (changed) _state.update { it.copy(clips = list) }
    }

    fun syncVisualizerDuration(visualizerClipId: String, audioClipId: String) {
        val audio = _state.value.clips.firstOrNull { it.id == audioClipId } ?: return
        updateClipDirect(visualizerClipId) { viz ->
            viz.copy(
                timelineStartMs = audio.timelineStartMs,
                sourceEndMs = audio.durationMs
            )
        }
    }

    fun updateVisualizerLayer(layerId: String, state: VisualizerState) {
        updateClipDirect(layerId) { c ->
            if (c.isVisualizerClip) c.copy(visualizer = state) else c
        }
    }

    fun removeVisualizerLayer(layerId: String) {
        val clip = _state.value.clips.firstOrNull { it.id == layerId } ?: return
        if (!clip.isVisualizerClip) return
        pushHistory()
        val list = _state.value.clips.toMutableList()
        list.removeAll { it.id == layerId }
        val gid = clip.linkGroupId
        if (gid != null) {
            val remaining = list.filter { it.linkGroupId == gid }
            if (remaining.size == 1) {
                val rIdx = list.indexOfFirst { it.linkGroupId == gid }
                if (rIdx >= 0) list[rIdx] = list[rIdx].copy(linkGroupId = null)
            }
        }
        _state.update {
            it.copy(clips = list, selectedClipId = null, multiSelectedIds = emptySet())
        }
        cleanupStaleBeats()
        updateHistoryFlags()
    }
}

// ═══════════════════════════════════════════════════════════════
//  SUPPORTING TYPES
// ═══════════════════════════════════════════════════════════════

data class ExportUiStateBundle(
    val fileName: String,
    val mode: String,
    val resolution: String,
    val fps: Int,
    val bitrateKbps: Int,
    val videoFormat: String,
    val audioFormat: String,
    val audioBitrateKbps: Int,
    val imageFormat: String,
    val jpegQuality: Int,
    val aspectRatio: String,
    val outputWidth: Int,
    val outputHeight: Int,
    val folderUri: String?,
    val startMs: Long,
    val endMs: Long,
    val totalDurationMs: Long,
    val clips: List<EditorClip>
)

object ExportClipsHolder {
    @Volatile
    var clips: List<EditorClip> = emptyList()

    fun set(newClips: List<EditorClip>) {
        clips = newClips
    }

    fun clear() {
        clips = emptyList()
    }
}