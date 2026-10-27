package com.moody.moodyvideoeditor

import com.moody.moodyvideoeditor.data.Keyframe
import com.moody.moodyvideoeditor.data.SpotlightState
import com.moody.moodyvideoeditor.utils.SpotlightAnimation
import org.junit.Assert.assertEquals
import org.junit.Test

class SpotlightAnimationTest {

    @Test
    fun samplesPositionIntensityRotationAndColorChannels() {
        val state = SpotlightState()
        val keyframes = mapOf(
            "spotlight.sourceX" to listOf(Keyframe(0f, 20f, "linear"), Keyframe(2f, 60f, "linear")),
            "spotlight.intensity" to listOf(Keyframe(0f, 0.5f, "linear"), Keyframe(2f, 2.5f, "linear")),
            "spotlight.rotation" to listOf(Keyframe(0f, -30f, "linear"), Keyframe(2f, 30f, "linear")),
            "spotlight.colorR" to listOf(Keyframe(0f, 0f, "linear"), Keyframe(2f, 200f, "linear"))
        )

        val sampled = SpotlightAnimation.sample(state, keyframes, 1f)

        assertEquals(40f, sampled.sourceX, 0.001f)
        assertEquals(1.5f, sampled.intensity, 0.001f)
        assertEquals(0f, sampled.rotationDeg, 0.001f)
        assertEquals(100, ((sampled.color shr 16) and 0xFF).toInt())
    }

    @Test
    fun propertyEditAutoUpdatesOnlyAnExistingAnimationTrack() {
        val initial = SpotlightState()
        val animated = mapOf(
            "spotlight.targetX" to listOf(Keyframe(0f, 50f))
        )
        val updated = SpotlightAnimation.updateAutoKeyframes(
            oldState = initial,
            newState = initial.copy(targetX = 82f, intensity = 2f),
            keyframes = animated,
            timeSec = 1f
        )

        assertEquals(82f, updated["spotlight.targetX"]?.last()?.value)
        assertEquals(null, updated["spotlight.intensity"])
    }
}
