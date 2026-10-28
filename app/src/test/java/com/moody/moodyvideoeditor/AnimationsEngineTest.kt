package com.moody.moodyvideoeditor

import com.moody.moodyvideoeditor.utils.AnimationsEngine
import org.junit.Assert.assertEquals
import org.junit.Test

class AnimationsEngineTest {

    @Test
    fun combinesPresetAndTextAnimationFrames() {
        val fade = AnimationsEngine.computeFrame("fadeUp", 0.5f)
        val combined = AnimationsEngine.computeTextFrame(
            textAnimation = "fadeUp",
            presetAnimation = "popIn",
            progress = 0.5f,
            elapsedSec = 0.5f,
            presetProgress = 0.25f
        )
        val pop = AnimationsEngine.computeFrame("popIn", 0.25f)

        assertEquals(fade.alpha * pop.alpha, combined.alpha, 0.0001f)
        assertEquals(fade.scaleX * pop.scaleX, combined.scaleX, 0.0001f)
        assertEquals(fade.translateY + pop.translateY, combined.translateY, 0.0001f)
    }

    @Test
    fun presetAnimationDoesNotReplaceTextAnimationSelection() {
        val state = com.moody.moodyvideoeditor.utils.CaptionPresetLibrary.applyTo(
            text = "sample",
            presetId = "caption-animation-2",
            highlightColor = 0xFFFF00FFL
        )

        assertEquals("none", state.animation)
        assertEquals("fadeUp", state.captionPresetAnimation)
    }
}
