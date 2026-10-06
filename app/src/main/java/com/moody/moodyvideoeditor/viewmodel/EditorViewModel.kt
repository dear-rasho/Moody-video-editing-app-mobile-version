package com.moody.moodyvideoeditor.viewmodel

import android.content.Context
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import com.moody.moodyvideoeditor.data.AdjustmentData
import com.moody.moodyvideoeditor.data.BeatsState
import com.moody.moodyvideoeditor.data.BrushState
import com.moody.moodyvideoeditor.data.BrushStroke
import com.moody.moodyvideoeditor.data.ChromaState
import com.moody.moodyvideoeditor.data.ColorWheelState
import com.moody.moodyvideoeditor.data.EditorClip
import com.moody.moodyvideoeditor.data.EditorState
import com.moody.moodyvideoeditor.data.EffectLibrary
import com.moody.moodyvideoeditor.data.EffectState
import com.moody.moodyvideoeditor.data.FilterState
import com.moody.moodyvideoeditor.data.Keyframe
import com.moody.moodyvideoeditor.data.MaskKeyframe
import com.moody.moodyvideoeditor.data.MaskLibrary
import com.moody.moodyvideoeditor.data.MaskPoint
import com.moody.moodyvideoeditor.data.MaskState
import com.moody.moodyvideoeditor.data.MaskType
import com.moody.moodyvideoeditor.data.OverlayState
import com.moody.moodyvideoeditor.data.RatioState
import com.moody.moodyvideoeditor.data.StickerState
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
import com.moody.moodyvideoeditor.utils.MaskEngine
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
import java.util.UUID
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

class EditorViewModel : ViewModel() {

    private val _state = MutableStateFlow(EditorState())
    val state: StateFlow<EditorState> = _state.asStateFlow()

    private val history = HistoryManager()

    // Export state from singleton, observed by UI
    val exportState: StateFlow<ExportUiState> = ExportStateHolder.state

    // Starts export in foreground service
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

    // Cancels running export
    fun cancelExport(context: Context) {
        try {
            val cancelIntent = ExportService.buildCancelIntent(context)
            context.startService(cancelIntent)
        } catch (t: Throwable) {
            android.util.Log.e("EditorViewModel", "cancelExport failed", t)
        }
    }

    // Clears export state after user dismisses
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
        _state.value = EditorState()
        updateHistoryFlags()
        return id
    }

    fun saveCurrentProject(context: Context): Boolean {
        val id = currentProjectId ?: return false
        val s = _state.value
        if (s.clips.isEmpty()) return false
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
            if (clip.isTextClip || clip.isStickerClip || clip.isBrushClip ||
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

    fun beginDrag() {
        dragBaseline = _state.value.clips
    }

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

    fun beginGroupGesture() {
        val s = _state.value
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

        val deltaScale = if (kotlin.math.abs(anchorBase.scale) < 0.001f) 1f
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
        val videoClip = EditorClip(
            uri = uri, name = name, type = "video/mp4",
            sourceStartMs = 0L, sourceEndMs = duration,
            timelineStartMs = 0L, trackIndex = 0, isAudio = false,
            sourceTotalMs = if (isImage) Long.MAX_VALUE else sourceTotalMs,
            linkedId = linkId
        )
        val audioClip = videoClip.copy(
            id = UUID.randomUUID().toString(),
            name = "$name (audio)", type = "audio/mpeg",
            trackIndex = 0, isAudio = true
        )
        val list = _state.value.clips.toMutableList()
        list.add(videoClip)
        list.add(audioClip)
        _state.update {
            it.copy(
                clips = list, currentIndex = 0, currentPosMs = 0L,
                selectedClipId = videoClip.id,
                selectedTrackIndex = 0, selectedIsAudio = false,
                visualLayerCount = maxOf(it.visualLayerCount, 3),
                audioLayerCount = maxOf(it.audioLayerCount, 2),
                multiSelectedIds = emptySet(),
                timelineZoom = 0f
            )
        }
        updateHistoryFlags()
    }

    fun addClipSmart(
        uri: Uri,
        name: String,
        durationMs: Long,
        mediaType: String = "video/mp4"
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
        val finalDurMs = if (isImage && durationMs < 100L) 5000L else durMs

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

            _state.update { st ->
                st.copy(
                    clips = st.timelineVisualList().flatten() + newAudioList.flatten(),
                    audioLayerCount = maxOf(
                        st.audioLayerCount, audioPlacement.trackIndex + 1
                    ),
                    selectedClipId = clip.id,
                    selectedTrackIndex = audioPlacement.trackIndex,
                    selectedIsAudio = true,
                    multiSelectedIds = emptySet(),
                    timelineZoom = 0f
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

        val audioClip = if (isImage) null else videoClip.copy(
            id = UUID.randomUUID().toString(),
            name = "$name (audio)", type = "audio/mpeg",
            trackIndex = audioPlacement.trackIndex,
            isAudio = true
        )

        _state.update { st ->
            val newVisualList = st.timelineVisualList().toMutableList()
            while (newVisualList.size <= visualPlacement.trackIndex)
                newVisualList.add(emptyList())
            newVisualList[visualPlacement.trackIndex] =
                newVisualList[visualPlacement.trackIndex] + videoClip

            val newAudioList = if (audioClip != null) {
                val list = st.timelineAudioList().toMutableList()
                while (list.size <= audioPlacement.trackIndex) list.add(emptyList())
                list[audioPlacement.trackIndex] =
                    list[audioPlacement.trackIndex] + audioClip
                list
            } else st.timelineAudioList()

            st.copy(
                clips = newVisualList.flatten() + newAudioList.flatten(),
                visualLayerCount = maxOf(st.visualLayerCount, visualPlacement.trackIndex + 1),
                audioLayerCount = maxOf(st.audioLayerCount, audioPlacement.trackIndex + 1),
                selectedClipId = videoClip.id,
                selectedTrackIndex = visualPlacement.trackIndex,
                selectedIsAudio = false,
                multiSelectedIds = emptySet(),
                timelineZoom = 0f
            )
        }
        updateHistoryFlags()
    }

    fun selectClip(clip: EditorClip) {
        _state.update {
            it.copy(
                selectedClipId = clip.id,
                selectedTrackIndex = clip.trackIndex,
                selectedIsAudio = clip.isAudio,
                currentIndex = it.clips.indexOf(clip).coerceAtLeast(0)
            )
        }
    }

    fun clearAllSelection() {
        _state.update {
            it.copy(selectedClipId = null, multiSelectedIds = emptySet())
        }
    }

    fun moveClip(clipId: String, targetTrackIndex: Int, newIsAudio: Boolean, newTimelineMs: Long) {
        val s = _state.value
        val clip = s.clips.firstOrNull { it.id == clipId } ?: return

        val isMulti = s.multiSelectedIds.size > 1 && clipId in s.multiSelectedIds
        if (isMulti) {
            moveSelectedClipsBulk(clipId, targetTrackIndex, newTimelineMs)
            return
        }

        if (clip.isAudio && !clip.isAudioEffectClip) {
            val movedDelta = newTimelineMs - clip.timelineStartMs
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
            moveLinkedGroup(clipId, targetTrackIndex, newIsAudio, newTimelineMs)
            return
        }

        val sourceTrack = clip.trackIndex
        val isAudio = clip.isAudio
        val targetTrack = targetTrackIndex.coerceAtLeast(0)
        val targetStart = newTimelineMs.coerceAtLeast(0L)

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

        val anchor = group.maxByOrNull { it.linkAnchorPriority } ?: movedClip
        val anchorIsMoved = anchor.id == movedClipId

        val list = s.clips.toMutableList()

        if (anchorIsMoved) {
            val timeDelta = newTimelineMs - anchor.timelineStartMs
            group.forEach { c ->
                val idx = list.indexOfFirst { it.id == c.id }
                if (idx < 0) return@forEach
                if (c.id == anchor.id) {
                    list[idx] = c.copy(
                        trackIndex = targetTrackIndex,
                        timelineStartMs = newTimelineMs
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
                    trackIndex = targetTrackIndex,
                    timelineStartMs = newTimelineMs
                )
            }
        }

        val maxVisual = list.filter { !it.isAudio }.maxOfOrNull { it.trackIndex } ?: 0
        val maxAudio = list.filter { it.isAudio }.maxOfOrNull { it.trackIndex } ?: 0

        _state.update {
            it.copy(
                clips = list,
                selectedClipId = movedClipId,
                selectedTrackIndex = targetTrackIndex,
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
        val timeDelta = newTimelineMs - anchor.timelineStartMs

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
        for (t in 0 until s.visualLayerCount) {
            val hasOverlap = s.clips.any { c ->
                !c.isAudio && c.trackIndex == t &&
                        c.timelineStartMs < endMs && startMs < c.timelineEndMs
            }
            if (!hasOverlap) return t
        }
        val newIdx = s.visualLayerCount
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

    fun closeGapsFromPlayhead() {
        val sel = _state.value.selectedClip ?: return
        pushHistory()
        val s = _state.value
        val list = if (sel.isAudio) s.timelineAudioList() else s.timelineVisualList()
        val track = list.getOrNull(sel.trackIndex) ?: return
        val updated = TimelineTools.closeGapsFromPlayhead(track, s.currentPosMs)

        _state.update { st ->
            val newList = (if (sel.isAudio) st.timelineAudioList()
            else st.timelineVisualList()).toMutableList()
            newList[sel.trackIndex] = updated
            st.withTrackList(sel.isAudio, newList)
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

    fun clearMultiSelect() {
        _state.update { it.copy(multiSelectedIds = emptySet()) }
    }

    fun deselectAll() {
        _state.update { it.copy(multiSelectedIds = emptySet()) }
    }

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
        val gid = clip.linkGroupId ?: return

        pushHistory()
        val list = s.clips.toMutableList()
        val idx = list.indexOfFirst { it.id == clipId }
        if (idx < 0) return
        list[idx] = list[idx].copy(linkGroupId = null)

        val remaining = list.filter { it.linkGroupId == gid }
        if (remaining.size == 1) {
            val rIdx = list.indexOfFirst { it.linkGroupId == gid }
            if (rIdx >= 0) list[rIdx] = list[rIdx].copy(linkGroupId = null)
        }
        _state.update { it.copy(clips = list) }
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

        clip.isStickerClip && clip.stickerState != null ->
            clip.stickerState.x to clip.stickerState.y

        clip.isVisualizerClip && clip.visualizer != null ->
            (clip.visualizer.positionX * 100f) to (clip.visualizer.positionY * 100f)

        else ->
            ((clip.offsetX * 100f) + 50f) to ((clip.offsetY * 100f) + 50f)
    }

    private fun getClipScale(clip: EditorClip): Float = when {
        clip.isTextClip && clip.textState != null -> clip.textState.scale
        clip.isStickerClip && clip.stickerState != null -> clip.stickerState.scale
        clip.isVisualizerClip && clip.visualizer != null ->
            clip.visualizer.size / 0.32f * 100f

        else -> clip.scale * 100f
    }

    private fun getClipRotation(clip: EditorClip): Float = when {
        clip.isTextClip && clip.textState != null -> clip.textState.rotation
        clip.isStickerClip && clip.stickerState != null -> clip.stickerState.rotation
        clip.isVisualizerClip && clip.visualizer != null -> clip.visualizer.rotation
        else -> clip.rotation
    }

    private fun setClipPos(clip: EditorClip, x: Float, y: Float): EditorClip = when {
        clip.isTextClip && clip.textState != null ->
            clip.copy(textState = clip.textState.copy(positionX = x, positionY = y))

        clip.isStickerClip && clip.stickerState != null ->
            clip.copy(stickerState = clip.stickerState.copy(x = x, y = y))

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

        clip.isStickerClip && clip.stickerState != null ->
            clip.copy(stickerState = clip.stickerState.copy(scale = scale))

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

        clip.isStickerClip && clip.stickerState != null ->
            clip.copy(stickerState = clip.stickerState.copy(rotation = rotation))

        clip.isVisualizerClip && clip.visualizer != null ->
            clip.copy(visualizer = clip.visualizer.copy(rotation = rotation))

        else -> clip.copy(rotation = rotation)
    }

    private fun setClipTransformWithAutoKeyframes(
        clip: EditorClip,
        x: Float,
        y: Float,
        scale: Float,
        rotation: Float,
        timeSec: Float
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
                list[i] = setClipPos(
                    list[i],
                    cx + dx,
                    cy + dy
                )
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
        val scaleFactor = if (kotlin.math.abs(anchorScale) > 0.001f) {
            newScale / anchorScale
        } else {
            1f
        }
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

    fun updateStickerPositionBulk(anchorId: String, newX: Float, newY: Float) =
        updateSelectedPositionBulk(anchorId, newX, newY)

    fun updateStickerTransformBulk(anchorId: String, newScale: Float, newRot: Float) =
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
        val playheadInSource = sel.sourceStartMs +
                (_state.value.currentPosMs * sel.speed).toLong()
        if (playheadInSource <= sel.sourceStartMs + EditorClip.MIN_DURATION_MS) return
        if (playheadInSource >= sel.sourceEndMs - EditorClip.MIN_DURATION_MS) return
        val cutAmount = playheadInSource - sel.sourceStartMs
        pushHistory()
        updateClipDirect(sel.id) {
            it.copy(
                sourceStartMs = playheadInSource,
                timelineStartMs = (it.timelineStartMs + cutAmount).coerceAtLeast(0L)
            )
        }
        syncLinkedTrim(_state.value.clips.first { it.id == sel.id })
        updateHistoryFlags()
    }

    fun trimRight() {
        val sel = _state.value.selectedClip ?: return
        val playheadInSource = sel.sourceStartMs +
                (_state.value.currentPosMs * sel.speed).toLong()
        if (playheadInSource <= sel.sourceStartMs + EditorClip.MIN_DURATION_MS) return
        if (playheadInSource >= sel.sourceEndMs - EditorClip.MIN_DURATION_MS) return
        pushHistory()
        updateClipDirect(sel.id) { it.copy(sourceEndMs = playheadInSource) }
        syncLinkedTrim(_state.value.clips.first { it.id == sel.id })
        updateHistoryFlags()
    }

    fun splitCurrentClip() {
        val sel = _state.value.selectedClip ?: return
        val s = _state.value
        pushHistory()
        val splitMs = sel.sourceStartMs + (s.currentPosMs * sel.speed).toLong()
        if (splitMs <= sel.sourceStartMs + EditorClip.MIN_DURATION_MS ||
            splitMs >= sel.sourceEndMs - EditorClip.MIN_DURATION_MS
        ) return

        val groupId = sel.linkGroupId
        val list = s.clips.toMutableList()

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

                val first = c.copy(
                    id = "${c.id}-A", sourceEndMs = localSplitMs,
                    linkGroupId = groupA
                )
                val second = c.copy(
                    id = "${c.id}-B", sourceStartMs = localSplitMs,
                    linkGroupId = groupB
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
            val first = sel.copy(id = "${sel.id}-A", sourceEndMs = splitMs)
            val second = sel.copy(
                id = "${sel.id}-B", sourceStartMs = splitMs, linkedId = null
            )
            val idx = list.indexOfFirst { it.id == sel.id }
            list.removeAt(idx)
            list.add(idx, second)
            list.add(idx, first)
            TimelineEngine.recalcTrackTimings(list, sel.trackIndex, sel.isAudio)
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

    fun addFreezeFrame(durationMs: Long) {
        val sel = _state.value.selectedClip ?: return
        if (sel.isAudio) return
        pushHistory()
        val freezeClip = FreezeEngine.makeFreezeClip(sel, sel.timelineEndMs, durationMs)
        val list = _state.value.clips.toMutableList()
        list.add(freezeClip)
        TimelineEngine.recalcTrackTimings(list, sel.trackIndex, false)
        _state.update { it.copy(clips = list, selectedClipId = freezeClip.id) }
        updateHistoryFlags()
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
        val clamped = v.coerceIn(0f, 1f)
        forEachSelectedClip { it.copy(volume = clamped) }
        _state.update { it.copy(volume = clamped) }
    }

    fun getSelectedBaseDurationMs(): Long =
        _state.value.selectedClip?.let { SpeedEngine.getBaseDuration(it) } ?: 0L

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
        val s = _state.value
        val durMs = (endMs - startMs).coerceAtLeast(500L)
        val trackIdx = findOrCreateVisualTrack(0, startMs, durMs)
        val clip = EditorClip(
            id = UUID.randomUUID().toString(), uri = Uri.EMPTY,
            name = "📝 ${textState.content.take(18).ifBlank { "Text" }}",
            type = "text/plain",
            sourceStartMs = 0L, sourceEndMs = durMs,
            timelineStartMs = startMs, trackIndex = trackIdx,
            isAudio = false, sourceTotalMs = Long.MAX_VALUE,
            textState = textState
        )
        pushHistory()
        val list = s.clips.toMutableList()
        list.add(clip)
        _state.update {
            it.copy(
                clips = list, selectedClipId = clip.id,
                selectedTrackIndex = trackIdx, selectedIsAudio = false
            )
        }
        updateHistoryFlags()
    }

    fun updateSelectedText(newState: TextState) {
        forEachSelectedClip { clip ->
            if (clip.isTextClip) {
                clip.copy(
                    textState = newState,
                    name = "📝 ${newState.content.take(18).ifBlank { "Text" }}"
                )
            } else clip
        }
    }

    fun getSelectedTextState(): TextState =
        _state.value.selectedClip?.textState ?: TextState()

    fun removeSelectedText() {
        val sel = _state.value.selectedClip ?: return
        if (!sel.isTextClip) return
        pushHistory()
        val list = _state.value.clips.toMutableList()
        list.removeAll { it.id == sel.id }
        _state.update { it.copy(clips = list, selectedClipId = null) }
        updateHistoryFlags()
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

    fun addOrUpdateSticker(emoji: String) {
        val sel = _state.value.selectedClip
        if (sel != null && sel.isStickerClip) {
            updateClipDirect(sel.id) {
                it.copy(
                    stickerState = (it.stickerState ?: StickerState()).copy(emoji = emoji),
                    name = emoji
                )
            }
            return
        }
        val s = _state.value
        val clip = EditorClip(
            id = UUID.randomUUID().toString(), uri = Uri.EMPTY,
            name = emoji, type = "sticker/plain",
            sourceStartMs = 0L, sourceEndMs = 3000L,
            timelineStartMs = s.currentPosMs, trackIndex = 0,
            isAudio = false, sourceTotalMs = Long.MAX_VALUE,
            stickerState = StickerState(emoji = emoji)
        )
        pushHistory()
        addClipOnNewLayer(clip, (s.selectedClip?.trackIndex ?: 0) + 1)
        updateHistoryFlags()
    }

    fun createStickerAtTime(emoji: String, startMs: Long, endMs: Long) {
        val s = _state.value
        val durMs = (endMs - startMs).coerceAtLeast(500L)
        val trackIdx = findOrCreateVisualTrack(0, startMs, durMs)
        val clip = EditorClip(
            id = UUID.randomUUID().toString(), uri = Uri.EMPTY,
            name = emoji, type = "sticker/plain",
            sourceStartMs = 0L, sourceEndMs = durMs,
            timelineStartMs = startMs, trackIndex = trackIdx,
            isAudio = false, sourceTotalMs = Long.MAX_VALUE,
            stickerState = StickerState(emoji = emoji)
        )
        pushHistory()
        val list = s.clips.toMutableList()
        list.add(clip)
        _state.update {
            it.copy(
                clips = list, selectedClipId = clip.id,
                selectedTrackIndex = trackIdx, selectedIsAudio = false
            )
        }
        updateHistoryFlags()
    }

    fun updateSelectedSticker(newState: StickerState) {
        forEachSelectedClip { clip ->
            if (clip.isStickerClip) clip.copy(stickerState = newState) else clip
        }
    }

    fun getSelectedStickerState(): StickerState =
        _state.value.selectedClip?.stickerState ?: StickerState()

    fun removeSelectedSticker() {
        val sel = _state.value.selectedClip ?: return
        if (!sel.isStickerClip) return
        pushHistory()
        val list = _state.value.clips.toMutableList()
        list.removeAll { it.id == sel.id }
        _state.update { it.copy(clips = list, selectedClipId = null) }
        updateHistoryFlags()
    }

    fun updateStickerPositionDirect(clipId: String, x: Float, y: Float) {
        val s = _state.value
        val clip = s.clips.firstOrNull { it.id == clipId } ?: return
        if (!clip.isStickerClip) return
        val currentTimeSec = KeyframeStore.clipLocalTimeSeconds(
            s.currentPosMs, clip.timelineStartMs, clip.durationMs
        )
        val hasAnyKf = KeyframeStore.hasAnyKeyframes(clip.keyframes)
        updateClipDirect(clipId) { c ->
            val ss = c.stickerState ?: return@updateClipDirect c
            var kf = c.keyframes
            if (hasAnyKf) {
                kf = KeyframeStore.autoKeyframeIfActive(kf, "x", currentTimeSec, x)
                kf = KeyframeStore.autoKeyframeIfActive(kf, "y", currentTimeSec, y)
                c.copy(stickerState = ss.copy(x = x, y = y), keyframes = kf)
            } else c.copy(stickerState = ss.copy(x = x, y = y))
        }
    }

    fun updateStickerTransformDirect(clipId: String, scale: Float, rotation: Float) {
        val s = _state.value
        val clip = s.clips.firstOrNull { it.id == clipId } ?: return
        if (!clip.isStickerClip) return
        val currentTimeSec = KeyframeStore.clipLocalTimeSeconds(
            s.currentPosMs, clip.timelineStartMs, clip.durationMs
        )
        val hasAnyKf = KeyframeStore.hasAnyKeyframes(clip.keyframes)
        updateClipDirect(clipId) { c ->
            val ss = c.stickerState ?: return@updateClipDirect c
            var kf = c.keyframes
            if (hasAnyKf) {
                kf = KeyframeStore.autoKeyframeIfActive(kf, "scale", currentTimeSec, scale)
                kf = KeyframeStore.autoKeyframeIfActive(
                    kf, "rotation", currentTimeSec, rotation
                )
                c.copy(
                    stickerState = ss.copy(scale = scale, rotation = rotation),
                    keyframes = kf
                )
            } else c.copy(stickerState = ss.copy(scale = scale, rotation = rotation))
        }
    }

    fun createBrushClip(initial: BrushState = BrushState()) {
        val s = _state.value
        val clip = EditorClip(
            id = UUID.randomUUID().toString(), uri = Uri.EMPTY,
            name = "🖌️ Brush", type = "brush/plain",
            sourceStartMs = 0L, sourceEndMs = 5000L,
            timelineStartMs = s.currentPosMs, trackIndex = 0,
            isAudio = false, sourceTotalMs = Long.MAX_VALUE,
            brush = initial
        )
        pushHistory()
        addClipOnNewLayer(clip, (s.selectedClip?.trackIndex ?: 0) + 1)
        updateHistoryFlags()
    }

    fun addStrokeToBrushClip(clipId: String?, stroke: BrushStroke) {
        val s = _state.value
        val targetId = clipId
            ?: s.selectedClip?.takeIf { it.isBrushClip }?.id
            ?: s.clips.lastOrNull { it.isBrushClip }?.id
        if (targetId == null) {
            createBrushClip(BrushState(strokes = listOf(stroke)))
            return
        }
        updateClipDirect(targetId) { clip ->
            clip.copy(brush = clip.brush.copy(strokes = clip.brush.strokes + stroke))
        }
    }

    fun removeStrokeFromBrushClip(clipId: String, strokeId: String) {
        updateClipDirect(clipId) { clip ->
            clip.copy(
                brush = clip.brush.copy(
                    strokes = clip.brush.strokes.filter { it.id != strokeId }
                )
            )
        }
    }

    fun clearBrushClipStrokes(clipId: String) {
        updateClipDirect(clipId) { clip -> clip.copy(brush = BrushState()) }
    }

    fun undoLastStrokeOnBrushClip(clipId: String) {
        updateClipDirect(clipId) { clip ->
            val s = clip.brush.strokes
            if (s.isEmpty()) clip
            else clip.copy(brush = clip.brush.copy(strokes = s.dropLast(1)))
        }
    }

    fun addBrushStroke(stroke: BrushStroke) {
        val sel = _state.value.selectedClip ?: return
        updateClipDirect(sel.id) { clip ->
            clip.copy(brush = clip.brush.copy(strokes = clip.brush.strokes + stroke))
        }
    }

    fun removeBrushStroke(strokeId: String) {
        val sel = _state.value.selectedClip ?: return
        updateClipDirect(sel.id) { clip ->
            clip.copy(
                brush = clip.brush.copy(
                    strokes = clip.brush.strokes.filter { it.id != strokeId }
                )
            )
        }
    }

    fun clearBrushStrokes() {
        val sel = _state.value.selectedClip ?: return
        updateClipDirect(sel.id) { it.copy(brush = BrushState()) }
    }

    fun undoLastBrushStroke() {
        val sel = _state.value.selectedClip ?: return
        val strokes = sel.brush.strokes
        if (strokes.isEmpty()) return
        updateClipDirect(sel.id) {
            it.copy(brush = it.brush.copy(strokes = strokes.dropLast(1)))
        }
    }

    fun updateMask(newMask: MaskState) {
        val sel = _state.value.selectedClip ?: return
        updateClipDirect(sel.id) { it.copy(mask = newMask) }
    }

    fun setMaskType(type: MaskType) {
        val sel = _state.value.selectedClip ?: return
        val newMask = MaskLibrary.defaultFor(type)
        updateClipDirect(sel.id) { it.copy(mask = newMask) }
    }

    fun removeMask() {
        val sel = _state.value.selectedClip ?: return
        updateClipDirect(sel.id) { it.copy(mask = MaskState()) }
    }

    fun addMaskKeyframe() {
        val sel = _state.value.selectedClip ?: return
        val m = sel.mask
        if (!m.isActive) return
        val timeMs = _state.value.currentPosMs - sel.timelineStartMs
        if (timeMs < 0) return
        val kf = MaskKeyframe(
            timeMs = timeMs, centerX = m.centerX, centerY = m.centerY,
            radius = m.radius, width = m.width, height = m.height,
            rotation = m.rotation, cornerRadius = m.cornerRadius,
            scale = m.scale, positionY = m.positionY, feather = m.feather,
            expansion = m.expansion, opacity = m.opacity,
            customPoints = m.customPoints
        )
        val newMask = MaskEngine.addKeyframe(m, kf)
        updateClipDirect(sel.id) { it.copy(mask = newMask) }
    }

    fun clearMaskKeyframes() {
        val sel = _state.value.selectedClip ?: return
        val newMask = MaskEngine.clearKeyframes(sel.mask)
        updateClipDirect(sel.id) { it.copy(mask = newMask) }
    }

    fun hasMaskKeyframeAtPlayhead(): Boolean {
        val sel = _state.value.selectedClip ?: return false
        val timeMs = _state.value.currentPosMs - sel.timelineStartMs
        return MaskEngine.hasKeyframeAt(sel.mask, timeMs)
    }

    fun addMaskPoint(x: Float, y: Float) {
        val sel = _state.value.selectedClip ?: return
        val m = sel.mask
        val baseMask = if (m.type != MaskType.CUSTOM) MaskState(type = MaskType.CUSTOM)
        else m
        val newPoint = MaskPoint(x = x.coerceIn(0f, 1f), y = y.coerceIn(0f, 1f))
        updateClipDirect(sel.id) {
            it.copy(
                mask = baseMask.copy(
                    type = MaskType.CUSTOM,
                    customPoints = baseMask.customPoints + newPoint
                )
            )
        }
    }

    fun moveMaskAnchor(index: Int, x: Float, y: Float) {
        val sel = _state.value.selectedClip ?: return
        val m = sel.mask
        if (index !in m.customPoints.indices) return
        val old = m.customPoints[index]
        val updated = old.copy(x = x.coerceIn(0f, 1f), y = y.coerceIn(0f, 1f))
        updateClipDirect(sel.id) {
            it.copy(mask = MaskEngine.updatePoint(m, index, updated))
        }
    }

    fun moveMaskHandle(index: Int, isIn: Boolean, dx: Float, dy: Float) {
        val sel = _state.value.selectedClip ?: return
        val m = sel.mask
        if (index !in m.customPoints.indices) return
        val old = m.customPoints[index]
        val updated = if (isIn) {
            old.copy(
                handleInX = dx, handleInY = dy,
                handleOutX = -dx, handleOutY = -dy, hasHandles = true
            )
        } else {
            old.copy(
                handleOutX = dx, handleOutY = dy,
                handleInX = -dx, handleInY = -dy, hasHandles = true
            )
        }
        updateClipDirect(sel.id) {
            it.copy(mask = MaskEngine.updatePoint(m, index, updated))
        }
    }

    fun toggleMaskPointSmooth(index: Int) {
        val sel = _state.value.selectedClip ?: return
        val m = sel.mask
        if (index !in m.customPoints.indices) return
        val old = m.customPoints[index]
        val updated = if (old.hasHandles) {
            old.copy(
                handleInX = 0f, handleInY = 0f,
                handleOutX = 0f, handleOutY = 0f, hasHandles = false
            )
        } else {
            val (hx, hy) = MaskEngine.autoHandleOffsets(m.customPoints, index, 0.25f)
            old.copy(
                handleInX = -hx, handleInY = -hy,
                handleOutX = hx, handleOutY = hy, hasHandles = true
            )
        }
        updateClipDirect(sel.id) {
            it.copy(mask = MaskEngine.updatePoint(m, index, updated))
        }
    }

    fun deleteMaskPoint(index: Int) {
        val sel = _state.value.selectedClip ?: return
        val m = sel.mask
        if (index !in m.customPoints.indices) return
        updateClipDirect(sel.id) {
            it.copy(mask = MaskEngine.removePoint(m, index))
        }
    }

    fun removeLastMaskPoint() {
        val sel = _state.value.selectedClip ?: return
        val m = sel.mask
        if (m.customPoints.isEmpty()) return
        updateClipDirect(sel.id) {
            it.copy(mask = m.copy(customPoints = m.customPoints.dropLast(1)))
        }
    }

    fun clearMaskPoints() {
        val sel = _state.value.selectedClip ?: return
        updateClipDirect(sel.id) {
            it.copy(mask = it.mask.copy(customPoints = emptyList()))
        }
    }

    fun setMaskClosed(closed: Boolean) {
        val sel = _state.value.selectedClip ?: return
        updateClipDirect(sel.id) {
            it.copy(mask = it.mask.copy(customClosed = closed))
        }
    }

    suspend fun createVisualizerClipWithBeats(context: Context): Boolean {
        val s = _state.value
        val selectedAudio = s.selectedClip
        if (selectedAudio == null || !selectedAudio.isAudio ||
            selectedAudio.isAudioEffectClip
        ) {
            return false
        }

        val existing = s.clips.firstOrNull {
            it.isVisualizerClip &&
                    it.visualizer?.linkedAudioClipId == selectedAudio.id
        }
        if (existing != null) {
            return false
        }

        val (beatTimes, beatStrengths) = try {
            val result = BeatsEngine.detect(
                context = context, uri = selectedAudio.uri, filter = "all"
            )
            if (result.detected && result.beatTimesMs.isNotEmpty()) {
                result.beatTimesMs to result.beatStrengths
            } else {
                emptyList<Long>() to emptyList<Float>()
            }
        } catch (e: Throwable) {
            emptyList<Long>() to emptyList<Float>()
        }

        val startMs = selectedAudio.timelineStartMs
        val durMs = selectedAudio.durationMs
        val linkId = selectedAudio.linkedId
            ?: "lk-viz-${System.currentTimeMillis()}"

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
        val selectedAudio = s.selectedClip
        if (selectedAudio == null || !selectedAudio.isAudio ||
            selectedAudio.isAudioEffectClip
        ) return false

        val existing = s.clips.firstOrNull {
            it.isVisualizerClip &&
                    it.visualizer?.linkedAudioClipId == selectedAudio.id
        }
        if (existing != null) {
            return false
        }

        val startMs = selectedAudio.timelineStartMs
        val durMs = selectedAudio.durationMs
        val linkId = selectedAudio.linkedId
            ?: "lk-viz-${System.currentTimeMillis()}"

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

    fun applyEffectPreset(presetKey: String, presetLabel: String) {
        val preset = EffectLibrary.findByKey(presetKey) ?: return
        val s = _state.value
        val baseClip = s.selectedClip
        val startMs = baseClip?.timelineStartMs ?: s.currentPosMs
        val durMs = baseClip?.durationMs ?: 3000L
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

    fun setPreviewEffect(presetKey: String?, intensity: Float) {
        if (presetKey == null) {
            _state.update { it.copy(previewEffectState = null) }
            return
        }
        val preset = EffectLibrary.findByKey(presetKey) ?: return
        val scaled = buildScaledEffectState(preset, intensity)
        _state.update { it.copy(previewEffectState = scaled) }
    }

    fun clearPreviewEffect() {
        _state.update { it.copy(previewEffectState = null) }
    }

    fun createEffectLayerAt(presetKey: String, intensity: Float) {
        val preset = EffectLibrary.findByKey(presetKey) ?: return
        val s = _state.value
        val baseClip = s.selectedClip
        val startMs = baseClip?.timelineStartMs ?: s.currentPosMs
        val durMs = baseClip?.durationMs ?: 3000L
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

    fun updateEffectLayerIntensity(layerId: String, intensity: Float) {
        val clip = _state.value.clips.firstOrNull { it.id == layerId } ?: return
        if (!clip.isEffectClip) return
        val presetKey = clip.effectKeys.firstOrNull() ?: return
        val preset = EffectLibrary.findByKey(presetKey) ?: return
        val scaled = buildScaledEffectState(preset, intensity)
        updateClipDirect(layerId) { it.copy(effectState = scaled) }
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
        val scaledMotion = preset.motion?.let {
            it.copy(intensity = (it.intensity * t).coerceIn(0f, 500f))
        }
        val scaledFilters = preset.filters?.let { f ->
            fun s100(v: Float) = (100f + (v - 100f) * t).coerceIn(0f, 300f)
            fun s0(v: Float) = (v * t).coerceIn(0f, 300f)
            f.copy(
                brightness = s100(f.brightness), contrast = s100(f.contrast),
                saturation = s100(f.saturation), hue = s0(f.hue).coerceIn(0f, 360f),
                grayscale = s0(f.grayscale).coerceIn(0f, 100f),
                sepia = s0(f.sepia).coerceIn(0f, 100f),
                invert = s0(f.invert).coerceIn(0f, 100f),
                blur = s0(f.blur), opacity = s100(f.opacity).coerceIn(0f, 100f)
            )
        }
        val scaledOverlay = preset.overlay?.let {
            it.copy(intensity = (it.intensity * t).coerceIn(0f, 300f))
        }
        return EffectState(
            kind = EffectState.KIND_EFFECT, presetKey = preset.key,
            filters = scaledFilters, motion = scaledMotion,
            overlay = scaledOverlay, masterIntensity = intensity
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
        if (sel == null || !sel.isAdjustmentClip) {
            applyAdjustment(newAdj)
            return
        }
        updateClipDirect(sel.id) { it.copy(adjustments = newAdj) }
    }

    fun updateClipAdjustment(clipId: String, newAdj: AdjustmentData) {
        updateClipDirect(clipId) { it.copy(adjustments = newAdj) }
    }

    fun resetAdjustments() {
        forEachSelectedClip { clip ->
            if (clip.isAdjustmentClip) clip.copy(adjustments = AdjustmentData()) else clip
        }
    }

    fun applyOverlay(newOverlay: OverlayState) {
        val s = _state.value
        val baseClip = s.selectedClip
        val clip = EditorClip(
            id = UUID.randomUUID().toString(), uri = Uri.EMPTY,
            name = "🎬 ${newOverlay.type}", type = "overlay/plain",
            sourceStartMs = 0L, sourceEndMs = baseClip?.durationMs ?: 3000L,
            timelineStartMs = baseClip?.timelineStartMs ?: s.currentPosMs,
            trackIndex = 0, isAudio = false, sourceTotalMs = Long.MAX_VALUE,
            overlay = newOverlay
        )
        pushHistory()
        addClipOnNewLayer(clip, (baseClip?.trackIndex ?: 0) + 1)
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

    fun applyChroma(newChroma: ChromaState) {
        val s = _state.value
        val baseClip = s.selectedClip
        val clip = EditorClip(
            id = UUID.randomUUID().toString(), uri = Uri.EMPTY,
            name = "🟢 Chroma", type = "chroma/plain",
            sourceStartMs = 0L, sourceEndMs = baseClip?.durationMs ?: 3000L,
            timelineStartMs = baseClip?.timelineStartMs ?: s.currentPosMs,
            trackIndex = 0, isAudio = false, sourceTotalMs = Long.MAX_VALUE,
            chroma = newChroma
        )
        pushHistory()
        addClipOnNewLayer(clip, (baseClip?.trackIndex ?: 0) + 1)
        updateHistoryFlags()
    }

    fun updateSelectedChroma(newChroma: ChromaState) {
        val s = _state.value
        val hasChromaSelected = s.multiSelectedIds.isNotEmpty() &&
                s.clips.any { it.isChromaClip && it.id in s.multiSelectedIds }
        if (hasChromaSelected) {
            forEachSelectedClip { clip ->
                if (clip.isChromaClip) clip.copy(chroma = newChroma) else clip
            }
            return
        }
        val sel = s.selectedClip
        if (sel == null || !sel.isChromaClip) {
            applyChroma(newChroma)
            return
        }
        updateClipDirect(sel.id) { it.copy(chroma = newChroma) }
    }

    fun removeSelectedChroma() {
        val sel = _state.value.selectedClip ?: return
        if (!sel.isChromaClip) return
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

    fun setPreviewFilters(filters: FilterState?) {
        _state.update { it.copy(previewFilters = filters) }
    }

    fun clearPreviewFilters() {
        _state.update { it.copy(previewFilters = null) }
    }

    fun createFilterLayer(filters: FilterState, durationMs: Long = 0L) {
        val s = _state.value
        val selectedDur = s.selectedClip?.durationMs ?: 0L
        val effectiveDur = when {
            durationMs > 0L -> durationMs
            selectedDur > 0L -> selectedDur
            else -> 3000L
        }.coerceAtLeast(500L)

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
            else if (sel.isStickerClip && sel.stickerState != null)
                sel.copy(stickerState = sel.stickerState.copy(x = value))
            else if (sel.isVisualizerClip && sel.visualizer != null)
                sel.copy(
                    visualizer = sel.visualizer.copy(
                        positionX = value / 100f
                    )
                )
            else sel.copy(offsetX = (value - 50f) / 100f)

            "y" -> if (sel.isTextClip && sel.textState != null)
                sel.copy(textState = sel.textState.copy(positionY = value))
            else if (sel.isStickerClip && sel.stickerState != null)
                sel.copy(stickerState = sel.stickerState.copy(y = value))
            else if (sel.isVisualizerClip && sel.visualizer != null)
                sel.copy(
                    visualizer = sel.visualizer.copy(
                        positionY = value / 100f
                    )
                )
            else sel.copy(offsetY = (value - 50f) / 100f)

            "scale" -> if (sel.isTextClip && sel.textState != null)
                sel.copy(textState = sel.textState.copy(scale = value))
            else if (sel.isStickerClip && sel.stickerState != null)
                sel.copy(stickerState = sel.stickerState.copy(scale = value))
            else if (sel.isVisualizerClip && sel.visualizer != null)
                sel.copy(
                    visualizer = sel.visualizer.copy(
                        size = value / 100f * 0.32f
                    )
                )
            else sel.copy(scale = value / 100f)

            "rotation" -> if (sel.isTextClip && sel.textState != null)
                sel.copy(textState = sel.textState.copy(rotation = value))
            else if (sel.isStickerClip && sel.stickerState != null)
                sel.copy(stickerState = sel.stickerState.copy(rotation = value))
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

        updateClipDirect(sel.id) { finalClip }
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
            val base = TransformApplier.baseOf(sel)
            val currentValue = when (prop) {
                "x" -> base.x
                "y" -> base.y
                "scale" -> base.scale
                "rotation" -> base.rotation
                "anchorX" -> base.anchorX
                "anchorY" -> base.anchorY
                "cropL" -> base.cropL
                "cropR" -> base.cropR
                "cropT" -> base.cropT
                "cropB" -> base.cropB
                else -> 0f
            }
            sel.copy(
                keyframes = KeyframeStore.setKeyframe(
                    sel.keyframes, prop, currentTimeSec, currentValue
                )
            )
        }
        updateClipDirect(sel.id) { updated }
    }

    fun toggleKeyframeAll() {
        val sel = _state.value.selectedClip ?: return
        val currentTimeSec = KeyframeStore.clipLocalTimeSeconds(
            _state.value.currentPosMs, sel.timelineStartMs, sel.durationMs
        )
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
            sel.copy(keyframes = kf)
        }
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
        updateClipDirect(sel.id) { updated }
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

        updateClipDirect(sel.id) {
            it.copy(keyframes = it.keyframes + (prop to filtered))
        }
        updateHistoryFlags()
    }

    fun deleteKeyframeFromGraph(prop: String, time: Float) {
        val sel = _state.value.selectedClip ?: return
        updateClipDirect(sel.id) {
            it.copy(keyframes = KeyframeStore.removeKeyframe(it.keyframes, prop, time))
        }
        updateHistoryFlags()
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

            sel.isStickerClip && sel.stickerState != null -> sel.copy(
                stickerState = sel.stickerState.copy(
                    x = 50f, y = 50f, scale = 100f, rotation = 0f
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

    fun setCrop(l: Float, r: Float, t: Float, b: Float) {
        val cl = l.coerceIn(0f, 0.45f)
        val cr = r.coerceIn(0f, 0.45f)
        val ct = t.coerceIn(0f, 0.45f)
        val cb = b.coerceIn(0f, 0.45f)
        forEachSelectedClip { it.copy(cropL = cl, cropR = cr, cropT = ct, cropB = cb) }
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
            if (clip.isAudio) continue
            val hasLeft = list.any { other ->
                other.id != clip.id && !other.isAudio &&
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
            if (clip.isAudio) continue
            if (abs(clip.timelineStartMs - targetMs) < 200L) {
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
                    val peakScale = (base.scale * (1f + amount / 100f))
                        .coerceIn(10f, 500f)
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
}


// Bundle of config passed from UI to ViewModel when starting export
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


// Holds clips in memory between ViewModel and Service
object ExportClipsHolder {
    @Volatile
    var clips: List<EditorClip> = emptyList()

    fun set(newClips: List<EditorClip>) {
        clips = newClips
        android.util.Log.e(
            "ExportClipsHolder",
            "Clips set: ${newClips.size}"
        )
    }

    fun clear() {
        clips = emptyList()
    }
}