package com.moody.moodyvideoeditor

import com.moody.moodyvideoeditor.utils.TextScaler
import org.junit.Assert.assertEquals
import org.junit.Test

class TextScalerTest {

    @Test
    fun lineHeightSupportsLargeUserSuppliedMultipliers() {
        assertEquals(4000f, TextScaler.lineHeight(20f, 200f), 0.001f)
        assertEquals(4000f, TextScaler.lineHeight(20f, 300f), 0.001f)
    }

    @Test
    fun lineHeightUsesSafeValuesForInvalidMultipliers() {
        assertEquals(2f, TextScaler.lineHeight(20f, 0f), 0.001f)
        assertEquals(24f, TextScaler.lineHeight(20f, Float.NaN), 0.001f)
    }
}
