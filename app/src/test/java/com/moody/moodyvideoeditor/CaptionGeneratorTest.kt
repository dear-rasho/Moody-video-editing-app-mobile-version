package com.moody.moodyvideoeditor

import com.moody.moodyvideoeditor.utils.CaptionGenerator
import com.moody.moodyvideoeditor.utils.CaptionSegment
import org.junit.Assert.assertEquals
import org.junit.Test

class CaptionGeneratorTest {

    @Test
    fun fitSegmentsToClipDurationClampsCaptionsToTrimmedTimelineRange() {
        val result = CaptionGenerator.fitSegmentsToClipDuration(
            segments = listOf(
                CaptionSegment(0L, 10_000L, "inside"),
                CaptionSegment(20_000L, 30_000L, "crosses trim"),
                CaptionSegment(30_000L, 40_000L, "outside")
            ),
            clipDurationMs = 25_000L,
            speed = 1f
        )

        assertEquals(
            listOf(
                CaptionSegment(0L, 10_000L, "inside"),
                CaptionSegment(20_000L, 25_000L, "crosses trim")
            ),
            result
        )
    }

    @Test
    fun fitSegmentsToClipDurationMapsSourceTimesToPlaybackSpeed() {
        val result = CaptionGenerator.fitSegmentsToClipDuration(
            segments = listOf(CaptionSegment(8_000L, 16_000L, "fast clip")),
            clipDurationMs = 10_000L,
            speed = 2f
        )

        assertEquals(listOf(CaptionSegment(4_000L, 8_000L, "fast clip")), result)
    }

    @Test
    fun pcmIsSplitIntoOrderedBoundedRangesForIncrementalTranscription() {
        assertEquals(
            listOf(0..3, 4..7, 8..9),
            CaptionGenerator.buildPcmChunkRanges(sampleCount = 10, chunkSamples = 4)
        )
    }

    @Test
    fun invalidPcmChunkSizesProduceNoRanges() {
        assertEquals(emptyList<IntRange>(), CaptionGenerator.buildPcmChunkRanges(0, 4))
        assertEquals(emptyList<IntRange>(), CaptionGenerator.buildPcmChunkRanges(10, 0))
    }
}
