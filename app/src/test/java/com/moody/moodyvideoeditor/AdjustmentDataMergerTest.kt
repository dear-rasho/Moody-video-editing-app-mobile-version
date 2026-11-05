package com.moody.moodyvideoeditor

import com.moody.moodyvideoeditor.data.AdjustmentData
import com.moody.moodyvideoeditor.utils.AdjustmentDataMerger
import org.junit.Assert.assertEquals
import org.junit.Test

class AdjustmentDataMergerTest {

    @Test
    fun previewAdjustmentCombinesClipAndAdjustmentLayerPixelControls() {
        val merged = AdjustmentDataMerger.mergePreviewAdjustments(
            AdjustmentData(reds = 20f, sharpen = 5f, brightness = 10f),
            AdjustmentData(reds = 15f, blues = -10f, sharpen = 7f, brightness = 30f)
        )

        assertEquals(35f, merged.reds, 0f)
        assertEquals(-10f, merged.blues, 0f)
        assertEquals(12f, merged.sharpen, 0f)
        assertEquals(10f, merged.brightness, 0f)
    }

}
