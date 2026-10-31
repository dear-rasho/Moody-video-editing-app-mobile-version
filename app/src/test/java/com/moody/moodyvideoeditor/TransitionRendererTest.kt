package com.moody.moodyvideoeditor

import com.moody.moodyvideoeditor.utils.TransitionRenderer
import org.junit.Assert.assertEquals
import org.junit.Test

class TransitionRendererTest {

    @Test
    fun fadeTransitionsAnimateIncomingClipOpacity() {
        val keys = listOf(
            "fade",
            "fadeBlack",
            "fadeWhite",
            "dissolve",
            "xfade-fadefast-0",
            "xfade-fadewhite-13"
        )

        keys.forEach { key ->
            assertEquals(
                "Incoming alpha should follow progress for $key",
                0.4f,
                TransitionRenderer.getIncomingTransform(key, 0.4f, 100f, 100f).alpha,
                0.001f
            )
        }
    }

    @Test
    fun incomingFadeOpacityClampsProgress() {
        assertEquals(
            0f,
            TransitionRenderer.getIncomingTransform("fade", -0.5f, 100f, 100f).alpha,
            0.001f
        )
        assertEquals(
            1f,
            TransitionRenderer.getIncomingTransform("fade", 1.5f, 100f, 100f).alpha,
            0.001f
        )
    }

    @Test
    fun geometricTransitionsDoNotScaleIncomingClip() {
        val keys = listOf(
            "circleopen",
            "circleclose",
            "xfade-circlecrop-10",
            "xfade-rectcrop-11",
            "xfade-radial-12",
            "xfade-vertopen-13",
            "xfade-horzclose-14",
            "diagtl",
            "xfade-wipebr-15"
        )

        keys.forEach { key ->
            val transform = TransitionRenderer.getIncomingTransform(key, 0.4f, 100f, 100f)
            assertEquals("Unexpected incoming scale for $key", 1f, transform.scaleX, 0.001f)
            assertEquals("Unexpected incoming scale for $key", 1f, transform.scaleY, 0.001f)
            assertEquals("Unexpected incoming translation for $key", 0f, transform.tx, 0.001f)
            assertEquals("Unexpected incoming translation for $key", 0f, transform.ty, 0.001f)
        }
    }
}
