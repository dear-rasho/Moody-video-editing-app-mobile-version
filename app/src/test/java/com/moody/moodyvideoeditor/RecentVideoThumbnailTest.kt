package com.moody.moodyvideoeditor

import com.moody.moodyvideoeditor.ui.features.videoThumbnailDimensions
import org.junit.Assert.assertEquals
import org.junit.Test

class RecentVideoThumbnailTest {

    @Test
    fun largeLandscapeVideoThumbnailFitsInsideBounds() {
        assertEquals(320 to 180, videoThumbnailDimensions(3840, 2160))
    }

    @Test
    fun portraitVideoThumbnailPreservesAspectRatio() {
        assertEquals(180 to 320, videoThumbnailDimensions(1080, 1920))
    }

    @Test
    fun smallVideoIsNotUpscaled() {
        assertEquals(160 to 90, videoThumbnailDimensions(160, 90))
    }
}
