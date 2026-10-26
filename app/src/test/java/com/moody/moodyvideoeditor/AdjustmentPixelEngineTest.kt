package com.moody.moodyvideoeditor

import com.moody.moodyvideoeditor.data.AdjustmentData
import com.moody.moodyvideoeditor.utils.AdjustmentPixelEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdjustmentPixelEngineTest {

    @Test
    fun colorChannelAdjustmentMatchesPreviewShaderHueResponse() {
        val adjusted = AdjustmentPixelEngine.applyColorChannels(
            204f,
            51f,
            26f,
            AdjustmentData(reds = 100f)
        )

        assertTrue(adjusted.first > 245f)
        assertTrue(adjusted.second < 30f)
        assertTrue(adjusted.third < 16f)
    }

    @Test
    fun colorChannelAdjustmentLeavesUnsaturatedPixelsUnchanged() {
        val input = Triple(100f, 100f, 100f)
        val adjusted = AdjustmentPixelEngine.applyColorChannels(
            input.first,
            input.second,
            input.third,
            AdjustmentData(reds = 100f, blues = -100f)
        )

        assertEquals(input, adjusted)
    }
}
