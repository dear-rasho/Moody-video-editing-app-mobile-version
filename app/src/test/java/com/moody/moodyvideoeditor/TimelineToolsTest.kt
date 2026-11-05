package com.moody.moodyvideoeditor

import android.net.Uri
import com.moody.moodyvideoeditor.data.EditorClip
import com.moody.moodyvideoeditor.utils.TimelineTools
import org.junit.Assert.assertEquals
import org.junit.Test

class TimelineToolsTest {

    @Test
    fun audioSettingsUseFirstFreeTrackBelowSourceTracks() {
        val source = audioClip(id = "source", track = 0)
        val settingsOnNextTrack = audioSettingsClip(id = "settings", track = 1)

        val track = TimelineTools.findAudioSettingsTrack(
            clips = listOf(source, settingsOnNextTrack),
            audioLayerCount = 4,
            startMs = 0L,
            durationMs = 5_000L
        )

        assertEquals(2, track)
    }

    @Test
    fun audioSettingsNeverChooseEmptyTrackAboveHigherSourceTrack() {
        val source = audioClip(id = "source", track = 2)

        val track = TimelineTools.findAudioSettingsTrack(
            clips = listOf(source),
            audioLayerCount = 4,
            startMs = 0L,
            durationMs = 5_000L
        )

        assertEquals(3, track)
    }

    private fun audioClip(id: String, track: Int) = EditorClip(
        id = id,
        uri = Uri.EMPTY,
        name = "Audio",
        type = "audio/mpeg",
        sourceStartMs = 0L,
        sourceEndMs = 5_000L,
        timelineStartMs = 0L,
        trackIndex = track,
        isAudio = true
    )

    private fun audioSettingsClip(id: String, track: Int) = EditorClip(
        id = id,
        uri = Uri.EMPTY,
        name = "Audio Settings",
        type = "audiofx/plain",
        sourceStartMs = 0L,
        sourceEndMs = 5_000L,
        timelineStartMs = 0L,
        trackIndex = track,
        isAudio = true
    )
}
