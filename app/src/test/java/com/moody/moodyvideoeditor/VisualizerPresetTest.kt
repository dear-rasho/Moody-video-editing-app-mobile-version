package com.moody.moodyvideoeditor

import com.moody.moodyvideoeditor.data.VisualizerPreset
import com.moody.moodyvideoeditor.data.VisualizerState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VisualizerPresetTest {
    @Test
    fun presetsHaveUniqueKeysAndValidCategories() {
        val presets = VisualizerPreset.values()
        assertTrue(presets.isNotEmpty())
        assertEquals(presets.size, presets.map { it.key }.distinct().size)
        assertTrue(presets.all { it.category in setOf("spectrum", "particles", "neon", "geometric", "cinematic") })
    }

    @Test
    fun stateUsesSelectedPresetAndBeatStrength() {
        val state = VisualizerState(
            preset = VisualizerPreset.NEON_GLOW_RING,
            beatTimesMs = listOf(100L),
            beatStrengths = listOf(0.8f)
        )

        assertEquals(VisualizerPreset.NEON_GLOW_RING, state.preset)
        assertEquals(0.8f, state.strengthAt(100L), 0.001f)
        assertTrue(state.isActive)
        assertFalse(state.copy(opacity = 0f).isActive)
    }
}
