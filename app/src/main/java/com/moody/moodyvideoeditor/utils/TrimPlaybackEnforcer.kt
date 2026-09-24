package com.moody.moodyvideoeditor.utils

import androidx.media3.exoplayer.ExoPlayer
import com.moody.moodyvideoeditor.data.EditorClip

/**
 * Mirrors js/workspace/trimPlayback.js
 * - Past sourceEnd → pause at out point
 * - Before sourceStart → jump forward
 * - Free seek allowed (playhead scrub)
 */
object TrimPlaybackEnforcer {

    private const val GUARD_MS = 50L

    /**
     * Call every playback tick. Returns true if playback was paused.
     */
    fun enforce(player: ExoPlayer, clip: EditorClip?): Boolean {
        if (clip == null) return false
        if (clip.isAudio) return false
        if (!player.isPlaying) return false

        val sourceIn = clip.sourceStartMs
        val sourceOut = clip.sourceEndMs
        val current = player.currentPosition

        if (current >= sourceOut - GUARD_MS) {
            player.pause()
            player.seekTo(sourceOut)
            return true
        }

        if (current < sourceIn - GUARD_MS) {
            player.seekTo(sourceIn)
        }
        return false
    }

    /**
     * Called on PLAY press — prepare start position.
     * Returns false if clip is already at end (don't play).
     */
    fun prepareOnPlay(player: ExoPlayer, clip: EditorClip?): Boolean {
        if (clip == null) return true
        if (clip.isAudio) return true

        val sourceIn = clip.sourceStartMs
        val sourceOut = clip.sourceEndMs
        val current = player.currentPosition

        if (current >= sourceOut - GUARD_MS) {
            player.seekTo(sourceOut)
            return false
        }

        if (current < sourceIn - GUARD_MS) {
            player.seekTo(sourceIn)
        }
        return true
    }
}