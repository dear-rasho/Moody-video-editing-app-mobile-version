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
    //  HISTORY
    // ═══════════════════════════════════════════════════════════
    private fun pushHistory() {
        history.push(_state.value.clips)
        updateHistoryFlags()
    }

    private fun updateHistoryFlags() {
        _state.update { it.copy(canUndo = history.canUndo(), canRedo = history.canRedo()) }
    }

    private fun updateClipDirect(clipId: String, transform: (EditorClip) -> EditorClip) {
        val list = _state.value.clips.toMutableList()
        val idx = list.indexOfFirst { it.id == clipId }
        if (idx < 0) return
        list[idx] = transform(list[idx])
        _state.update { it.copy(clips = list) }
    }

    // ═══════════════════════════════════════════════════════════
    //  LAYERS
    // ═══════════════════════════════════════════════════════════
    fun addVisualLayer() = _state.update { it.copy(visualLayerCount = it.visualLayerCount + 1) }
    fun addAudioLayer() = _state.update { it.copy(audioLayerCount = it.audioLayerCount + 1) }

    fun selectTrack(trackIndex: Int, isAudio: Boolean) {
        _state.update {
            it.copy(
                selectedTrackIndex = trackIndex,
                selectedIsAudio = isAudio,
                selectedClipId = null
            )
        }
    }

    fun ensureLayerExists(trackIndex: Int, isAudio: Boolean) {
        _state.update { s ->
            if (isAudio && trackIndex >= s.audioLayerCount) s.copy(audioLayerCount = trackIndex + 1)
            else if (!isAudio && trackIndex >= s.visualLayerCount) s.copy(visualLayerCount = trackIndex + 1)
            else s
        }
    }

    fun setTimelineZoom(z: Float) = _state.update { it.copy(timelineZoom = z.coerceIn(0.5f, 4f)) }

    // ═══════════════════════════════════════════════════════════
    //  CLIPS
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
        list.add(videoClip)
        list.add(audioClip)

        _state.update {
            it.copy(
                clips = list, currentIndex = 0, currentPosMs = 0L,
                selectedClipId = videoClip.id, selectedTrackIndex = 0, selectedIsAudio = false,
                visualLayerCount = maxOf(it.visualLayerCount, 3),
                audioLayerCount = maxOf(it.audioLayerCount, 2)
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

    fun moveClip(clipId: String, newTrackIndex: Int, newIsAudio: Boolean, newTimelineMs: Long) {
        val clip = _state.value.clips.firstOrNull { it.id == clipId } ?: return
        val list = _state.value.clips.toMutableList()
        val idx = list.indexOfFirst { it.id == clipId }
        if (idx < 0) return
        val clampedStart = newTimelineMs.coerceAtLeast(0L)
        val oldTrack = clip.trackIndex
        val oldAudio = clip.isAudio

        list[idx] = list[idx].copy(
            trackIndex = newTrackIndex,
            isAudio = newIsAudio,
            timelineStartMs = clampedStart
        )

        if (clip.linkedId != null) {
            val li = list.indexOfFirst { it.linkedId == clip.linkedId && it.id != clipId }
            if (li >= 0) list[li] = list[li].copy(timelineStartMs = clampedStart)
        }
        if (oldTrack != newTrackIndex || oldAudio != newIsAudio) {
            TimelineEngine.recalcTrackTimings(list, oldTrack, oldAudio)
            TimelineEngine.recalcTrackTimings(list, newTrackIndex, newIsAudio)
        }
        _state.update {
            it.copy(
                clips = list, selectedClipId = clipId,
                selectedTrackIndex = newTrackIndex, selectedIsAudio = newIsAudio
            )
        }
        updateHistoryFlags()
    }

    fun setCurrentPos(ms: Long) = _state.update { it.copy(currentPosMs = ms) }
    fun setPlaying(playing: Boolean) = _state.update { it.copy(isPlaying = playing) }

    // ═══════════════════════════════════════════════════════════
    //  LAYER CREATION HELPERS
    // ═══════════════════════════════════════════════════════════
    private fun findOrCreateVisualTrack(preferredTrack: Int, startMs: Long, durMs: Long): Int {
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
        val trackIdx =
            findOrCreateVisualTrack(preferredTrack, clip.timelineStartMs, clip.durationMs)
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
    //  TRIM
    // ═══════════════════════════════════════════════════════════
    private fun syncLinkedTrim(primary: EditorClip) {
        val linkId = primary.linkedId ?: return
        val list = _state.value.clips.toMutableList()
        val li = list.indexOfFirst { it.linkedId == linkId && it.id != primary.id }
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
            val linked =
                _state.value.clips.firstOrNull { it.linkedId == sel.linkedId && it.id != sel.id }
            if (linked != null) {
                updateClipDirect(linked.id) { clip ->
                    clip.copy(
                        sourceStartMs = clampedSourceStart,
                        timelineStartMs = (clip.timelineStartMs + delta).coerceAtLeast(0L)
                    )
                }
            }
        }
    }

    fun trimClipRight(newSourceEndMs: Long) {
        val sel = _state.value.selectedClip ?: return
        val minEnd = sel.sourceStartMs + EditorClip.MIN_DURATION_MS
        val maxEnd = if (sel.sourceTotalMs != Long.MAX_VALUE) sel.sourceTotalMs else Long.MAX_VALUE
        val clampedEnd = newSourceEndMs.coerceIn(minEnd, maxEnd)
        if (clampedEnd == sel.sourceEndMs) return
        updateClipDirect(sel.id) { it.copy(sourceEndMs = clampedEnd) }
        if (sel.linkedId != null) {
            val linked =
                _state.value.clips.firstOrNull { it.linkedId == sel.linkedId && it.id != sel.id }
            if (linked != null) updateClipDirect(linked.id) { it.copy(sourceEndMs = clampedEnd) }
        }
    }

    fun trimLeft() {
        val sel = _state.value.selectedClip ?: return
        val playheadInSource = sel.sourceStartMs + (_state.value.currentPosMs * sel.speed).toLong()
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
        val playheadInSource = sel.sourceStartMs + (_state.value.currentPosMs * sel.speed).toLong()
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
        val second = sel.copy(id = "${sel.id}-B", sourceStartMs = splitMs, linkedId = null)
        val list = s.clips.toMutableList()
        val idx = list.indexOfFirst { it.id == sel.id }
        list.removeAt(idx); list.add(idx, second); list.add(idx, first)
        TimelineEngine.recalcTrackTimings(list, sel.trackIndex, sel.isAudio)
        _state.update { it.copy(clips = list, selectedClipId = first.id) }
        updateHistoryFlags()
    }

    fun commitTrim() = pushHistory()

    // ═══════════════════════════════════════════════════════════
    //  DELETE / DUPLICATE
    // ═══════════════════════════════════════════════════════════
    fun deleteCurrentClip() {
        val sel = _state.value.selectedClip ?: return
        pushHistory()
        val ids = DeleteEngine.collectForDeletion(sel, _state.value.clips)
        val newList = DeleteEngine.applyDeletion(_state.value.clips, ids)
        _state.update { it.copy(clips = newList, selectedClipId = null, currentPosMs = 0L) }
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

    // ═══════════════════════════════════════════════════════════
    //  FREEZE
    // ═══════════════════════════════════════════════════════════
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

    // ═══════════════════════════════════════════════════════════
    //  SPEED
    // ═══════════════════════════════════════════════════════════
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
        val startMs = s.currentPosMs
        val durMs = 3000L
        val baseTrack = s.selectedClip?.trackIndex ?: 0

        val clip = EditorClip(
            id = UUID.randomUUID().toString(),
            uri = Uri.EMPTY,
            name = "📝 ${initial.content.take(18).ifBlank { "Text" }}",
            type = "text/plain",
            sourceStartMs = 0L,
            sourceEndMs = durMs,
            timelineStartMs = startMs,
            trackIndex = 0,
            isAudio = false,
            textState = initial
        )
        pushHistory()
        addClipOnNewLayer(clip, baseTrack + 1)
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

    // ═══════════════════════════════════════════════════════════
    //  STICKER
    // ═══════════════════════════════════════════════════════════
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
        val startMs = s.currentPosMs
        val durMs = 3000L
        val baseTrack = s.selectedClip?.trackIndex ?: 0

        val clip = EditorClip(
            id = UUID.randomUUID().toString(),
            uri = Uri.EMPTY,
            name = emoji,
            type = "sticker/plain",
            sourceStartMs = 0L,
            sourceEndMs = durMs,
            timelineStartMs = startMs,
            trackIndex = 0,
            isAudio = false,
            stickerState = StickerState(emoji = emoji)
        )
        pushHistory()
        addClipOnNewLayer(clip, baseTrack + 1)
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

    // ═══════════════════════════════════════════════════════════
    //  EFFECT — creates a NEW LAYER with full EffectState
    // ═══════════════════════════════════════════════════════════
    fun applyEffectPreset(presetKey: String, presetLabel: String) {
        val preset = EffectLibrary.findByKey(presetKey) ?: return
        val s = _state.value
        val baseClip = s.selectedClip
        val startMs = baseClip?.timelineStartMs ?: s.currentPosMs
        val durMs = baseClip?.durationMs ?: 3000L
        val baseTrack = baseClip?.trackIndex ?: 0

        // Build full EffectState from preset (JS parity)
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

    fun clearAllEffects() { /* no-op */
    }

    // ═══════════════════════════════════════════════════════════
    //  ADJUSTMENT
    // ═══════════════════════════════════════════════════════════
    fun applyAdjustment(newAdj: AdjustmentData) {
        val s = _state.value
        val baseClip = s.selectedClip
        val startMs = baseClip?.timelineStartMs ?: s.currentPosMs
        val durMs = baseClip?.durationMs ?: 3000L
        val baseTrack = baseClip?.trackIndex ?: 0

        val clip = EditorClip(
            id = UUID.randomUUID().toString(),
            uri = Uri.EMPTY,
            name = "🎚️ Adjust",
            type = "adjustment/plain",
            sourceStartMs = 0L,
            sourceEndMs = durMs,
            timelineStartMs = startMs,
            trackIndex = 0,
            isAudio = false,
            adjustments = newAdj
        )
        pushHistory()
        addClipOnNewLayer(clip, baseTrack + 1)
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
    //  OVERLAY
    // ═══════════════════════════════════════════════════════════
    fun applyOverlay(newOverlay: OverlayState) {
        val s = _state.value
        val baseClip = s.selectedClip
        val startMs = baseClip?.timelineStartMs ?: s.currentPosMs
        val durMs = baseClip?.durationMs ?: 3000L
        val baseTrack = baseClip?.trackIndex ?: 0

        val clip = EditorClip(
            id = UUID.randomUUID().toString(),
            uri = Uri.EMPTY,
            name = "🎬 ${newOverlay.type}",
            type = "overlay/plain",
            sourceStartMs = 0L,
            sourceEndMs = durMs,
            timelineStartMs = startMs,
            trackIndex = 0,
            isAudio = false,
            overlay = newOverlay
        )
        pushHistory()
        addClipOnNewLayer(clip, baseTrack + 1)
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
    //  CHROMA
    // ═══════════════════════════════════════════════════════════
    fun applyChroma(newChroma: ChromaState) {
        val s = _state.value
        val baseClip = s.selectedClip
        val startMs = baseClip?.timelineStartMs ?: s.currentPosMs
        val durMs = baseClip?.durationMs ?: 3000L
        val baseTrack = baseClip?.trackIndex ?: 0

        val clip = EditorClip(
            id = UUID.randomUUID().toString(),
            uri = Uri.EMPTY,
            name = "🟢 Chroma",
            type = "chroma/plain",
            sourceStartMs = 0L,
            sourceEndMs = durMs,
            timelineStartMs = startMs,
            trackIndex = 0,
            isAudio = false,
            chroma = newChroma
        )
        pushHistory()
        addClipOnNewLayer(clip, baseTrack + 1)
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
    //  FILTERS / COLOR WHEEL
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
    //  TRANSITIONS / RATIO / BEATS / VOLUME / TRANSFORM
    // ═══════════════════════════════════════════════════════════
    fun updateTransition(state: TransitionState) {
        val sel = _state.value.selectedClip ?: return
        updateClipDirect(sel.id) { it.copy(transition = state) }
    }

    fun removeTransition() {
        val sel = _state.value.selectedClip ?: return
        updateClipDirect(sel.id) { it.copy(transition = null) }
    }

    fun updateRatio(state: RatioState) =
        _state.update { it.copy(aspectRatio = state.key, aspectMode = 0) }

    fun updateBeats(state: BeatsState) = _state.update {
        it.copy(
            beatsDetected = state.detected,
            beatsCount = state.count,
            beatsFilter = state.filter
        )
    }

    fun clearBeats() =
        _state.update { it.copy(beatsDetected = false, beatsCount = 0, beatsFilter = "all") }

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
        val currentTimeSec = (sel.timelineStartMs.let { start ->
            (_state.value.currentPosMs - start).toFloat() / 1000f
        }).coerceAtLeast(0f)

        // 1) Update base
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

        // 2) Auto-keyframe if active
        val newKf = KeyframeStore.autoKeyframeIfActive(
            updated.keyframes, prop, currentTimeSec, value
        )

        val finalClip = updated.copy(keyframes = newKf)
        updateClipDirect(sel.id) { finalClip }
    }

    fun toggleKeyframeAtPlayhead(prop: String) {
        val sel = _state.value.selectedClip ?: return
        val currentTimeSec = ((_state.value.currentPosMs - sel.timelineStartMs).toFloat() / 1000f)
            .coerceAtLeast(0f)

        val existing = KeyframeStore.hasKeyframeAt(sel.keyframes, prop, currentTimeSec)

        val updated = if (existing) {
            sel.copy(keyframes = KeyframeStore.removeKeyframe(sel.keyframes, prop, currentTimeSec))
        } else {
            // Get current value from base
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
                    sel.keyframes,
                    prop,
                    currentTimeSec,
                    currentValue
                )
            )
        }

        updateClipDirect(sel.id) { updated }
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

    fun setEaseAtPlayhead(ease: String) {
        val sel = _state.value.selectedClip ?: return
        val currentTimeSec = ((_state.value.currentPosMs - sel.timelineStartMs).toFloat() / 1000f)
            .coerceAtLeast(0f)
        val updated = sel.copy(
            keyframes = KeyframeStore.setAllEasesAtTime(sel.keyframes, currentTimeSec, ease)
        )
        updateClipDirect(sel.id) { updated }
    }

    // ═══════════════════════════════════════════════════════════
    //  UNDO / REDO
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