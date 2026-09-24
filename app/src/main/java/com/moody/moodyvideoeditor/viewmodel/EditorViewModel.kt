package com.moody.moodyvideoeditor.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import com.moody.moodyvideoeditor.data.AdjustmentData
import com.moody.moodyvideoeditor.data.EditorClip
import com.moody.moodyvideoeditor.data.EditorState
import com.moody.moodyvideoeditor.utils.HistoryManager
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
    //  INTERNAL HELPERS
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

    private fun recalcTimelineForTrack(
        list: MutableList<EditorClip>,
        trackIndex: Int,
        isAudio: Boolean
    ): MutableList<EditorClip> {
        val trackClips = list.filter { it.trackIndex == trackIndex && it.isAudio == isAudio }
            .sortedBy { it.timelineStartMs }
        var cursor = 0L
        trackClips.forEach { clip ->
            val idx = list.indexOfFirst { it.id == clip.id }
            if (idx >= 0) {
                list[idx] = list[idx].copy(timelineStartMs = cursor)
                cursor += list[idx].durationMs
            }
        }
        return list
    }

    // ═══════════════════════════════════════════════════════════
    //  LAYERS
    // ═══════════════════════════════════════════════════════════
    fun addVisualLayer() {
        _state.update { it.copy(visualLayerCount = it.visualLayerCount + 1) }
    }

    fun addAudioLayer() {
        _state.update { it.copy(audioLayerCount = it.audioLayerCount + 1) }
    }

    // ═══════════════════════════════════════════════════════════
    //  SELECTION
    // ═══════════════════════════════════════════════════════════
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

    fun clearSelection() {
        _state.update { it.copy(selectedClipId = null) }
    }

    // ═══════════════════════════════════════════════════════════
    //  CLIPS
    // ═══════════════════════════════════════════════════════════
    fun addClip(uri: Uri, name: String, duration: Long) {
        pushHistory()
        val clip = EditorClip(
            uri = uri,
            name = name,
            sourceStartMs = 0L,
            sourceEndMs = duration,
            timelineStartMs = 0L,
            trackIndex = 0,
            isAudio = false
        )
        _state.update {
            it.copy(
                clips = listOf(clip),
                currentIndex = 0,
                currentPosMs = 0L,
                selectedClipId = clip.id,
                selectedTrackIndex = 0,
                selectedIsAudio = false
            )
        }
    }

    fun setCurrentPos(ms: Long) = _state.update { it.copy(currentPosMs = ms) }
    fun setPlaying(playing: Boolean) = _state.update { it.copy(isPlaying = playing) }

    // ═══════════════════════════════════════════════════════════
    //  SELECTED CLIP HELPER
    // ═══════════════════════════════════════════════════════════
    private fun updateSelectedClip(transform: (EditorClip) -> EditorClip) {
        val sel = _state.value.selectedClip ?: return
        val list = _state.value.clips.toMutableList()
        val idx = list.indexOfFirst { it.id == sel.id }
        if (idx < 0) return
        list[idx] = transform(list[idx])
        _state.update { it.copy(clips = list) }
    }

    // ═══════════════════════════════════════════════════════════
    //  ADJUSTMENTS — 24 sliders
    //  Har slider alag function hai
    // ═══════════════════════════════════════════════════════════
    private fun updateAdjustment(transform: (AdjustmentData) -> AdjustmentData) {
        updateSelectedClip { clip ->
            clip.copy(adjustments = transform(clip.adjustments))
        }
    }

    // Main entry point for AdjustmentsPanel
    fun updateSelectedAdjustments(newAdj: AdjustmentData) {
        updateSelectedClip { it.copy(adjustments = newAdj) }
    }

    // ─── LIGHT (7) ───────────────────────────────────────
    fun setBrightness(v: Float) = updateAdjustment { it.copy(brightness = v) }
    fun setContrast(v: Float) = updateAdjustment { it.copy(contrast = v) }
    fun setExposure(v: Float) = updateAdjustment { it.copy(exposure = v) }
    fun setWhites(v: Float) = updateAdjustment { it.copy(whites = v) }
    fun setBlacks(v: Float) = updateAdjustment { it.copy(blacks = v) }
    fun setShadows(v: Float) = updateAdjustment { it.copy(shadows = v) }
    fun setHighlights(v: Float) = updateAdjustment { it.copy(highlights = v) }

    // ─── COLOR (3) ───────────────────────────────────────
    fun setSaturation(v: Float) = updateAdjustment { it.copy(saturation = v) }
    fun setVibrance(v: Float) = updateAdjustment { it.copy(vibrance = v) }
    fun setClarity(v: Float) = updateAdjustment { it.copy(clarity = v) }

    // ─── TEMPERATURE (2) ─────────────────────────────────
    fun setTemperature(v: Float) = updateAdjustment { it.copy(temperature = v) }
    fun setTint(v: Float) = updateAdjustment { it.copy(tint = v) }

    // ─── DETAILS (3) ─────────────────────────────────────
    fun setNoise(v: Float) = updateAdjustment { it.copy(noise = v) }
    fun setSharpen(v: Float) = updateAdjustment { it.copy(sharpen = v) }
    fun setVignette(v: Float) = updateAdjustment { it.copy(vignette = v) }

    // ─── COLOR CHANNELS (9) ─────────────────────────────
    fun setReds(v: Float) = updateAdjustment { it.copy(reds = v) }
    fun setOranges(v: Float) = updateAdjustment { it.copy(oranges = v) }
    fun setYellows(v: Float) = updateAdjustment { it.copy(yellows = v) }
    fun setGreens(v: Float) = updateAdjustment { it.copy(greens = v) }
    fun setCyans(v: Float) = updateAdjustment { it.copy(cyans = v) }
    fun setBlues(v: Float) = updateAdjustment { it.copy(blues = v) }
    fun setPurples(v: Float) = updateAdjustment { it.copy(purples = v) }
    fun setMagentas(v: Float) = updateAdjustment { it.copy(magentas = v) }
    fun setSkinTones(v: Float) = updateAdjustment { it.copy(skinTones = v) }

    // ─── RESET ───────────────────────────────────────────
    fun resetAdjustments() {
        updateSelectedAdjustments(AdjustmentData())
    }

    // ═══════════════════════════════════════════════════════════
    //  SPEED / VOLUME / ROTATION / RATIO
    // ═══════════════════════════════════════════════════════════
    fun setSpeed(speed: Float) {
        updateSelectedClip { it.copy(speed = speed) }
        _state.update { it.copy(speed = speed) }
    }

    fun setVolume(v: Float) = _state.update { it.copy(volume = v) }
    fun toggleMute() = _state.update { it.copy(isMuted = !it.isMuted) }
    fun setRotation(deg: Int) = _state.update { it.copy(rotation = deg) }
    fun setAspectMode(mode: Int) = _state.update { it.copy(aspectMode = mode) }
    fun setAspectRatio(ratio: String) = _state.update { it.copy(aspectRatio = ratio) }

    // ═══════════════════════════════════════════════════════════
    //  TEXT
    // ═══════════════════════════════════════════════════════════
    fun setText(t: String) = _state.update { it.copy(text = t) }
    fun setTextColor(c: Int) = _state.update { it.copy(textColor = c) }
    fun setTextSize(s: Int) = _state.update { it.copy(textSize = s) }
    fun setTextAnimation(a: String) = _state.update { it.copy(textAnimation = a) }
    fun setTextFont(f: String) = _state.update { it.copy(textFont = f) }

    // ═══════════════════════════════════════════════════════════
    //  EFFECTS / FILTERS / OVERLAYS / TRANSITIONS / MOTION
    // ═══════════════════════════════════════════════════════════
    fun setEffect(e: String) = _state.update { it.copy(effect = e) }
    fun setFilter(f: String) = _state.update { it.copy(filter = f) }
    fun setOverlay(o: String) = _state.update { it.copy(overlay = o) }
    fun setTransition(t: String) = _state.update { it.copy(transition = t) }
    fun setMotion(m: String) = _state.update { it.copy(motion = m) }

    // ═══════════════════════════════════════════════════════════
    //  STICKER
    // ═══════════════════════════════════════════════════════════
    fun setSticker(s: String) = _state.update { it.copy(sticker = s) }
    fun setStickerPosition(x: Float, y: Float) = _state.update { it.copy(stickerX = x, stickerY = y) }

    // ═══════════════════════════════════════════════════════════
    //  COLOR WHEEL — Different names to avoid conflict!
    //  (setShadows/setHighlights adjustments ke liye use ho rahe hain)
    // ═══════════════════════════════════════════════════════════
    fun setShadowsHue(h: Float, s: Float) = _state.update { it.copy(shadowsHue = h, shadowsSat = s) }
    fun setMidtonesHue(h: Float, s: Float) = _state.update { it.copy(midtonesHue = h, midtonesSat = s) }
    fun setHighlightsHue(h: Float, s: Float) = _state.update { it.copy(highlightsHue = h, highlightsSat = s) }
    fun setHdrWhite(v: Float) = _state.update { it.copy(hdrWhite = v) }

    // ═══════════════════════════════════════════════════════════
    //  CHROMA KEY
    // ═══════════════════════════════════════════════════════════
    fun setChromaColor(c: Int) = _state.update { it.copy(chromaColor = c) }
    fun setChromaSimilarity(v: Float) = _state.update { it.copy(chromaSimilarity = v) }
    fun setChromaSmoothness(v: Float) = _state.update { it.copy(chromaSmoothness = v) }
    fun setChromaSpill(v: Float) = _state.update { it.copy(chromaSpill = v) }
    fun setChromaIntensity(v: Float) = _state.update { it.copy(chromaIntensity = v) }

    // ═══════════════════════════════════════════════════════════
    //  AUDIO FX / SOUND FX
    // ═══════════════════════════════════════════════════════════
    fun setAudioFx(fx: String) = _state.update { it.copy(audioFx = fx) }
    fun setSoundFx(fx: String) = _state.update { it.copy(soundFx = fx) }

    // ═══════════════════════════════════════════════════════════
    //  BEATS
    // ═══════════════════════════════════════════════════════════
    fun setBeats(count: Int, filter: String) = _state.update {
        it.copy(beatsDetected = count > 0, beatsCount = count, beatsFilter = filter)
    }
    fun clearBeats() = _state.update { it.copy(beatsDetected = false, beatsCount = 0) }

    // ═══════════════════════════════════════════════════════════
    //  TRANSFORM
    // ═══════════════════════════════════════════════════════════
    fun setClipScale(v: Float) = updateSelectedClip { it.copy(scale = v.coerceIn(0.1f, 3f)) }
    fun setClipRotation(v: Float) = updateSelectedClip { it.copy(rotation = v) }
    fun setClipOffset(x: Float, y: Float) = updateSelectedClip { it.copy(offsetX = x, offsetY = y) }

    // ═══════════════════════════════════════════════════════════
    //  CROP
    // ═══════════════════════════════════════════════════════════
    fun setCrop(l: Float, r: Float, t: Float, b: Float) = updateSelectedClip {
        it.copy(
            cropL = l.coerceIn(0f, 0.45f),
            cropR = r.coerceIn(0f, 0.45f),
            cropT = t.coerceIn(0f, 0.45f),
            cropB = b.coerceIn(0f, 0.45f)
        )
    }

    // ═══════════════════════════════════════════════════════════
    //  DUPLICATE / SPLIT / TRIM / DELETE
    // ═══════════════════════════════════════════════════════════
    fun duplicateCurrentClip() {
        val clip = _state.value.selectedClip ?: return
        pushHistory()
        val copy = clip.copy(id = UUID.randomUUID().toString(), timelineStartMs = clip.timelineEndMs)
        _state.update { it.copy(clips = it.clips + copy) }
        updateHistoryFlags()
    }

    fun splitCurrentClip() {
        val sel = _state.value.selectedClip ?: return
        pushHistory()
        _state.update { state ->
            val splitMs = sel.sourceStartMs + (state.currentPosMs * sel.speed).toLong()
            if (splitMs <= sel.sourceStartMs + 300 || splitMs >= sel.sourceEndMs - 300) {
                return@update state
            }
            val first = sel.copy(id = "${sel.id}-A", sourceEndMs = splitMs)
            val second = sel.copy(id = "${sel.id}-B", sourceStartMs = splitMs)
            val list = state.clips.toMutableList()
            val idx = list.indexOfFirst { it.id == sel.id }
            list.removeAt(idx)
            list.add(idx, second)
            list.add(idx, first)
            state.copy(clips = recalcTimelineForTrack(list, sel.trackIndex, sel.isAudio))
        }
        updateHistoryFlags()
    }

    fun trimLeft() {
        val sel = _state.value.selectedClip ?: return
        pushHistory()
        val cutMs = (_state.value.currentPosMs * sel.speed).toLong()
        val newStart = (sel.sourceStartMs + cutMs).coerceAtMost(sel.sourceEndMs - 300)
        updateSelectedClip { it.copy(sourceStartMs = newStart) }
        updateHistoryFlags()
    }

    fun trimRight() {
        val sel = _state.value.selectedClip ?: return
        pushHistory()
        val cutMs = (_state.value.currentPosMs * sel.speed).toLong()
        val newEnd = (sel.sourceStartMs + cutMs).coerceAtLeast(sel.sourceStartMs + 300)
        updateSelectedClip { it.copy(sourceEndMs = newEnd) }
        updateHistoryFlags()
    }

    fun deleteCurrentClip() {
        val sel = _state.value.selectedClip ?: return
        pushHistory()
        _state.update { state ->
            val list = state.clips.toMutableList()
            list.removeAll { it.id == sel.id }
            state.copy(clips = list, selectedClipId = null, currentPosMs = 0L)
        }
        updateHistoryFlags()
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