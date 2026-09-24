package com.moody.moodyvideoeditor.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
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

    // ═══ INTERNAL ═══
    private fun pushHistory() {
        history.push(_state.value.clips)
        updateHistoryFlags()
    }

    private fun updateHistoryFlags() {
        _state.update { it.copy(canUndo = history.canUndo(), canRedo = history.canRedo()) }
    }

    private fun recalcTimeline(list: MutableList<EditorClip>): MutableList<EditorClip> {
        var cursor = 0L
        for (i in list.indices) {
            list[i] = list[i].copy(timelineStartMs = cursor)
            cursor += list[i].durationMs
        }
        return list
    }

    // ═══ CLIPS ═══
    fun addClip(uri: Uri, name: String, duration: Long) {
        pushHistory()
        val clip = EditorClip(
            uri = uri,
            name = name,
            sourceStartMs = 0L,
            sourceEndMs = duration,
            timelineStartMs = 0L
        )
        _state.update { it.copy(clips = listOf(clip), currentIndex = 0, currentPosMs = 0L) }
    }

    fun setCurrentIndex(index: Int) =
        _state.update { it.copy(currentIndex = index, currentPosMs = 0L) }

    fun setCurrentPos(ms: Long) = _state.update { it.copy(currentPosMs = ms) }
    fun setPlaying(playing: Boolean) = _state.update { it.copy(isPlaying = playing) }

    // ═══ SPEED / VOLUME ═══
    fun setSpeed(speed: Float) {
        val clip = _state.value.currentClip ?: return
        val list = _state.value.clips.toMutableList()
        list[_state.value.currentIndex] = clip.copy(speed = speed)
        _state.update { it.copy(clips = recalcTimeline(list), speed = speed) }
    }

    fun setVolume(v: Float) = _state.update { it.copy(volume = v) }
    fun toggleMute() = _state.update { it.copy(isMuted = !it.isMuted) }

    // ═══ ROTATION / RATIO ═══
    fun setRotation(deg: Int) = _state.update { it.copy(rotation = deg) }
    fun setAspectMode(mode: Int) = _state.update { it.copy(aspectMode = mode) }
    fun setAspectRatio(ratio: String) = _state.update { it.copy(aspectRatio = ratio) }

    // ═══ TEXT ═══
    fun setText(t: String) = _state.update { it.copy(text = t) }
    fun setTextColor(c: Int) = _state.update { it.copy(textColor = c) }
    fun setTextSize(s: Int) = _state.update { it.copy(textSize = s) }
    fun setTextAnimation(a: String) = _state.update { it.copy(textAnimation = a) }
    fun setTextFont(f: String) = _state.update { it.copy(textFont = f) }

    // ═══ EFFECTS ═══
    fun setEffect(e: String) = _state.update { it.copy(effect = e) }
    fun setFilter(f: String) = _state.update { it.copy(filter = f) }
    fun setOverlay(o: String) = _state.update { it.copy(overlay = o) }
    fun setTransition(t: String) = _state.update { it.copy(transition = t) }
    fun setMotion(m: String) = _state.update { it.copy(motion = m) }

    // ═══ STICKER ═══
    fun setSticker(s: String) = _state.update { it.copy(sticker = s) }
    fun setStickerPosition(x: Float, y: Float) =
        _state.update { it.copy(stickerX = x, stickerY = y) }

    // ═══ ADJUSTMENTS ═══
    fun setBrightness(v: Float) = _state.update { it.copy(brightness = v) }
    fun setContrast(v: Float) = _state.update { it.copy(contrast = v) }
    fun setSaturation(v: Float) = _state.update { it.copy(saturation = v) }
    fun setExposure(v: Float) = _state.update { it.copy(exposure = v) }
    fun setTemperature(v: Float) = _state.update { it.copy(temperature = v) }
    fun setTint(v: Float) = _state.update { it.copy(tint = v) }
    fun setVignette(v: Float) = _state.update { it.copy(vignette = v) }
    fun setGrain(v: Float) = _state.update { it.copy(grain = v) }
    fun resetAdjustments() = _state.update {
        it.copy(
            brightness = 1f,
            contrast = 1f,
            saturation = 1f,
            exposure = 1f,
            temperature = 0f,
            tint = 0f,
            vignette = 0f,
            grain = 0f
        )
    }

    // ═══ COLOR WHEEL ═══
    fun setShadows(h: Float, s: Float) = _state.update { it.copy(shadowsHue = h, shadowsSat = s) }
    fun setMidtones(h: Float, s: Float) =
        _state.update { it.copy(midtonesHue = h, midtonesSat = s) }

    fun setHighlights(h: Float, s: Float) =
        _state.update { it.copy(highlightsHue = h, highlightsSat = s) }

    fun setHdrWhite(v: Float) = _state.update { it.copy(hdrWhite = v) }

    // ═══ CHROMA ═══
    fun setChromaColor(c: Int) = _state.update { it.copy(chromaColor = c) }
    fun setChromaSimilarity(v: Float) = _state.update { it.copy(chromaSimilarity = v) }
    fun setChromaSmoothness(v: Float) = _state.update { it.copy(chromaSmoothness = v) }
    fun setChromaSpill(v: Float) = _state.update { it.copy(chromaSpill = v) }
    fun setChromaIntensity(v: Float) = _state.update { it.copy(chromaIntensity = v) }

    // ═══ AUDIO FX / SOUND FX ═══
    fun setAudioFx(fx: String) = _state.update { it.copy(audioFx = fx) }
    fun setSoundFx(fx: String) = _state.update { it.copy(soundFx = fx) }

    // ═══ BEATS ═══
    fun setBeats(count: Int, filter: String) = _state.update {
        it.copy(
            beatsDetected = count > 0,
            beatsCount = count,
            beatsFilter = filter
        )
    }

    fun clearBeats() = _state.update { it.copy(beatsDetected = false, beatsCount = 0) }

    // ═══ TRANSFORM ═══
    fun setClipScale(v: Float) {
        val clip = _state.value.currentClip ?: return
        val list = _state.value.clips.toMutableList()
        list[_state.value.currentIndex] = clip.copy(scale = v.coerceIn(0.1f, 3f))
        _state.update { it.copy(clips = list) }
    }

    fun setClipRotation(v: Float) {
        val clip = _state.value.currentClip ?: return
        val list = _state.value.clips.toMutableList()
        list[_state.value.currentIndex] = clip.copy(rotation = v)
        _state.update { it.copy(clips = list) }
    }

    fun setClipOffset(x: Float, y: Float) {
        val clip = _state.value.currentClip ?: return
        val list = _state.value.clips.toMutableList()
        list[_state.value.currentIndex] = clip.copy(offsetX = x, offsetY = y)
        _state.update { it.copy(clips = list) }
    }

    // ═══ CROP ═══
    fun setCrop(l: Float, r: Float, t: Float, b: Float) {
        val clip = _state.value.currentClip ?: return
        val list = _state.value.clips.toMutableList()
        list[_state.value.currentIndex] = clip.copy(
            cropL = l.coerceIn(0f, 0.45f), cropR = r.coerceIn(0f, 0.45f),
            cropT = t.coerceIn(0f, 0.45f), cropB = b.coerceIn(0f, 0.45f)
        )
        _state.update { it.copy(clips = list) }
    }

    // ═══ DUPLICATE / SPLIT / TRIM / DELETE ═══
    fun duplicateCurrentClip() {
        pushHistory()
        val clip = _state.value.currentClip ?: return
        val list = _state.value.clips.toMutableList()
        list.add(_state.value.currentIndex + 1, clip.copy(id = UUID.randomUUID().toString()))
        _state.update { it.copy(clips = recalcTimeline(list)) }
        updateHistoryFlags()
    }

    fun splitCurrentClip() {
        pushHistory()
        _state.update { state ->
            val clip = state.currentClip ?: return@update state
            val splitMs = clip.sourceStartMs + (state.currentPosMs * clip.speed).toLong()
            if (splitMs <= clip.sourceStartMs + 300 || splitMs >= clip.sourceEndMs - 300) return@update state
            val first = clip.copy(id = "${clip.id}-A", sourceEndMs = splitMs)
            val second = clip.copy(id = "${clip.id}-B", sourceStartMs = splitMs)
            val list =
                (state.clips.subList(0, state.currentIndex) + first + second + state.clips.subList(
                    state.currentIndex + 1,
                    state.clips.size
                )).toMutableList()
            state.copy(clips = recalcTimeline(list), currentPosMs = 0L)
        }
        updateHistoryFlags()
    }

    fun trimLeft() {
        pushHistory()
        _state.update { state ->
            val clip = state.currentClip ?: return@update state
            val cutMs = (state.currentPosMs * clip.speed).toLong()
            val newStart = (clip.sourceStartMs + cutMs).coerceAtMost(clip.sourceEndMs - 300)
            val list = state.clips.toMutableList()
            list[state.currentIndex] = clip.copy(sourceStartMs = newStart)
            state.copy(clips = recalcTimeline(list), currentPosMs = 0L)
        }
        updateHistoryFlags()
    }

    fun trimRight() {
        pushHistory()
        _state.update { state ->
            val clip = state.currentClip ?: return@update state
            val cutMs = (state.currentPosMs * clip.speed).toLong()
            val newEnd = (clip.sourceStartMs + cutMs).coerceAtLeast(clip.sourceStartMs + 300)
            val list = state.clips.toMutableList()
            list[state.currentIndex] = clip.copy(sourceEndMs = newEnd)
            state.copy(clips = recalcTimeline(list), currentPosMs = 0L)
        }
        updateHistoryFlags()
    }

    fun deleteCurrentClip() {
        pushHistory()
        _state.update { state ->
            if (state.clips.isEmpty()) return@update state
            val list = state.clips.toMutableList()
            list.removeAt(state.currentIndex)
            state.copy(
                clips = recalcTimeline(list),
                currentIndex = state.currentIndex.coerceAtMost((list.size - 1).coerceAtLeast(0)),
                currentPosMs = 0L
            )
        }
        updateHistoryFlags()
    }

    // ═══ UNDO / REDO ═══
    fun undo() {
        val prev = history.undo(_state.value.clips) ?: return
        _state.update {
            it.copy(
                clips = prev,
                currentIndex = it.currentIndex.coerceAtMost((prev.size - 1).coerceAtLeast(0))
            )
        }
        updateHistoryFlags()
    }

    fun redo() {
        val next = history.redo(_state.value.clips) ?: return
        _state.update {
            it.copy(
                clips = next,
                currentIndex = it.currentIndex.coerceAtMost((next.size - 1).coerceAtLeast(0))
            )
        }
        updateHistoryFlags()
    }
}