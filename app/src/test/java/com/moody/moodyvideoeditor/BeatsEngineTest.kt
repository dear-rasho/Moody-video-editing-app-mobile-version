package com.moody.moodyvideoeditor

import com.moody.moodyvideoeditor.utils.BeatsEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class BeatsEngineTest {

    @Test
    fun detectsEvenlySpacedPercussionOnsetsWithAccurateTiming() {
        val sampleRate = 22_050
        val samples = FloatArray(sampleRate * 6)
        val expectedTimes = (300L..5_800L step 500L).toList()

        expectedTimes.forEach { beatMs ->
            val start = (beatMs * sampleRate / 1_000L).toInt()
            val pulseSamples = sampleRate * 40 / 1_000
            for (i in 0 until pulseSamples) {
                val index = start + i
                if (index < samples.size) {
                    samples[index] =
                        (sin(2.0 * PI * 120.0 * i / sampleRate) * 0.6).toFloat()
                }
            }
        }

        val (detected, strengths) = BeatsEngine.analyzeBeatsWithStrengths(samples, "all")

        assertEquals(expectedTimes.size, detected.size)
        assertEquals(detected.size, strengths.size)
        detected.zip(expectedTimes).forEach { (actual, expected) ->
            assertTrue("Expected $expected ms, got $actual ms", kotlin.math.abs(actual - expected) <= 40L)
        }
        assertTrue(strengths.all { it in 0f..1f && it > 0f })
    }

    @Test
    fun silenceDoesNotProduceBeats() {
        val samples = FloatArray(22_050)

        val (detected, strengths) = BeatsEngine.analyzeBeatsWithStrengths(samples, "all")

        assertTrue(detected.isEmpty())
        assertTrue(strengths.isEmpty())
    }
}
