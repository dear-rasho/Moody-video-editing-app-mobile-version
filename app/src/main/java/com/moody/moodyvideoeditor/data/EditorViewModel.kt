package com.moody.moodyvideoeditor.data

import android.net.Uri
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Editor ka ViewModel
 * UI iske through state observe karega aur change karega
 */
class EditorViewModel : ViewModel() {

    private val _state = MutableStateFlow(EditorState())
    val state: StateFlow<EditorState> = _state.asStateFlow()

    // ═══════════════════════════════════════════════════════════
    //  CLIP OPERATIONS
    // ═══════════════════════════════════════════════════════════

    /**
     * Visual track mein clip add karein
     * trackIndex 0 = V1, 1 = V2, etc.
     */
    fun addVisualClip(trackIndex: Int, clip: Clip) {
        _state.update { current ->
            val newTimeline = current.timeline
            while (newTimeline.visualTracks.size <= trackIndex) {
                newTimeline.visualTracks.add(mutableListOf())
            }
            // Track pe clip add karein
            val track = newTimeline.visualTracks[trackIndex]

            // Agar clip ka startTimeMs 0 hai toh timeline ke end pe rakhein
            if (clip.startTimeMs == 0L) {
                val trackEnd = track.maxOfOrNull { it.endTimeMs } ?: 0L
                clip.startTimeMs = trackEnd
            }

            track.add(clip)
            track.sortBy { it.startTimeMs }
            current.copy(timeline = newTimeline)
        }
    }

    /**
     * Audio track mein clip add karein
     */
    fun addAudioClip(trackIndex: Int, clip: Clip) {
        _state.update { current ->
            val newTimeline = current.timeline
            while (newTimeline.audioTracks.size <= trackIndex) {
                newTimeline.audioTracks.add(mutableListOf())
            }
            val track = newTimeline.audioTracks[trackIndex]
            if (clip.startTimeMs == 0L) {
                val trackEnd = track.maxOfOrNull { it.endTimeMs } ?: 0L
                clip.startTimeMs = trackEnd
            }
            track.add(clip)
            track.sortBy { it.startTimeMs }
            current.copy(timeline = newTimeline)
        }
    }

    /**
     * Naya visual track add karein
     */
    fun addVisualTrack() {
        _state.update { current ->
            current.timeline.visualTracks.add(mutableListOf())
            current
        }
    }

    /**
     * Naya audio track add karein
     */
    fun addAudioTrack() {
        _state.update { current ->
            current.timeline.audioTracks.add(mutableListOf())
            current
        }
    }

    /**
     * Clip delete karein (id se)
     */
    fun deleteClip(clipId: String) {
        _state.update { current ->
            val timeline = current.timeline
            timeline.visualTracks.forEach { it.removeAll { clip -> clip.id == clipId } }
            timeline.audioTracks.forEach { it.removeAll { clip -> clip.id == clipId } }
            current.copy(
                timeline = timeline,
                selectedClipId = if (current.selectedClipId == clipId) null else current.selectedClipId
            )
        }
    }

    /**
     * Clip select karein
     */
    fun selectClip(clipId: String?) {
        _state.update { it.copy(selectedClipId = clipId) }
    }

    // ═══════════════════════════════════════════════════════════
    //  PLAYBACK
    // ═══════════════════════════════════════════════════════════

    fun setCurrentTime(timeMs: Long) {
        _state.update { it.copy(currentTimeMs = timeMs.coerceAtLeast(0)) }
    }

    fun setPlaying(playing: Boolean) {
        _state.update { it.copy(isPlaying = playing) }
    }

    // ═══════════════════════════════════════════════════════════
    //  TRACK VISIBILITY / MUTE
    // ═══════════════════════════════════════════════════════════

    fun toggleVisualTrackVisibility(trackIndex: Int) {
        _state.update { current ->
            val hidden = current.timeline.hiddenVisualTracks
            if (hidden.contains(trackIndex)) hidden.remove(trackIndex)
            else hidden.add(trackIndex)
            current
        }
    }

    fun toggleAudioTrackMute(trackIndex: Int) {
        _state.update { current ->
            val muted = current.timeline.mutedAudioTracks
            if (muted.contains(trackIndex)) muted.remove(trackIndex)
            else muted.add(trackIndex)
            current
        }
    }
}