package com.moody.moodyvideoeditor

import com.moody.moodyvideoeditor.data.ColorWheelState
import com.moody.moodyvideoeditor.data.ToneValue
import com.moody.moodyvideoeditor.utils.ColorWheelEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ColorWheelEngineTest {

    @Test
    fun hdrWhiteAdjustsHighlightsWithoutWashingOutShadows() {
        val state = ColorWheelState(hdrWhite = 50f)

        val shadow = ColorWheelEngine.applyPixel(30f, 30f, 30f, state)
        val highlight = ColorWheelEngine.applyPixel(255f, 255f, 255f, state)

        assertEquals(30f, shadow.first, 0.001f)
        assertEquals(191.5f, highlight.first, 0.001f)
    }

    @Test
    fun toneWheelGradesDarkPixelsByTheirTone() {
        val state = ColorWheelState(
            shadows = ToneValue(
                hue = 220f,
                saturation = 85f,
                intensity = 100f
            )
        )

        val original = Triple(45f, 40f, 35f)
        val graded = ColorWheelEngine.applyPixel(
            original.first, original.second, original.third, state
        )
        assertNotEquals(original, graded)
    }
}
