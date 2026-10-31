package com.moody.moodyvideoeditor

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
}
