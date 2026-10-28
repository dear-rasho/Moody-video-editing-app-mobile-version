package com.moody.moodyvideoeditor

import com.moody.moodyvideoeditor.utils.CaptionPresetLibrary
import com.moody.moodyvideoeditor.utils.CaptionPresetMode
import com.moody.moodyvideoeditor.utils.CaptionGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptionPresetLibraryTest {

    @Test
    fun exposesAllPreviewablePresetsAndLegacyAnimations() {
        assertEquals(32, CaptionPresetLibrary.presets.size)
        CaptionPresetLibrary.presets.forEach { preset ->
            assertTrue(
                CaptionPresetLibrary.colorRanges(
                    text = "Moody Editing App",
                    presetId = preset.id,
                    highlightColor = 0xFFFF00FF,
                    elapsedMs = 0L,
                    durationMs = 3_000L
                ).isNotEmpty()
            )
        }
        assertTrue(
            CaptionPresetLibrary.presets.map { it.animation }.containsAll(
                listOf(
                    "fadeIn", "fadeUp", "slideUp", "popIn", "bounceIn",
                    "blurryReveal", "characterRise", "jellyBounce",
                    "overshootPop", "crossSlide", "zoomIn", "popScale"
                )
            )
        )
    }

    @Test
    fun wordHighlightAdvancesProportionallyAcrossClipDuration() {
        val preset = CaptionPresetLibrary.presets.first {
            it.mode == CaptionPresetMode.WORD_HIGHLIGHT
        }
        val first = CaptionPresetLibrary.colorRanges(
            "hello my dear donald", preset.id, 0xFFFF00FFL, 0L, 8_000L
        ).single()
        val third = CaptionPresetLibrary.colorRanges(
            "hello my dear donald", preset.id, 0xFFFF00FFL, 4_000L, 8_000L
        ).single()

        assertEquals(0, first.start)
        assertEquals(9, third.start)
        assertEquals(0xFFFF00FFL, third.color)
    }

    @Test
    fun characterHighlightSkipsWhitespace() {
        val preset = CaptionPresetLibrary.presets.first {
            it.mode == CaptionPresetMode.CHARACTER_HIGHLIGHT
        }
        val range = CaptionPresetLibrary.colorRanges(
            "hi all", preset.id, 0xFFFF00FFL, 3_000L, 6_000L
        ).single()

        assertEquals(3, range.start)
        assertEquals(4, range.end)
    }

    @Test
    fun wordColorPresetLeavesGradientBaseUncovered() {
        val preset = CaptionPresetLibrary.presets.first {
            it.mode == CaptionPresetMode.WORD_COLORS
        }

        assertTrue(
            CaptionPresetLibrary.colorRanges(
                text = "hello world",
                presetId = preset.id,
                highlightColor = 0xFFFF00FFL,
                elapsedMs = 0L,
                durationMs = 2_000L,
                preserveGradient = true
            ).isEmpty()
        )
    }

    @Test
    fun presetsIncludeTheFormerAutoCaptionFontAndGlowStyles() {
        val presets = CaptionPresetLibrary.presets
        assertTrue(presets.map { it.fontFamily }.distinct().size > 5)
        assertTrue(presets.any { it.glowEnabled && it.glowRadius >= 26f })
        assertTrue(presets.any { !it.glowEnabled })

        val preset = presets.first()
        val textStyle = CaptionPresetLibrary.applyTo(
            "preview",
            preset.id,
            0xFF00D9FFL
        )
        assertEquals(preset.fontFamily, textStyle.fontFamily)
        assertEquals(preset.glowRadius, textStyle.glowRadius)
    }

    @Test
    fun wordGroupingCreatesSeparatePresetLayersForEachConfiguredChunk() {
        val chunks = CaptionGenerator.buildPresetChunks(
            text = "one two three four five six seven eight nine ten eleven twelve",
            startMs = 1_000L,
            endMs = 9_000L,
            grouping = CaptionGenerator.GroupingMode.WORDS,
            wordsPerLayer = 3,
            wordsPerLine = 1
        )

        assertEquals(4, chunks.size)
        assertEquals("one two three", chunks[0].first)
        assertEquals("four five six", chunks[1].first)
        assertEquals("seven eight nine", chunks[2].first)
        assertEquals("ten eleven twelve", chunks[3].first)
        assertEquals(1_000L, chunks.first().second)
        assertEquals(9_000L, chunks.last().third)
        assertTrue(chunks.zipWithNext().all { (left, right) -> left.third == right.second })
    }
}
