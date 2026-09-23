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

    private fun pushHistory() {
        history.push(_state.value.clips)
        updateHistoryFlags()
    }

    private fun updateHistoryFlags() {
        _state.update {
            it.copy(canUndo = history.canUndo(), canRedo = history.canRedo())
        }
    }

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

    fun setCurrentIndex(index: Int) {
        _state.update { it.copy(currentIndex = index, currentPosMs = 0L) }
    }

    fun setCurrentPos(ms: Long) {
        _state.update { it.copy(currentPosMs = ms) }
    }

    fun setPlaying(playing: Boolean) {
        _state.update { it.copy(isPlaying = playing) }
    }

    fun setSpeed(speed: Float) {
        val clip = _state.value.currentClip ?: return
        val newList = _state.value.clips.toMutableList()
        newList[_state.value.currentIndex] = clip.copy(speed = speed)
        var cursor = 0L
        for (i in newList.indices) {
            newList[i] = newList[i].copy(timelineStartMs = cursor)
            cursor += newList[i].durationMs
        }
        _state.update { it.copy(clips = newList, speed = speed) }
    }

    fun setVolume(v: Float) {
        _state.update { it.copy(volume = v) }
    }

    fun toggleMute() {
        _state.update { it.copy(isMuted = !it.isMuted) }
    }

    fun setRotation(deg: Int) {
        _state.update { it.copy(rotation = deg) }
    }

    fun setAspectMode(mode: Int) {
        _state.update { it.copy(aspectMode = mode) }
    }

    fun setAspectRatio(ratio: String) {
        _state.update { it.copy(aspectRatio = ratio) }
    }

    fun setText(text: String) {
        _state.update { it.copy(text = text) }
    }

    fun setTextColor(color: Int) {
        _state.update { it.copy(textColor = color) }
    }

    fun setTextSize(size: Int) {
        _state.update { it.copy(textSize = size) }
    }

    fun setEffect(effect: String) {
        _state.update { it.copy(effect = effect) }
    }

    fun setSticker(sticker: String) {
        _state.update { it.copy(sticker = sticker) }
    }

    fun setOverlay(overlay: String) {
        _state.update { it.copy(overlay = overlay) }
    }

    fun setBrightness(v: Float) {
        _state.update { it.copy(brightness = v) }
    }

    fun setContrast(v: Float) {
        _state.update { it.copy(contrast = v) }
    }

    fun setSaturation(v: Float) {
        _state.update { it.copy(saturation = v) }
    }

    fun resetAdjustments() {
        _state.update { it.copy(brightness = 1f, contrast = 1f, saturation = 1f) }
    }

    fun setClipScale(scale: Float) {
        val clip = _state.value.currentClip ?: return
        val newList = _state.value.clips.toMutableList()
        newList[_state.value.currentIndex] = clip.copy(scale = scale.coerceIn(0.1f, 3f))
        _state.update { it.copy(clips = newList) }
    }

    fun setClipRotation(rot: Float) {
        val clip = _state.value.currentClip ?: return
        val newList = _state.value.clips.toMutableList()
        newList[_state.value.currentIndex] = clip.copy(rotation = rot)
        _state.update { it.copy(clips = newList) }
    }

    fun setClipOffset(x: Float, y: Float) {
        val clip = _state.value.currentClip ?: return
        val newList = _state.value.clips.toMutableList()
        newList[_state.value.currentIndex] = clip.copy(offsetX = x, offsetY = y)
        _state.update { it.copy(clips = newList) }
    }

    fun setCrop(l: Float, r: Float, t: Float, b: Float) {
        val clip = _state.value.currentClip ?: return
        val newList = _state.value.clips.toMutableList()
        newList[_state.value.currentIndex] = clip.copy(
            cropL = l.coerceIn(0f, 0.45f),
            cropR = r.coerceIn(0f, 0.45f),
            cropT = t.coerceIn(0f, 0.45f),
            cropB = b.coerceIn(0f, 0.45f)
        )
        _state.update { it.copy(clips = newList) }
    }

    fun duplicateCurrentClip() {
        pushHistory()
        val clip = _state.value.currentClip ?: return
        val newClip = clip.copy(id = UUID.randomUUID().toString())
        val newList = _state.value.clips.toMutableList()
        newList.add(_state.value.currentIndex + 1, newClip)
        var cursor = 0L
        for (i in newList.indices) {
            newList[i] = newList[i].copy(timelineStartMs = cursor)
            cursor += newList[i].durationMs
        }
        _state.update { it.copy(clips = newList) }
        updateHistoryFlags()
    }

    fun splitCurrentClip() {
        pushHistory()
        _state.update { state ->
            val clip = state.currentClip ?: return@update state
            val splitSourceMs = clip.sourceStartMs + (state.currentPosMs * clip.speed).toLong()

            if (splitSourceMs <= clip.sourceStartMs + 300 ||
                splitSourceMs >= clip.sourceEndMs - 300
            ) return@update state

            val first = clip.copy(id = "${clip.id}-A", sourceEndMs = splitSourceMs)
            val second = clip.copy(id = "${clip.id}-B", sourceStartMs = splitSourceMs)

            val before = state.clips.subList(0, state.currentIndex)
            val after = state.clips.subList(state.currentIndex + 1, state.clips.size)
            val newList = (before + first + second + after).toMutableList()

            var cursor = 0L
            for (i in newList.indices) {
                newList[i] = newList[i].copy(timelineStartMs = cursor)
                cursor += newList[i].durationMs
            }

            state.copy(clips = newList, currentPosMs = 0L)
        }
        updateHistoryFlags()
    }

    fun trimLeft() {
        pushHistory()
        _state.update { state ->
            val clip = state.currentClip ?: return@update state
            val cutMs = (state.currentPosMs * clip.speed).toLong()
            val newStart = (clip.sourceStartMs + cutMs).coerceAtMost(clip.sourceEndMs - 300)
            val newList = state.clips.toMutableList()
            newList[state.currentIndex] = clip.copy(sourceStartMs = newStart)
            var cursor = 0L
            for (i in newList.indices) {
                newList[i] = newList[i].copy(timelineStartMs = cursor)
                cursor += newList[i].durationMs
            }
            state.copy(clips = newList, currentPosMs = 0L)
        }
        updateHistoryFlags()
    }

    fun trimRight() {
        pushHistory()
        _state.update { state ->
            val clip = state.currentClip ?: return@update state
            val cutMs = (state.currentPosMs * clip.speed).toLong()
            val newEnd = (clip.sourceStartMs + cutMs).coerceAtLeast(clip.sourceStartMs + 300)
            val newList = state.clips.toMutableList()
            newList[state.currentIndex] = clip.copy(sourceEndMs = newEnd)
            var cursor = 0L
            for (i in newList.indices) {
                newList[i] = newList[i].copy(timelineStartMs = cursor)
                cursor += newList[i].durationMs
            }
            state.copy(clips = newList, currentPosMs = 0L)
        }
        updateHistoryFlags()
    }

    fun deleteCurrentClip() {
        pushHistory()
        _state.update { state ->
            if (state.clips.isEmpty()) return@update state
            val newList = state.clips.toMutableList()
            newList.removeAt(state.currentIndex)
            var cursor = 0L
            for (i in newList.indices) {
                newList[i] = newList[i].copy(timelineStartMs = cursor)
                cursor += newList[i].durationMs
            }
            state.copy(
                clips = newList,
                currentIndex = state.currentIndex.coerceAtMost((newList.size - 1).coerceAtLeast(0)),
                currentPosMs = 0L
            )
        }
        updateHistoryFlags()
    }

    fun undo() {
        val prev = history.undo(_state.value.clips) ?: return
        _state.update { state ->
            state.copy(
                clips = prev,
                currentIndex = state.currentIndex.coerceAtMost((prev.size - 1).coerceAtLeast(0))
            )
        }
        updateHistoryFlags()
    }

    fun redo() {
        val next = history.redo(_state.value.clips) ?: return
        _state.update { state ->
            state.copy(
                clips = next,
                currentIndex = state.currentIndex.coerceAtMost((next.size - 1).coerceAtLeast(0))
            )
        }
        updateHistoryFlags()
    }
}