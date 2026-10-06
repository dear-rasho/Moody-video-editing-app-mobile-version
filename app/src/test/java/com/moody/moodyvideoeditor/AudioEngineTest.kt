package com.moody.moodyvideoeditor

import com.moody.moodyvideoeditor.utils.AudioEngine
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
}
