package com.moody.moodyvideoeditor

import com.moody.moodyvideoeditor.data.KeyframeLibrary
import com.moody.moodyvideoeditor.utils.TypographyAnimationPresetLibrary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TypographyAnimationPresetLibraryTest {

    @Test
    fun presetsProvideSpacingAndSizeAnimationsAcrossTheClip() {
        val presets = TypographyAnimationPresetLibrary.presets
        assertTrue(presets.size >= 4)
        assertTrue(presets.any { it.sizeScale != null })
        assertTrue(presets.any { it.letterSpacing != null })

        presets.forEach { preset ->
            val tracks = TypographyAnimationPresetLibrary.keyframesFor(
                preset = preset,
                baseSize = 48,
                baseLetterSpacing = 0f,
                durationSec = 4f
            )
            assertEquals(0f, tracks.getValue(KeyframeLibrary.TEXT_SIZE).first().time, 0.001f)
            assertEquals(4f, tracks.getValue(KeyframeLibrary.TEXT_SIZE).last().time, 0.001f)
            assertEquals(0f, tracks.getValue(KeyframeLibrary.TEXT_LETTER_SPACING).first().time, 0.001f)
            assertEquals(4f, tracks.getValue(KeyframeLibrary.TEXT_LETTER_SPACING).last().time, 0.001f)
            assertTrue(
                tracks.getValue(KeyframeLibrary.TEXT_SIZE).all { it.value in 0f..200f }
            )
            assertTrue(
                tracks.getValue(KeyframeLibrary.TEXT_LETTER_SPACING)
                    .all { it.value in -200f..300f }
            )
        }
    }

    @Test
    fun sizeRevealStartsInvisibleAndSpacingUnfoldEndsAtNormalTracking() {
        val grow = TypographyAnimationPresetLibrary.find("size-grow")!!
        val growTrack = TypographyAnimationPresetLibrary.keyframesFor(
            grow, baseSize = 48, baseLetterSpacing = 0f, durationSec = 2f
        ).getValue(KeyframeLibrary.TEXT_SIZE)
        assertEquals(0f, growTrack.first().value, 0.001f)
        assertEquals(48f, growTrack.last().value, 0.001f)

        val unfold = TypographyAnimationPresetLibrary.find("spacing-unfold")!!
        val spacingTrack = TypographyAnimationPresetLibrary.keyframesFor(
            unfold, baseSize = 48, baseLetterSpacing = 0f, durationSec = 2f
        ).getValue(KeyframeLibrary.TEXT_LETTER_SPACING)
        assertEquals(300f, spacingTrack.first().value, 0.001f)
        assertEquals(0f, spacingTrack.last().value, 0.001f)
    }
}
