package com.moody.moodyvideoeditor

import com.moody.moodyvideoeditor.utils.ExportSettings
import org.junit.Assert.assertEquals
import org.junit.Test

class ExportSettingsTest {
    @Test
    fun landscapeSixteenByNineExportUsesLandscapeDimensions() {
        assertEquals(
            1280 to 720,
            ExportSettings.targetDimensions("720p", "16:9")
        )
    }

    @Test
    fun portraitNineBySixteenExportUsesPortraitDimensions() {
        assertEquals(
            720 to 1280,
            ExportSettings.targetDimensions("720p", "9:16")
        )
    }

    @Test
    fun squareExportIsProducedOnlyWhenSquareRatioIsSelected() {
        assertEquals(
            1280 to 1280,
            ExportSettings.targetDimensions("720p", "1:1")
        )
    }
}
