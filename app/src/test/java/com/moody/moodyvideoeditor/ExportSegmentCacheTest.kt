package com.moody.moodyvideoeditor

import com.moody.moodyvideoeditor.utils.ExportSegmentCache
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ExportSegmentCacheTest {

    @Test
    fun matchingProjectSegmentAndQualityProduceStableCacheKey() {
        val first = cacheKey(bitrateKbps = 8_000)
        val second = cacheKey(bitrateKbps = 8_000)

        assertEquals(first, second)
    }

    @Test
    fun cacheKeyChangesWhenExportQualityChanges() {
        val standard = cacheKey(bitrateKbps = 8_000)
        val highBitrate = cacheKey(bitrateKbps = 16_000)

        assertNotEquals(standard, highBitrate)
    }

    @Test
    fun cacheKeyChangesWhenTimelineContentChanges() {
        val original = cacheKey(clipSignature = "clip-state")
        val edited = cacheKey(clipSignature = "clip-state-with-adjustment")

        assertNotEquals(original, edited)
    }

    @Test
    fun cacheKeyChangesWhenSegmentRangeChanges() {
        val original = cacheKey(startMs = 0L, endMs = 600_000L)
        val changedRange = cacheKey(startMs = 0L, endMs = 500_000L)

        assertNotEquals(original, changedRange)
    }

    private fun cacheKey(
        bitrateKbps: Int = 8_000,
        clipSignature: String = "clip-state",
        startMs: Long = 0L,
        endMs: Long = 600_000L
    ): String =
        ExportSegmentCache.keyForSignature(
            segmentIndex = 0,
            startMs = startMs,
            endMs = endMs,
            clipSignature = clipSignature,
            width = 1280,
            height = 720,
            fps = 30,
            bitrateKbps = bitrateKbps,
            format = "mp4"
        )
}
