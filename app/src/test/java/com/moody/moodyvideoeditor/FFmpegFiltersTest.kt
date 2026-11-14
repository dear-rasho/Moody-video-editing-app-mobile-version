package com.moody.moodyvideoeditor

import com.moody.moodyvideoeditor.data.AdjustmentData
import com.moody.moodyvideoeditor.utils.FFmpegFilters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FFmpegFiltersTest {

    @Test
    fun zeroAndNegativeVignetteDoNotGenerateZeroAngleFilter() {
        assertEquals("", FFmpegFilters.build(AdjustmentData(vignette = 0f)))
        assertEquals("", FFmpegFilters.build(AdjustmentData(vignette = -20f)))
    }

    @Test
    fun positiveVignetteProducesAValidPositiveAngle() {
        val filter = FFmpegFilters.build(AdjustmentData(vignette = 25f))

        assertTrue(filter.startsWith("vignette=angle="))
        assertFalse(filter.contains("angle=0.0000"))
    }

    @Test
    fun tinyVignetteThatRoundsToZeroIsOmitted() {
        val filter = FFmpegFilters.build(AdjustmentData(vignette = 0.0001f))

        assertFalse(filter.contains("vignette="))
    }

    @Test
    fun timelineEnableIsAddedToExpensiveAdjustmentFilter() {
        val filter = FFmpegFilters.withTimelineEnable(
            "lut3d=file='adjustment.cube':interp=tetrahedral",
            "between(t,2.0,4.0)"
        )

        assertEquals(
            "lut3d=file='adjustment.cube':interp=tetrahedral:enable='between(t,2.0,4.0)'",
            filter
        )
    }

    @Test
    fun adjustmentFilterIsUnchangedWithoutTimelineEnable() {
        val filter = "lut3d=file='adjustment.cube':interp=tetrahedral"

        assertEquals(filter, FFmpegFilters.withTimelineEnable(filter, null))
    }
}
