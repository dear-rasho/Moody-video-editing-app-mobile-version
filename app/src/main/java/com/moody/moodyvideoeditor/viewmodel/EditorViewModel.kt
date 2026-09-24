package com.moody.moodyvideoeditor.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import com.moody.moodyvideoeditor.data.*
import com.moody.moodyvideoeditor.utils.HistoryManager
import com.moody.moodyvideoeditor.utils.SpeedEngine
import com.moody.moodyvideoeditor.utils.StickerEngine
import com.moody.moodyvideoeditor.utils.TimelineEngine
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
                selectedClipId = null,
                selectedTextId = null,
                selectedStickerId = null
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

    // ═══════════════════════════════════════════════════════════
    //  CLIPS
    // ═══════════════════════════════════════════════════════════
    fun addClipWithSource(uri: Uri, name: String, duration: Long, sourceTotalMs: Long) {
        pushHistory()
        val linkId = "lk-${System.currentTimeMillis()}"

        val videoClip = EditorClip(
            uri = uri, name = name,
            sourceStartMs = 0L, sourceEndMs = duration,
            timelineStartMs = 0L, trackIndex = 0, isAudio = false,
            sourceTotalMs = sourceTotalMs, linkedId = linkId
        )
        val audioClip = videoClip.copy(
            id = UUID.randomUUID().toString(),
            name = "$name (audio)",
            trackIndex = 0, isAudio = true
        )

        val list = _state.value.clips.toMutableList()
        list.add(videoClip)
        list.add(audioClip)

        _state.update {
            it.copy(
                clips = list,
                currentIndex = 0,
                currentPosMs = 0L,
                selectedClipId = videoClip.id,
                selectedTrackIndex = 0,
                selectedIsAudio = false,
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
                selectedTextId = null,
                selectedStickerId = null,
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
                clips = list,
                selectedClipId = clipId,
                selectedTrackIndex = newTrackIndex,
                selectedIsAudio = newIsAudio
            )
        }
        updateHistoryFlags()
    }

    fun setCurrentPos(ms: Long) = _state.update { it.copy(currentPosMs = ms) }
    fun setPlaying(playing: Boolean) = _state.update { it.copy(isPlaying = playing) }

    // ═══════════════════════════════════════════════════════════
    //  TRIM (mirrors js/features/trim.js)
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
        if (sel.isAudio) return
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
        if (sel.isAudio) return
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
        val second = sel.copy(
            id = "${sel.id}-B",
            sourceStartMs = splitMs,
            linkedId = null
        )
        val list = s.clips.toMutableList()
        val idx = list.indexOfFirst { it.id == sel.id }
        list.removeAt(idx)
        list.add(idx, second)
        list.add(idx, first)
        TimelineEngine.recalcTrackTimings(list, sel.trackIndex, sel.isAudio)
        _state.update { it.copy(clips = list, selectedClipId = first.id) }
        updateHistoryFlags()
    }

    fun commitTrim() = pushHistory()

    fun deleteCurrentClip() {
        val sel = _state.value.selectedClip ?: return
        pushHistory()
        _state.update { s ->
            val list = s.clips.toMutableList()
            list.removeAll { it.id == sel.id || (sel.linkedId != null && it.linkedId == sel.linkedId) }
            s.copy(clips = list, selectedClipId = null, currentPosMs = 0L)
        }
        updateHistoryFlags()
    }

    fun duplicateCurrentClip() {
        val sel = _state.value.selectedClip ?: return
        pushHistory()
        val copy = sel.copy(
            id = UUID.randomUUID().toString(),
            linkedId = null,
            timelineStartMs = sel.timelineEndMs
        )
        val list = _state.value.clips.toMutableList()
        list.add(copy)
        TimelineEngine.recalcTrackTimings(list, sel.trackIndex, sel.isAudio)
        _state.update { it.copy(clips = list) }
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
            primaryClip = updated.first { it.id == sel.id },
            speed = clamped,
            allClips = updated
        )
        _state.update { it.copy(clips = synced, speed = clamped) }
    }

    fun resetSpeed() {
        pushHistory()
        setSpeed(1.0f)
        updateHistoryFlags()
    }

    fun getSelectedBaseDurationMs(): Long {
        val sel = _state.value.selectedClip ?: return 0L
        return SpeedEngine.getBaseDuration(sel)
    }

    // ═══════════════════════════════════════════════════════════
    //  TEXT
    // ═══════════════════════════════════════════════════════════
    fun createTextClip() {
        val newClip = TextClip(
            state = TextState(content = "", positionX = 50f, positionY = 50f),
            startTimeMs = _state.value.currentPosMs,
            durationMs = 3000L
        )
        _state.update {
            it.copy(
                textClips = it.textClips + newClip,
                selectedTextId = newClip.id,
                selectedClipId = null,
                selectedStickerId = null
            )
        }
    }

    fun updateSelectedText(newState: TextState) {
        val selId = _state.value.selectedTextId
        if (selId == null) {
            if (newState.content.isNotBlank()) {
                val clip = TextClip(
                    state = newState,
                    startTimeMs = _state.value.currentPosMs,
                    durationMs = 3000L
                )
                _state.update {
                    it.copy(textClips = it.textClips + clip, selectedTextId = clip.id)
                }
            }
            return
        }
        _state.update {
            it.copy(
                textClips = it.textClips.map { tc ->
                    if (tc.id == selId) tc.copy(state = newState) else tc
                }
            )
        }
    }

    fun removeSelectedText() {
        val selId = _state.value.selectedTextId ?: return
        _state.update {
            it.copy(
                textClips = it.textClips.filter { tc -> tc.id != selId },
                selectedTextId = null
            )
        }
    }

    fun selectTextClip(id: String) {
        _state.update {
            it.copy(
                selectedTextId = id,
                selectedClipId = null,
                selectedStickerId = null
            )
        }
    }

    fun getSelectedTextState(): TextState =
        _state.value.selectedText?.state ?: TextState()

    fun setTextAnimation(animation: String) {
        val st = getSelectedTextState()
        updateSelectedText(st.copy(animation = animation))
    }

    // ═══════════════════════════════════════════════════════════
    //  STICKERS
    // ═══════════════════════════════════════════════════════════
    fun addOrUpdateSticker(emoji: String) {
        val selId = _state.value.selectedStickerId
        if (selId == null) {
            val clip = StickerClip(
                state = StickerState(emoji = emoji),
                startTimeMs = _state.value.currentPosMs,
                durationMs = 3000L
            )
            _state.update {
                it.copy(stickerClips = it.stickerClips + clip, selectedStickerId = clip.id)
            }
        } else {
            _state.update {
                it.copy(
                    stickerClips = it.stickerClips.map { sc ->
                        if (sc.id == selId) sc.copy(state = sc.state.copy(emoji = emoji)) else sc
                    }
                )
            }
        }
    }

    fun updateSelectedSticker(newState: StickerState) {
        val selId = _state.value.selectedStickerId ?: return
        _state.update {
            it.copy(
                stickerClips = it.stickerClips.map { sc ->
                    if (sc.id == selId) sc.copy(state = newState) else sc
                }
            )
        }
    }

    fun removeSelectedSticker() {
        val selId = _state.value.selectedStickerId ?: return
        _state.update {
            it.copy(
                stickerClips = it.stickerClips.filter { sc -> sc.id != selId },
                selectedStickerId = null
            )
        }
    }

    fun selectStickerClip(id: String) {
        _state.update {
            it.copy(
                selectedStickerId = id,
                selectedClipId = null,
                selectedTextId = null
            )
        }
    }

    fun getSelectedStickerState(): StickerState =
        _state.value.selectedSticker?.state ?: StickerState()

    fun addStickerKeyframe(
        type: String,   // "position" | "scale" | "rotation"
        timeSec: Float,
        x: Float = 0f, y: Float = 0f, value: Float = 0f
    ) {
        val selId = _state.value.selectedStickerId ?: return
        _state.update { s ->
            s.copy(
                stickerClips = s.stickerClips.map { sc ->
                    if (sc.id != selId) return@map sc
                    val kfs = sc.keyframes
                    val updated = when (type) {
                        "position" -> kfs.copy(
                            position = StickerEngine.addPositionKf(
                                kfs.position,
                                PositionKeyframe(timeSec, x, y)
                            )
                        )

                        "scale" -> kfs.copy(
                            scale = StickerEngine.addValueKf(
                                kfs.scale,
                                ValueKeyframe(timeSec, value)
                            )
                        )

                        "rotation" -> kfs.copy(
                            rotation = StickerEngine.addValueKf(
                                kfs.rotation,
                                ValueKeyframe(timeSec, value)
                            )
                        )

                        else -> kfs
                    }
                    sc.copy(keyframes = updated)
                }
            )
        }
    }

    fun clearStickerKeyframes() {
        val selId = _state.value.selectedStickerId ?: return
        _state.update {
            it.copy(
                stickerClips = it.stickerClips.map { sc ->
                    if (sc.id == selId) sc.copy(keyframes = StickerKeyframes()) else sc
                }
            )
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  FILTERS
    // ═══════════════════════════════════════════════════════════
    fun updateFilters(newFilters: FilterState) {
        val sel = _state.value.selectedClip ?: return
        updateClipDirect(sel.id) { it.copy(filters = newFilters) }
    }

    fun resetFilters() {
        val sel = _state.value.selectedClip ?: return
        updateClipDirect(sel.id) { it.copy(filters = FilterState()) }
    }

    // ═══════════════════════════════════════════════════════════
    //  EFFECTS — apply preset key list per clip
    // ═══════════════════════════════════════════════════════════
    fun applyEffectPreset(presetKey: String) {
        val sel = _state.value.selectedClip ?: return
        pushHistory()
        updateClipDirect(sel.id) { clip ->
            if (clip.effectKeys.contains(presetKey)) clip
            else clip.copy(effectKeys = clip.effectKeys + presetKey)
        }
        updateHistoryFlags()
    }

    fun removeEffectPreset(presetKey: String) {
        val sel = _state.value.selectedClip ?: return
        pushHistory()
        updateClipDirect(sel.id) { clip ->
            clip.copy(effectKeys = clip.effectKeys.filter { it != presetKey })
        }
        updateHistoryFlags()
    }

    fun clearAllEffects() {
        val sel = _state.value.selectedClip ?: return
        pushHistory()
        updateClipDirect(sel.id) { it.copy(effectKeys = emptyList()) }
        updateHistoryFlags()
    }

    // ═══════════════════════════════════════════════════════════
    //  ADJUSTMENTS
    // ═══════════════════════════════════════════════════════════
    fun updateSelectedAdjustments(newAdj: AdjustmentData) {
        val sel = _state.value.selectedClip ?: return
        updateClipDirect(sel.id) { it.copy(adjustments = newAdj) }
    }

    fun resetAdjustments() {
        val sel = _state.value.selectedClip ?: return
        updateClipDirect(sel.id) { it.copy(adjustments = AdjustmentData()) }
    }

    // ═══════════════════════════════════════════════════════════
    //  COLOR WHEEL
    // ═══════════════════════════════════════════════════════════
    fun updateColorWheel(newState: ColorWheelState) {
        val sel = _state.value.selectedClip ?: return
        updateClipDirect(sel.id) { it.copy(colorWheel = newState) }
    }

    fun resetColorWheel() {
        val sel = _state.value.selectedClip ?: return
        updateClipDirect(sel.id) { it.copy(colorWheel = ColorWheelState()) }
    }

    // ═══════════════════════════════════════════════════════════
    //  OVERLAYS
    // ═══════════════════════════════════════════════════════════
    fun updateOverlay(newState: OverlayState) {
        val sel = _state.value.selectedClip ?: return
        updateClipDirect(sel.id) { it.copy(overlay = newState) }
    }

    fun removeOverlay() {
        val sel = _state.value.selectedClip ?: return
        updateClipDirect(sel.id) { it.copy(overlay = OverlayState()) }
    }

    // ═══════════════════════════════════════════════════════════
    //  OLD API COMPAT — speed/volume for player
    // ═══════════════════════════════════════════════════════════
    fun setVolume(v: Float) = _state.update { it.copy(volume = v) }
    fun toggleMute() = _state.update { it.copy(isMuted = !it.isMuted) }
    fun setRotation(deg: Int) = _state.update { it.copy(rotation = deg) }
    fun setAspectMode(mode: Int) = _state.update { it.copy(aspectMode = mode) }
    fun setAspectRatio(ratio: String) = _state.update { it.copy(aspectRatio = ratio) }
    fun setAudioFx(fx: String) = _state.update { it.copy(audioFx = fx) }
    fun setSoundFx(fx: String) = _state.update { it.copy(soundFx = fx) }
    fun setChromaColor(c: Int) = _state.update { it.copy(chromaColor = c) }
    fun setChromaSimilarity(v: Float) = _state.update { it.copy(chromaSimilarity = v) }
    fun setChromaSmoothness(v: Float) = _state.update { it.copy(chromaSmoothness = v) }
    fun setChromaSpill(v: Float) = _state.update { it.copy(chromaSpill = v) }
    fun setChromaIntensity(v: Float) = _state.update { it.copy(chromaIntensity = v) }
    fun setBeats(count: Int, filter: String) = _state.update {
        it.copy(beatsDetected = count > 0, beatsCount = count, beatsFilter = filter)
    }

    fun clearBeats() = _state.update { it.copy(beatsDetected = false, beatsCount = 0) }

    // ═══════════════════════════════════════════════════════════
    //  TRANSFORM (uses selectedClip)
    // ═══════════════════════════════════════════════════════════
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