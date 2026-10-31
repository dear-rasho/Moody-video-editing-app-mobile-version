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

    @Test
    fun yellowAdjustmentChangesYellowHueWithoutChangingBlueHue() {
        val yellow = AdjustmentPixelEngine.applyColorChannels(
            220f,
            160f,
            30f,
            AdjustmentData(yellows = 100f)
        )
        val blue = Triple(30f, 80f, 220f)
        val unchangedBlue = AdjustmentPixelEngine.applyColorChannels(
            blue.first,
            blue.second,
            blue.third,
            AdjustmentData(yellows = 100f)
        )

        assertTrue(yellow != Triple(220f, 160f, 30f))
        assertEquals(blue, unchangedBlue)
    }

    @Test
    fun colorChannelValuesClampToSupportedRange() {
        val atLimit = AdjustmentPixelEngine.applyColorChannels(
            204f,
            51f,
            26f,
            AdjustmentData(reds = 500f, skinTones = -500f)
        )
        val beyondLimit = AdjustmentPixelEngine.applyColorChannels(
            204f,
            51f,
            26f,
            AdjustmentData(reds = 1000f, skinTones = -1000f)
        )

        assertEquals(atLimit, beyondLimit)
    }

    @Test
    fun sharpenPreservesUniformAreas() {
        val flat = IntArray(25) { 0xFF646464.toInt() }

        assertEquals(flat.toList(), AdjustmentPixelEngine.applySharpen(flat, 5, 5, 1f).toList())
    }

    @Test
    fun sharpenIncreasesLocalContrastAndPreservesAlpha() {
        val pixels = IntArray(25) { 0xFF646464.toInt() }
        pixels[12] = 0xFFC8C8C8.toInt()

        val sharpened = AdjustmentPixelEngine.applySharpen(pixels, 5, 5, 1f)

        assertTrue((sharpened[12] and 0xFF) > (pixels[12] and 0xFF))
        assertEquals(0xFF, sharpened[12] ushr 24)
    }

    @Test
    fun sharpenDoesNotPullColorFromTransparentPixels() {
        val pixels = IntArray(9) { 0x00FFFFFF }
        pixels[4] = 0xFF646464.toInt()

        val sharpened = AdjustmentPixelEngine.applySharpen(pixels, 3, 3, 2f)

        assertEquals(pixels.toList(), sharpened.toList())
    }
}
