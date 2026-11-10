package com.moody.moodyvideoeditor

import android.net.Uri
import com.moody.moodyvideoeditor.data.AudioAdjustmentSettings
import com.moody.moodyvideoeditor.data.EditorClip
import com.moody.moodyvideoeditor.utils.AudioEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioEngineTest {

    @Test
    fun echoFiltersKeepGainsWithinFfmpegRangeAtMaximumIntensity() {
        listOf("echo", "reverb", "cave", "stadium").forEach { effect ->
            val filter = AudioEngine.buildAudioFilter(effect, 200f)
            val parameters = filter.removePrefix("aecho=").split(':')
            val inputGain = parameters[0].toFloat()
            val outputGain = parameters[1].toFloat()

            assertTrue("$effect input gain out of range: $filter", inputGain in 0f..1f)
            assertTrue("$effect output gain out of range: $filter", outputGain in 0f..1f)
        }
    }

    @Test
    fun invalidIntensityDoesNotProduceNanOrInfinity() {
        listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY).forEach {
            val filter = AudioEngine.buildAudioFilter("echo", it)
            assertFalse(filter.contains("NaN"))
            assertFalse(filter.contains("Infinity"))
        }
    }

    @Test
    fun noiseReductionPresetHasSoftwareExportFilter() {
        assertTrue(AudioEngine.AUDIO_FX.any { it.key == "noise_reduction" })
        val filter = AudioEngine.buildAudioFilter("noise_reduction", 100f)
        assertTrue(filter.startsWith("highpass=f=70,afftdn=nr="))
        assertTrue(filter.contains(":tn=1:"))
        assertEquals("", AudioEngine.buildAudioFilter("none", 100f))
    }

    @Test
    fun everyAudioPresetHasAFilter() {
        AudioEngine.AUDIO_FX.filter { it.key != "none" }.forEach { preset ->
            assertTrue(
                "${preset.key} should map to an FFmpeg audio filter",
                AudioEngine.buildAudioFilter(preset.key, 100f).isNotBlank()
            )
        }
    }

    @Test
    fun everySoundPresetBuildsAnOfflineAudioSource() {
        AudioEngine.SOUND_FX.filter { it.key != "none" }.forEach { preset ->
            val source = AudioEngine.buildSoundFxSource(preset.key, 100f, 3.0)
            assertTrue("${preset.key} should create a source", source?.startsWith("aevalsrc=") == true)
            assertEquals("", AudioEngine.buildAudioFilter(preset.key, 100f))
        }
    }

    @Test
    fun audioSettingsReverbUsesMultipleAudibleTaps() {
        val filters = AudioEngine.audioAdjustmentFilters(
            AudioAdjustmentSettings(reverb = 100f, studioReverb = 100f)
        )

        assertTrue(filters.any { it.contains("aecho=0.8000:0.55:37|71|113|173|251|347|461:") })
        assertTrue(filters.any { it.contains("aecho=0.8000:0.5:19|37|58|83|111:") })
    }

    @Test
    fun audioSettingsSlidersProduceExportFilters() {
        val filters = AudioEngine.audioAdjustmentFilters(
            AudioAdjustmentSettings(
                volume = 40f,
                bass = 60f,
                pitch = 65f,
                loudness = 50f,
                vocalEnhancement = 70f
            )
        )

        assertTrue(filters.any { it == "volume=0.4000" })
        assertTrue(filters.any { it.startsWith("equalizer=f=100:") })
        assertTrue(filters.any { it.startsWith("asetrate=") })
        assertTrue(filters.any { it.startsWith("dynaudnorm=") })
        assertTrue(filters.any { it.startsWith("equalizer=f=2800:") })
        assertFalse(filters.any { it.startsWith("acompressor=") })
    }

    @Test
    fun audioSettingsNoiseReductionProducesDenoiseFilter() {
        val filters = AudioEngine.audioAdjustmentFilters(
            AudioAdjustmentSettings(noiseReduction = 80f)
        )

        assertTrue(filters.any { it == "afftdn=nr=24.0000:nf=-35:tn=1:gs=5" })
    }

    @Test
    fun audioSettingsLayerProcessesLinkedVideoAudioAndKeepsReverbTail() {
        val linkedAudio = EditorClip(
            uri = Uri.EMPTY,
            name = "Video audio",
            type = "audio/mpeg",
            sourceStartMs = 0L,
            sourceEndMs = 4_000L,
            timelineStartMs = 0L,
            trackIndex = 0,
            isAudio = true,
            linkedId = "video-audio-link"
        )
        val settingsLayer = EditorClip(
            uri = Uri.EMPTY,
            name = "Audio Settings",
            type = "audiofx/plain",
            sourceStartMs = 0L,
            sourceEndMs = 4_000L,
            timelineStartMs = 0L,
            trackIndex = 2,
            isAudio = true,
            audioAdjustments = AudioAdjustmentSettings(reverb = 100f)
        )
        val filters = mutableListOf<String>()

        val output = AudioEngine.appendTimelineAudioAdjustments(
            filters,
            linkedAudio,
            listOf(linkedAudio, settingsLayer),
            rangeStartMs = 0L,
            inputLabel = "linkedAudio",
            outputLabel = "adjustedAudio"
        )

        assertEquals("adjustedAudio", output)
        assertTrue(filters.any { it.contains("aecho=0.8000:0.55:37|71|113|173|251|347|461:") })
        assertTrue(filters.any { it.contains("amix=inputs=2:duration=longest:normalize=0") })
    }

    @Test
    fun reverbDryGainMatchesPreviewIntensityAndFxPresetsKeepDrySignal() {
        val halfIntensity = AudioEngine.audioAdjustmentFilters(
            AudioAdjustmentSettings(echo = 50f, reverb = 50f, studioReverb = 50f)
        )
        assertTrue(halfIntensity.all { !it.startsWith("aecho=") || it.startsWith("aecho=0.9000:") })

        listOf("echo", "reverb", "cave", "stadium").forEach { effect ->
            assertTrue(
                "$effect should not attenuate the dry signal",
                AudioEngine.buildAudioFilter(effect, 100f).startsWith("aecho=1.0:")
            )
        }
    }

    @Test
    fun audioSettingsLayerDoesNotAffectAudioTracksBelowIt() {
        val audioClip = EditorClip(
            uri = Uri.EMPTY,
            name = "Audio",
            type = "audio/mpeg",
            sourceStartMs = 0L,
            sourceEndMs = 4_000L,
            timelineStartMs = 0L,
            trackIndex = 2,
            isAudio = true
        )
        val settingsLayer = EditorClip(
            uri = Uri.EMPTY,
            name = "Audio Settings",
            type = "audiofx/plain",
            sourceStartMs = 0L,
            sourceEndMs = 4_000L,
            timelineStartMs = 0L,
            trackIndex = 1,
            isAudio = true,
            audioAdjustments = AudioAdjustmentSettings(reverb = 100f)
        )
        val filters = mutableListOf<String>()

        val output = AudioEngine.appendTimelineAudioAdjustments(
            filters,
            audioClip,
            listOf(audioClip, settingsLayer),
            rangeStartMs = 0L,
            inputLabel = "audio",
            outputLabel = "adjustedAudio"
        )

        assertEquals("audio", output)
        assertTrue(filters.isEmpty())
    }

    @Test
    fun audioSettingsLayerUsesAbsoluteTimelineForTrimmedExportRange() {
        val originalAudio = EditorClip(
            id = "audio",
            uri = Uri.EMPTY,
            name = "Audio",
            type = "audio/mpeg",
            sourceStartMs = 0L,
            sourceEndMs = 20_000L,
            timelineStartMs = 0L,
            trackIndex = 0,
            isAudio = true
        )
        val trimmedAudio = originalAudio.copy(
            sourceStartMs = 10_000L,
            sourceEndMs = 15_000L,
            timelineStartMs = 0L
        )
        val settingsLayer = EditorClip(
            id = "settings",
            uri = Uri.EMPTY,
            name = "Audio Settings",
            type = "audiofx/plain",
            sourceStartMs = 0L,
            sourceEndMs = 5_000L,
            timelineStartMs = 10_000L,
            trackIndex = 1,
            isAudio = true,
            audioAdjustments = AudioAdjustmentSettings(reverb = 80f)
        )
        val filters = mutableListOf<String>()

        val output = AudioEngine.appendTimelineAudioAdjustments(
            filters,
            trimmedAudio,
            listOf(originalAudio, settingsLayer),
            rangeStartMs = 10_000L,
            inputLabel = "audio",
            outputLabel = "adjustedAudio"
        )

        assertEquals("adjustedAudio", output)
        assertTrue(filters.any { it.contains("between(t,0.0000,5.0000)") })
        assertTrue(filters.any { it.contains("aecho=") })
    }

    @Test
    fun audioMixesDoNotAutomaticallyAttenuateOriginalAudio() {
        val source = EditorClip(
            uri = Uri.EMPTY,
            name = "Audio",
            type = "audio/mpeg",
            sourceStartMs = 0L,
            sourceEndMs = 4_000L,
            timelineStartMs = 0L,
            isAudio = true
        )
        val effectLayer = EditorClip(
            uri = Uri.EMPTY,
            name = "Audio Effect",
            type = "audiofx/plain",
            sourceStartMs = 0L,
            sourceEndMs = 4_000L,
            timelineStartMs = 0L,
            isAudio = true,
            audioFx = "echo"
        )
        val filterParts = mutableListOf<String>()

        AudioEngine.appendTimelineAudioEffects(
            filterParts,
            listOf(source, effectLayer),
            rangeStartMs = 0L,
            baseLabel = "original",
            outLabel = "output"
        )

        assertTrue(filterParts.any { it.contains("amix=inputs=2:duration=longest:normalize=0") })
    }
}
