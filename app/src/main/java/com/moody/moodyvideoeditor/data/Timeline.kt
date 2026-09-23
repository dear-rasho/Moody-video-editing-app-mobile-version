package com.moody.moodyvideoeditor.data

import java.util.UUID

/**
 * Ek clip jo timeline pe hoti hai.
 * Type field batata hai kya hai: "video/", "image/", "audio/", "text/", "sticker/", "effect/"
 */
data class Clip(
    var id: String = UUID.randomUUID().toString(),
    var uri: String = "",                    // content:// ya file:// ya custom://
    var type: String = "video/",             // category
    var name: String = "Untitled",
    var startTimeMs: Long = 0,               // timeline pe kahan shuru hoti hai
    var durationMs: Long = 0,                // visible duration (trim ke baad)
    var sourceInMs: Long = 0,                // source mein kahan se shuru (trim in-point)
    var sourceTotalMs: Long = 0,             // source ki poori duration
    var playbackSpeed: Float = 1.0f,
    var trackLabel: String = ""              // "V1", "A1", etc (optional tracking)
) {
    /** Trim ke baad visible duration */
    val visibleDurationMs: Long
        get() = (durationMs).coerceAtLeast(0L)

    /** Timeline pe end time */
    val endTimeMs: Long
        get() = startTimeMs + durationMs

    /** Kya yeh clip specific time pe active hai? */
    fun isActiveAt(timeMs: Long): Boolean {
        return timeMs >= startTimeMs && timeMs < endTimeMs
    }
}

/**
 * Timeline = multiple tracks
 * Visual tracks (V1, V2, ...) aur Audio tracks (A1, A2, ...)
 * Har track ek list of clips hai
 */
data class Timeline(
    val visualTracks: MutableList<MutableList<Clip>> = mutableListOf(
        mutableListOf(),  // V1
        mutableListOf(),  // V2
        mutableListOf()   // V3
    ),
    val audioTracks: MutableList<MutableList<Clip>> = mutableListOf(
        mutableListOf(),  // A1
        mutableListOf()   // A2
    ),
    val hiddenVisualTracks: MutableSet<Int> = mutableSetOf(),
    val mutedAudioTracks: MutableSet<Int> = mutableSetOf()
) {
    /** Timeline ki total duration (sabse aakhri clip ka end) */
    fun totalDurationMs(): Long {
        var maxEnd = 0L
        val allTracks = visualTracks + audioTracks
        for (track in allTracks) {
            for (clip in track) {
                if (clip.endTimeMs > maxEnd) maxEnd = clip.endTimeMs
            }
        }
        return maxEnd
    }

    /** Specific time pe top-most active visual clip */
    fun topVisualClipAt(timeMs: Long): Clip? {
        for (trackIdx in visualTracks.indices.reversed()) {
            if (hiddenVisualTracks.contains(trackIdx)) continue
            val track = visualTracks[trackIdx]
            for (clip in track) {
                if (clip.isActiveAt(timeMs) &&
                    (clip.type.startsWith("video/") || clip.type.startsWith("image/"))) {
                    return clip
                }
            }
        }
        return null
    }

    /** Specific time pe active audio clip */
    fun activeAudioClipAt(timeMs: Long): Clip? {
        for (trackIdx in audioTracks.indices) {
            if (mutedAudioTracks.contains(trackIdx)) continue
            val track = audioTracks[trackIdx]
            for (clip in track) {
                if (clip.isActiveAt(timeMs)) return clip
            }
        }
        return null
    }
}