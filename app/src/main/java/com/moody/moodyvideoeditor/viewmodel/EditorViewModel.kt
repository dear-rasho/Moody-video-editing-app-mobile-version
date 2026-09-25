package com.moody.moodyvideoeditor.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import com.moody.moodyvideoeditor.data.AdjustmentData
import com.moody.moodyvideoeditor.data.BeatsState
import com.moody.moodyvideoeditor.data.ChromaState
import com.moody.moodyvideoeditor.data.ColorWheelState
import com.moody.moodyvideoeditor.data.EditorClip
import com.moody.moodyvideoeditor.data.EditorState
import com.moody.moodyvideoeditor.data.EffectLibrary
import com.moody.moodyvideoeditor.data.EffectState
import com.moody.moodyvideoeditor.data.FilterState
import com.moody.moodyvideoeditor.data.Keyframe
import com.moody.moodyvideoeditor.data.OverlayState
import com.moody.moodyvideoeditor.data.RatioState
import com.moody.moodyvideoeditor.data.StickerState
import com.moody.moodyvideoeditor.data.TextState
import com.moody.moodyvideoeditor.data.TransitionState
import com.moody.moodyvideoeditor.utils.DeleteEngine
import com.moody.moodyvideoeditor.utils.DuplicateEngine
import com.moody.moodyvideoeditor.utils.FreezeEngine
import com.moody.moodyvideoeditor.utils.HistoryManager
import com.moody.moodyvideoeditor.utils.KeyframeStore
import com.moody.moodyvideoeditor.utils.SpeedEngine
import com.moody.moodyvideoeditor.utils.TimelineEngine
import com.moody.moodyvideoeditor.utils.TimelineTools
import com.moody.moodyvideoeditor.utils.TimelineZoom
import com.moody.moodyvideoeditor.utils.TransformApplier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

class EditorViewModel : ViewModel() {

    private val _state = MutableStateFlow(EditorState())
    val state: StateFlow<EditorState> = _state.asStateFlow()

    private val history = HistoryManager()

    // ═══════════════════════════════════════════════════════════
    private fun pushHistory() {
        history.push(_state.value.clips)
        updateHistoryFlags()
    }

    private fun updateHistoryFlags() {
        _state.update {
            it.copy(canUndo = history.canUndo(), canRedo = history.canRedo())
        }
    }

    private fun updateClipDirect(clipId: String, transform: (EditorClip) -> EditorClip) {
        val list = _state.value.clips.toMutableList()
        val idx = list.indexOfFirst { it.id == clipId }
        if (idx < 0) return
        list[idx] = transform(list[idx])
        _state.update { it.copy(clips = list) }
    }

    // ═══════════════════════════════════════════════════════════
    //  DRAG SANDBOXING
    // ═══════════════════════════════════════════════════════════
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

    // ═══════════════════════════════════════════════════════════
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
                    TimelineZoom.SLIDER_MIN,
                    TimelineZoom.SLIDER_MAX
                )
            )
        }

    // 🆕 Export settings
    fun setExportResolution(res: String) =
        _state.update { it.copy(exportResolution = res) }

    fun setExportFps(fps: Int) =
        _state.update { it.copy(exportFps = fps) }

    fun setExportBitrate(kbps: Int) =
        _state.update { it.copy(exportBitrateKbps = kbps.coerceIn(1000, 50000)) }

    fun setExportFormat(fmt: String) =
        _state.update { it.copy(exportFormat = fmt) }

    fun setExportFolderUri(uri: String?) =
        _state.update { it.copy(exportFolderUri = uri) }

    // 🆕 Track visibility / mute
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

    fun isVisualTrackHidden(trackIndex: Int): Boolean =
        _state.value.hiddenVisualTracks.contains(trackIndex)

    fun isAudioTrackMuted(trackIndex: Int): Boolean =
        _state.value.mutedAudioTracks.contains(trackIndex)

    // ═══════════════════════════════════════════════════════════
    fun addClipWithSource(uri: Uri, name: String, duration: Long, sourceTotalMs: Long) {
        pushHistory()
        val linkId = "lk-${System.currentTimeMillis()}"
        val videoClip = EditorClip(
            uri = uri, name = name, type = "video/mp4",
            sourceStartMs = 0L, sourceEndMs = duration,
            timelineStartMs = 0L, trackIndex = 0, isAudio = false,
            sourceTotalMs = sourceTotalMs, linkedId = linkId
        )
        val audioClip = videoClip.copy(
            id = UUID.randomUUID().toString(),
            name = "$name (audio)", type = "audio/mpeg",
            trackIndex = 0, isAudio = true
        )
        val list = _state.value.clips.toMutableList()
        list.add(videoClip); list.add(audioClip)
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

    fun addClipSmart(uri: Uri, name: String, durationMs: Long) {
        val s = _state.value
        val durMs = if (durationMs < 100L) 5_000L else durationMs.coerceAtLeast(1_000L)

        val visualPlacement = TimelineTools.findPlacement(
            visualTracks = s.timelineVisualList(),
            visualLayerCount = s.visualLayerCount,
            playheadMs = s.currentPosMs,
            durMs = durMs
        )
        val audioPlacement = TimelineTools.findPlacement(
            visualTracks = s.timelineAudioList(),
            visualLayerCount = s.audioLayerCount,
            playheadMs = s.currentPosMs,
            durMs = durMs
        )

        pushHistory()
        val linkId = "lk-${System.currentTimeMillis()}"
        val videoClip = EditorClip(
            uri = uri, name = name, type = "video/mp4",
            sourceStartMs = 0L, sourceEndMs = durMs,
            timelineStartMs = s.currentPosMs,
            trackIndex = visualPlacement.trackIndex,
            isAudio = false,
            sourceTotalMs = durMs,
            linkedId = linkId
        )
        val audioClip = videoClip.copy(
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

            val newAudioList = st.timelineAudioList().toMutableList()
            while (newAudioList.size <= audioPlacement.trackIndex)
                newAudioList.add(emptyList())
            newAudioList[audioPlacement.trackIndex] =
                newAudioList[audioPlacement.trackIndex] + audioClip

            st.copy(
                clips = newVisualList.flatten() + newAudioList.flatten(),
                visualLayerCount = maxOf(
                    st.visualLayerCount,
                    visualPlacement.trackIndex + 1
                ),
                audioLayerCount = maxOf(
                    st.audioLayerCount,
                    audioPlacement.trackIndex + 1
                ),
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

    // ═══════════════════════════════════════════════════════════
    //  MOVE CLIP
    // ═══════════════════════════════════════════════════════════
    fun moveClip(clipId: String, targetTrackIndex: Int, newIsAudio: Boolean, newTimelineMs: Long) {
        val s = _state.value
        val clip = s.clips.firstOrNull { it.id == clipId } ?: return
        val list = s.clips.toMutableList()
        val idx = list.indexOfFirst { it.id == clipId }
        if (idx < 0) return

        val isAudio = clip.isAudio
        val targetTrack = targetTrackIndex.coerceAtLeast(0)
        val targetStart = newTimelineMs.coerceAtLeast(0L)
        val targetEnd = targetStart + clip.durationMs
        val trackDelta = targetTrack - clip.trackIndex

        val linkedClip = clip.linkedId?.let { linkId ->
            list.firstOrNull { it.linkedId == linkId && it.id != clipId }
        }

        // STEP 1 — Push overlapping clips
        var maxShiftedTrack = targetTrack - 1
        var probeTrack = targetTrack
        while (true) {
            val hasOverlap = list.any { c ->
                c.id != clipId &&
                        (linkedClip == null || c.id != linkedClip.id) &&
                        c.isAudio == isAudio &&
                        c.trackIndex == probeTrack &&
                        c.timelineStartMs < targetEnd &&
                        targetStart < c.timelineEndMs
            }
            if (!hasOverlap) break
            maxShiftedTrack = probeTrack
            probeTrack++
        }

        for (i in list.indices) {
            if (list[i].id == clipId) continue
            if (linkedClip != null && list[i].id == linkedClip.id) continue
            if (list[i].isAudio != isAudio) continue
            val t = list[i].trackIndex
            if (t < targetTrack || t > maxShiftedTrack) continue
            val overlaps = list[i].timelineStartMs < targetEnd &&
                    targetStart < list[i].timelineEndMs
            if (overlaps) {
                list[i] = list[i].copy(trackIndex = t + 1)
            }
        }

        // STEP 2 — Place moving clip
        list[idx] = list[idx].copy(
            trackIndex = targetTrack,
            timelineStartMs = targetStart
        )

        // STEP 3 — Linked clip sync
        if (linkedClip != null) {
            val li = list.indexOfFirst { it.id == linkedClip.id }
            if (li >= 0) {
                val linkedIsAudio = linkedClip.isAudio
                val linkedNewTrack = (linkedClip.trackIndex + trackDelta).coerceAtLeast(0)
                val linkedTargetEnd = targetStart + linkedClip.durationMs

                var linkedMaxShift = linkedNewTrack - 1
                var linkedProbe = linkedNewTrack
                while (true) {
                    val hasOverlap = list.any { c ->
                        c.id != clipId &&
                                c.id != linkedClip.id &&
                                c.isAudio == linkedIsAudio &&
                                c.trackIndex == linkedProbe &&
                                c.timelineStartMs < linkedTargetEnd &&
                                targetStart < c.timelineEndMs
                    }
                    if (!hasOverlap) break
                    linkedMaxShift = linkedProbe
                    linkedProbe++
                }

                for (i in list.indices) {
                    if (list[i].id == clipId) continue
                    if (list[i].id == linkedClip.id) continue
                    if (list[i].isAudio != linkedIsAudio) continue
                    val t = list[i].trackIndex
                    if (t < linkedNewTrack || t > linkedMaxShift) continue
                    val overlaps = list[i].timelineStartMs < linkedTargetEnd &&
                            targetStart < list[i].timelineEndMs
                    if (overlaps) {
                        list[i] = list[i].copy(trackIndex = t + 1)
                    }
                }

                list[li] = list[li].copy(
                    trackIndex = linkedNewTrack,
                    timelineStartMs = targetStart
                )
            }
        }

        // Layer count
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

    // ═══════════════════════════════════════════════════════════
    //  TRACK SWAP
    // ═══════════════════════════════════════════════════════════
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

    // ═══════════════════════════════════════════════════════════
    private fun findOrCreateVisualTrack(
        preferredTrack: Int, startMs: Long, durMs: Long
    ): Int {
        val s = _state.value
        val endMs = startMs + durMs
        for (t in (s.visualLayerCount - 1) downTo 0) {
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

    // ═══════════════════════════════════════════════════════════
    //  TIMELINE TOOLS
    // ═══════════════════════════════════════════════════════════
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

    // ═══════════════════════════════════════════════════════════
    //  TRIM
    // ═══════════════════════════════════════════════════════════
    private fun syncLinkedTrim(primary: EditorClip) {
        val linkId = primary.linkedId ?: return
        val list = _state.value.clips.toMutableList()
        val li = list.indexOfFirst {
            it.linkedId == linkId && it.id != primary.id
        }
        if (li < 0) return
        list[li] = list[li].copy(
            sourceStartMs = primary.sourceStartMs,
            sourceEndMs = primary.sourceEndMs,
            timelineStartMs = primary.timelineStartMs
        )
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
            val linked = _state.value.clips.firstOrNull {
                it.linkedId == sel.linkedId && it.id != sel.id
            }
            if (linked != null) {
                updateClipDirect(linked.id) { clip ->
                    clip.copy(
                        sourceStartMs = clampedSourceStart,
                        timelineStartMs = (clip.timelineStartMs + delta)
                            .coerceAtLeast(0L)
                    )
                }
            }
        }
    }

    fun trimClipRight(newSourceEndMs: Long) {
        val sel = _state.value.selectedClip ?: return
        val minEnd = sel.sourceStartMs + EditorClip.MIN_DURATION_MS
        val maxEnd = if (sel.sourceTotalMs != Long.MAX_VALUE)
            sel.sourceTotalMs else Long.MAX_VALUE
        val clampedEnd = newSourceEndMs.coerceIn(minEnd, maxEnd)
        if (clampedEnd == sel.sourceEndMs) return
        updateClipDirect(sel.id) { it.copy(sourceEndMs = clampedEnd) }
        if (sel.linkedId != null) {
            val linked = _state.value.clips.firstOrNull {
                it.linkedId == sel.linkedId && it.id != sel.id
            }
            if (linked != null) updateClipDirect(linked.id) {
                it.copy(sourceEndMs = clampedEnd)
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
        val first = sel.copy(id = "${sel.id}-A", sourceEndMs = splitMs)
        val second = sel.copy(
            id = "${sel.id}-B",
            sourceStartMs = splitMs,
            linkedId = null
        )
        val list = s.clips.toMutableList()
        val idx = list.indexOfFirst { it.id == sel.id }
        list.removeAt(idx); list.add(idx, second); list.add(idx, first)
        TimelineEngine.recalcTrackTimings(list, sel.trackIndex, sel.isAudio)
        _state.update { it.copy(clips = list, selectedClipId = first.id) }
        updateHistoryFlags()
    }

    fun commitTrim() = pushHistory()

    fun deleteCurrentClip() {
        val sel = _state.value.selectedClip ?: return
        pushHistory()
        val ids = DeleteEngine.collectForDeletion(sel, _state.value.clips)
        val newList = DeleteEngine.applyDeletion(_state.value.clips, ids)
        _state.update {
            it.copy(clips = newList, selectedClipId = null, currentPosMs = 0L)
        }
        updateHistoryFlags()
    }

    fun duplicateCurrentClip() {
        val sel = _state.value.selectedClip ?: return
        pushHistory()
        val copy = DuplicateEngine.duplicateAfter(sel, _state.value.clips)
        val list = _state.value.clips.toMutableList()
        list.add(copy)
        TimelineEngine.recalcTrackTimings(list, sel.trackIndex, sel.isAudio)
        _state.update { it.copy(clips = list, selectedClipId = copy.id) }
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
        val sel = _state.value.selectedClip ?: return
        val clamped = speed.coerceIn(SpeedEngine.MIN_SPEED, SpeedEngine.MAX_SPEED)
        updateClipDirect(sel.id) { it.copy(speed = clamped) }
        val updated = _state.value.clips
        val synced = SpeedEngine.syncLinkedSpeed(
            updated.first { it.id == sel.id }, clamped, updated
        )
        _state.update { it.copy(clips = synced, speed = clamped) }
    }

    fun resetSpeed() {
        pushHistory(); setSpeed(1.0f); updateHistoryFlags()
    }

    fun getSelectedBaseDurationMs(): Long =
        _state.value.selectedClip?.let { SpeedEngine.getBaseDuration(it) } ?: 0L

    // ═══════════════════════════════════════════════════════════
    //  TEXT
    // ═══════════════════════════════════════════════════════════
    fun createTextClip(initial: TextState = TextState(content = "Text")) {
        val s = _state.value
        val clip = EditorClip(
            id = UUID.randomUUID().toString(),
            uri = Uri.EMPTY,
            name = "📝 ${initial.content.take(18).ifBlank { "Text" }}",
            type = "text/plain",
            sourceStartMs = 0L,
            sourceEndMs = 3000L,
            timelineStartMs = s.currentPosMs,
            trackIndex = 0,
            isAudio = false,
            textState = initial
        )
        pushHistory()
        addClipOnNewLayer(clip, (s.selectedClip?.trackIndex ?: 0) + 1)
        updateHistoryFlags()
    }

    fun updateSelectedText(newState: TextState) {
        val sel = _state.value.selectedClip
        if (sel == null || !sel.isTextClip) return
        updateClipDirect(sel.id) {
            it.copy(
                textState = newState,
                name = "📝 ${newState.content.take(18).ifBlank { "Text" }}"
            )
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
        val st = getSelectedTextState()
        updateSelectedText(st.copy(animation = animation))
    }

    // 🆕 Preview direct manipulation (keyframe-aware)
    fun updateTextPositionDirect(clipId: String, x: Float, y: Float) {
        val s = _state.value
        val clip = s.clips.firstOrNull { it.id == clipId } ?: return
        if (!clip.isTextClip) return
        val currentTimeSec = ((s.currentPosMs - clip.timelineStartMs).toFloat() / 1000f)
            .coerceAtLeast(0f)
        val hasAnyKf = KeyframeStore.hasAnyKeyframes(clip.keyframes)

        updateClipDirect(clipId) { c ->
            val st = c.textState ?: return@updateClipDirect c
            var kf = c.keyframes
            if (hasAnyKf) {
                kf = KeyframeStore.autoKeyframeIfActive(kf, "x", currentTimeSec, x)
                kf = KeyframeStore.autoKeyframeIfActive(kf, "y", currentTimeSec, y)
                c.copy(textState = st.copy(positionX = x, positionY = y), keyframes = kf)
            } else {
                c.copy(textState = st.copy(positionX = x, positionY = y))
            }
        }
    }

    fun updateTextTransformDirect(clipId: String, scale: Float, rotation: Float) {
        val s = _state.value
        val clip = s.clips.firstOrNull { it.id == clipId } ?: return
        if (!clip.isTextClip) return
        val currentTimeSec = ((s.currentPosMs - clip.timelineStartMs).toFloat() / 1000f)
            .coerceAtLeast(0f)
        val hasAnyKf = KeyframeStore.hasAnyKeyframes(clip.keyframes)

        updateClipDirect(clipId) { c ->
            val st = c.textState ?: return@updateClipDirect c
            var kf = c.keyframes
            if (hasAnyKf) {
                kf = KeyframeStore.autoKeyframeIfActive(kf, "scale", currentTimeSec, scale)
                kf = KeyframeStore.autoKeyframeIfActive(kf, "rotation", currentTimeSec, rotation)
                c.copy(textState = st.copy(scale = scale, rotation = rotation), keyframes = kf)
            } else {
                c.copy(textState = st.copy(scale = scale, rotation = rotation))
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  STICKER
    // ═══════════════════════════════════════════════════════════
    fun addOrUpdateSticker(emoji: String) {
        val sel = _state.value.selectedClip
        if (sel != null && sel.isStickerClip) {
            updateClipDirect(sel.id) {
                it.copy(
                    stickerState = (it.stickerState ?: StickerState())
                        .copy(emoji = emoji),
                    name = emoji
                )
            }
            return
        }
        val s = _state.value
        val clip = EditorClip(
            id = UUID.randomUUID().toString(),
            uri = Uri.EMPTY,
            name = emoji,
            type = "sticker/plain",
            sourceStartMs = 0L,
            sourceEndMs = 3000L,
            timelineStartMs = s.currentPosMs,
            trackIndex = 0,
            isAudio = false,
            stickerState = StickerState(emoji = emoji)
        )
        pushHistory()
        addClipOnNewLayer(clip, (s.selectedClip?.trackIndex ?: 0) + 1)
        updateHistoryFlags()
    }

    fun updateSelectedSticker(newState: StickerState) {
        val sel = _state.value.selectedClip ?: return
        if (!sel.isStickerClip) return
        updateClipDirect(sel.id) { it.copy(stickerState = newState) }
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
        val currentTimeSec = ((s.currentPosMs - clip.timelineStartMs).toFloat() / 1000f)
            .coerceAtLeast(0f)
        val hasAnyKf = KeyframeStore.hasAnyKeyframes(clip.keyframes)

        updateClipDirect(clipId) { c ->
            val ss = c.stickerState ?: return@updateClipDirect c
            var kf = c.keyframes
            if (hasAnyKf) {
                kf = KeyframeStore.autoKeyframeIfActive(kf, "x", currentTimeSec, x)
                kf = KeyframeStore.autoKeyframeIfActive(kf, "y", currentTimeSec, y)
                c.copy(stickerState = ss.copy(x = x, y = y), keyframes = kf)
            } else {
                c.copy(stickerState = ss.copy(x = x, y = y))
            }
        }
    }

    fun updateStickerTransformDirect(clipId: String, scale: Float, rotation: Float) {
        val s = _state.value
        val clip = s.clips.firstOrNull { it.id == clipId } ?: return
        if (!clip.isStickerClip) return
        val currentTimeSec = ((s.currentPosMs - clip.timelineStartMs).toFloat() / 1000f)
            .coerceAtLeast(0f)
        val hasAnyKf = KeyframeStore.hasAnyKeyframes(clip.keyframes)

        updateClipDirect(clipId) { c ->
            val ss = c.stickerState ?: return@updateClipDirect c
            var kf = c.keyframes
            if (hasAnyKf) {
                kf = KeyframeStore.autoKeyframeIfActive(kf, "scale", currentTimeSec, scale)
                kf = KeyframeStore.autoKeyframeIfActive(kf, "rotation", currentTimeSec, rotation)
                c.copy(stickerState = ss.copy(scale = scale, rotation = rotation), keyframes = kf)
            } else {
                c.copy(stickerState = ss.copy(scale = scale, rotation = rotation))
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  EFFECT
    // ═══════════════════════════════════════════════════════════
    fun applyEffectPreset(presetKey: String, presetLabel: String) {
        val preset = EffectLibrary.findByKey(presetKey) ?: return
        val s = _state.value
        val baseClip = s.selectedClip
        val startMs = baseClip?.timelineStartMs ?: s.currentPosMs
        val durMs = baseClip?.durationMs ?: 3000L
        val baseTrack = baseClip?.trackIndex ?: 0

        val effectState = EffectState(
            kind = EffectState.KIND_EFFECT,
            presetKey = preset.key,
            filters = preset.filters,
            motion = preset.motion,
            overlay = preset.overlay
        )

        val clip = EditorClip(
            id = UUID.randomUUID().toString(),
            uri = Uri.EMPTY,
            name = "✨ $presetLabel",
            type = "effect/plain",
            sourceStartMs = 0L,
            sourceEndMs = durMs,
            timelineStartMs = startMs,
            trackIndex = 0,
            isAudio = false,
            effectKeys = listOf(presetKey),
            effectState = effectState
        )
        pushHistory()
        addClipOnNewLayer(clip, baseTrack + 1)
        updateHistoryFlags()
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

    fun clearAllEffects() {}

    // ═══════════════════════════════════════════════════════════
    fun applyAdjustment(newAdj: AdjustmentData) {
        val s = _state.value
        val baseClip = s.selectedClip
        val clip = EditorClip(
            id = UUID.randomUUID().toString(),
            uri = Uri.EMPTY,
            name = "🎚️ Adjust",
            type = "adjustment/plain",
            sourceStartMs = 0L,
            sourceEndMs = baseClip?.durationMs ?: 3000L,
            timelineStartMs = baseClip?.timelineStartMs ?: s.currentPosMs,
            trackIndex = 0,
            isAudio = false,
            adjustments = newAdj
        )
        pushHistory()
        addClipOnNewLayer(clip, (baseClip?.trackIndex ?: 0) + 1)
        updateHistoryFlags()
    }

    fun updateSelectedAdjustment(newAdj: AdjustmentData) {
        val sel = _state.value.selectedClip
        if (sel == null || !sel.isAdjustmentClip) {
            applyAdjustment(newAdj)
            return
        }
        updateClipDirect(sel.id) { it.copy(adjustments = newAdj) }
    }

    fun resetAdjustments() {
        val sel = _state.value.selectedClip
        if (sel != null && sel.isAdjustmentClip) {
            updateClipDirect(sel.id) { it.copy(adjustments = AdjustmentData()) }
        }
    }

    // ═══════════════════════════════════════════════════════════
    fun applyOverlay(newOverlay: OverlayState) {
        val s = _state.value
        val baseClip = s.selectedClip
        val clip = EditorClip(
            id = UUID.randomUUID().toString(),
            uri = Uri.EMPTY,
            name = "🎬 ${newOverlay.type}",
            type = "overlay/plain",
            sourceStartMs = 0L,
            sourceEndMs = baseClip?.durationMs ?: 3000L,
            timelineStartMs = baseClip?.timelineStartMs ?: s.currentPosMs,
            trackIndex = 0,
            isAudio = false,
            overlay = newOverlay
        )
        pushHistory()
        addClipOnNewLayer(clip, (baseClip?.trackIndex ?: 0) + 1)
        updateHistoryFlags()
    }

    fun updateSelectedOverlay(newOverlay: OverlayState) {
        val sel = _state.value.selectedClip
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

    // ═══════════════════════════════════════════════════════════
    fun applyChroma(newChroma: ChromaState) {
        val s = _state.value
        val baseClip = s.selectedClip
        val clip = EditorClip(
            id = UUID.randomUUID().toString(),
            uri = Uri.EMPTY,
            name = "🟢 Chroma",
            type = "chroma/plain",
            sourceStartMs = 0L,
            sourceEndMs = baseClip?.durationMs ?: 3000L,
            timelineStartMs = baseClip?.timelineStartMs ?: s.currentPosMs,
            trackIndex = 0,
            isAudio = false,
            chroma = newChroma
        )
        pushHistory()
        addClipOnNewLayer(clip, (baseClip?.trackIndex ?: 0) + 1)
        updateHistoryFlags()
    }

    fun updateSelectedChroma(newChroma: ChromaState) {
        val sel = _state.value.selectedClip
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

    // ═══════════════════════════════════════════════════════════
    fun updateFilters(newFilters: FilterState) {
        val sel = _state.value.selectedClip ?: return
        if (!sel.isVisualClip) return
        updateClipDirect(sel.id) { it.copy(filters = newFilters) }
    }

    fun resetFilters() {
        val sel = _state.value.selectedClip ?: return
        updateClipDirect(sel.id) { it.copy(filters = FilterState()) }
    }

    fun updateColorWheel(newState: ColorWheelState) {
        val sel = _state.value.selectedClip ?: return
        if (!sel.isVisualClip) return
        updateClipDirect(sel.id) { it.copy(colorWheel = newState) }
    }

    fun resetColorWheel() {
        val sel = _state.value.selectedClip ?: return
        updateClipDirect(sel.id) { it.copy(colorWheel = ColorWheelState()) }
    }

    // ═══════════════════════════════════════════════════════════
    fun updateTransition(state: TransitionState) {
        val sel = _state.value.selectedClip ?: return
        updateClipDirect(sel.id) { it.copy(transition = state) }
    }

    fun removeTransition() {
        val sel = _state.value.selectedClip ?: return
        updateClipDirect(sel.id) { it.copy(transition = null) }
    }

    fun removeTransitionFor(clipId: String) {
        updateClipDirect(clipId) { it.copy(transition = null) }
    }

    fun setTransitionDuration(clipId: String, durationMs: Long) {
        updateClipDirect(clipId) { clip ->
            clip.transition?.let { t ->
                clip.copy(
                    transition = t.copy(
                        durationMs = durationMs.coerceIn(200L, 3000L)
                    )
                )
            } ?: clip
        }
    }

    fun updateRatio(state: RatioState) = _state.update {
        it.copy(aspectRatio = state.key, aspectMode = 0)
    }

    fun updateBeats(state: BeatsState) = _state.update {
        it.copy(
            beatsDetected = state.detected,
            beatsCount = state.count,
            beatsFilter = state.filter
        )
    }

    fun clearBeats() = _state.update {
        it.copy(beatsDetected = false, beatsCount = 0, beatsFilter = "all")
    }

    fun setVolume(v: Float) = _state.update { it.copy(volume = v.coerceIn(0f, 1f)) }
    fun toggleMute() = _state.update { it.copy(isMuted = !it.isMuted) }
    fun setRotation(deg: Int) = _state.update { it.copy(rotation = deg) }
    fun setAspectMode(mode: Int) = _state.update { it.copy(aspectMode = mode) }
    fun setAudioFx(fx: String) = _state.update { it.copy(audioFx = fx) }
    fun setSoundFx(fx: String) = _state.update { it.copy(soundFx = fx) }

    fun setClipScale(v: Float) {
        val sel = _state.value.selectedClip ?: return
        updateClipDirect(sel.id) { it.copy(scale = v.coerceIn(0.1f, 3f)) }
    }

    fun setClipRotation(v: Float) {
        val sel = _state.value.selectedClip ?: return
        updateClipDirect(sel.id) { it.copy(rotation = v) }
    }

    fun setClipOffset(x: Float, y: Float) {
        val sel = _state.value.selectedClip ?: return
        updateClipDirect(sel.id) { it.copy(offsetX = x, offsetY = y) }
    }

    fun setCrop(l: Float, r: Float, t: Float, b: Float) {
        val sel = _state.value.selectedClip ?: return
        updateClipDirect(sel.id) {
            it.copy(
                cropL = l.coerceIn(0f, 0.45f),
                cropR = r.coerceIn(0f, 0.45f),
                cropT = t.coerceIn(0f, 0.45f),
                cropB = b.coerceIn(0f, 0.45f)
            )
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  TRANSFORM + KEYFRAMES
    // ═══════════════════════════════════════════════════════════
    fun changeTransformProperty(prop: String, value: Float) {
        val sel = _state.value.selectedClip ?: return
        val currentTimeSec = ((_state.value.currentPosMs - sel.timelineStartMs)
            .toFloat() / 1000f).coerceAtLeast(0f)

        val updated = when (prop) {
            "x" -> if (sel.isTextClip && sel.textState != null)
                sel.copy(textState = sel.textState.copy(positionX = value))
            else if (sel.isStickerClip && sel.stickerState != null)
                sel.copy(stickerState = sel.stickerState.copy(x = value))
            else sel.copy(offsetX = (value - 50f) / 100f)

            "y" -> if (sel.isTextClip && sel.textState != null)
                sel.copy(textState = sel.textState.copy(positionY = value))
            else if (sel.isStickerClip && sel.stickerState != null)
                sel.copy(stickerState = sel.stickerState.copy(y = value))
            else sel.copy(offsetY = (value - 50f) / 100f)

            "scale" -> if (sel.isTextClip && sel.textState != null)
                sel.copy(textState = sel.textState.copy(scale = value))
            else if (sel.isStickerClip && sel.stickerState != null)
                sel.copy(stickerState = sel.stickerState.copy(scale = value))
            else sel.copy(scale = value / 100f)

            "rotation" -> if (sel.isTextClip && sel.textState != null)
                sel.copy(textState = sel.textState.copy(rotation = value))
            else if (sel.isStickerClip && sel.stickerState != null)
                sel.copy(stickerState = sel.stickerState.copy(rotation = value))
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
        val currentTimeSec = ((_state.value.currentPosMs - sel.timelineStartMs)
            .toFloat() / 1000f).coerceAtLeast(0f)

        val existing = KeyframeStore.hasKeyframeAt(
            sel.keyframes, prop, currentTimeSec
        )

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
        val currentTimeSec = ((_state.value.currentPosMs - sel.timelineStartMs)
            .toFloat() / 1000f).coerceAtLeast(0f)

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
        val currentTimeSec = ((_state.value.currentPosMs - sel.timelineStartMs)
            .toFloat() / 1000f).coerceAtLeast(0f)
        return KeyframeStore.hasAnyKeyframeAt(sel.keyframes, currentTimeSec)
    }

    fun setEaseAtPlayhead(ease: String) {
        val sel = _state.value.selectedClip ?: return
        val currentTimeSec = ((_state.value.currentPosMs - sel.timelineStartMs)
            .toFloat() / 1000f).coerceAtLeast(0f)
        val updated = sel.copy(
            keyframes = KeyframeStore.setAllEasesAtTime(
                sel.keyframes, currentTimeSec, ease
            )
        )
        updateClipDirect(sel.id) { updated }
    }

    fun updateKeyframeInGraph(
        prop: String,
        oldTime: Float,
        newTime: Float,
        newValue: Float
    ) {
        val sel = _state.value.selectedClip ?: return
        val list = KeyframeStore.getKeyframes(sel.keyframes, prop).toMutableList()
        val idx = list.indexOfFirst { kotlin.math.abs(it.time - oldTime) < 0.08f }
        if (idx < 0) return

        val clipDurSec = sel.durationMs / 1000f
        val t = newTime.coerceIn(0f, clipDurSec)

        val filtered = list.filterIndexed { i, kf ->
            i == idx || kotlin.math.abs(kf.time - t) > 0.08f
        }.toMutableList()

        val realIdx = filtered.indexOfFirst {
            kotlin.math.abs(it.time - oldTime) < 0.08f
        }
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
            it.copy(
                keyframes = KeyframeStore.removeKeyframe(
                    it.keyframes, prop, time
                )
            )
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

            else -> sel.copy(
                offsetX = 0f, offsetY = 0f,
                scale = 1f, rotation = 0f,
                cropL = 0f, cropR = 0f, cropT = 0f, cropB = 0f,
                keyframes = emptyMap()
            )
        }
        updateClipDirect(sel.id) { updated }
        updateHistoryFlags()
    }

    // ═══════════════════════════════════════════════════════════
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