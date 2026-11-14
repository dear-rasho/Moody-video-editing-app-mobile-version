package com.moody.moodyvideoeditor

import com.moody.moodyvideoeditor.data.Keyframe
import com.moody.moodyvideoeditor.data.KeyframeLibrary
import com.moody.moodyvideoeditor.utils.SpeedEngine
import android.net.TestUri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class SpeedEngineTest {
    @Test
    fun speedRampMapsTimelineToFullSourceRange() {
        val keyframes = mapOf(
            KeyframeLibrary.SPEED to listOf(
                Keyframe(time = 0f, value = 0.5f),
                Keyframe(time = 10f, value = 1.5f)
            )
        )

        assertEquals(
            0.5f,
            SpeedEngine.playbackSpeedAt(keyframes, 1f, 10_000L, 0L),
            0.01f
        )
        assertEquals(
            1f,
            SpeedEngine.playbackSpeedAt(keyframes, 1f, 10_000L, 5_000L),
            0.02f
        )
        assertEquals(
            3_750L,
            SpeedEngine.sourcePositionAt(
                0L, 10_000L, 1f, 10_000L, keyframes, 5_000L
            )
        )
        assertEquals(
            10_000L,
            SpeedEngine.sourcePositionAt(
                0L, 10_000L, 1f, 10_000L, keyframes, 10_000L
            )
        )
    }

    @Test
    fun easingChangesTheKeyframedSpeedCurve() {
        val linear = mapOf(
            KeyframeLibrary.SPEED to listOf(
                Keyframe(0f, 0.5f),
                Keyframe(2f, 2.5f)
            )
        )
        val easeIn = mapOf(
            KeyframeLibrary.SPEED to listOf(
                Keyframe(0f, 0.5f, "easeIn"),
                Keyframe(2f, 2.5f)
            )
        )

        assertEquals(1.5f, SpeedEngine.speedAt(linear, 1f, 1_000L), 0.001f)
        assertEquals(1.0f, SpeedEngine.speedAt(easeIn, 1f, 1_000L), 0.001f)
    }

    @Test
    fun changingLaterKeyframeDoesNotRescaleEarlierSegment() {
        val slowerLater = mapOf(
            KeyframeLibrary.SPEED to listOf(
                Keyframe(0f, 1f),
                Keyframe(3f, 0.5f),
                Keyframe(8f, 2f)
            )
        )
        val fasterLater = mapOf(
            KeyframeLibrary.SPEED to listOf(
                Keyframe(0f, 1f),
                Keyframe(3f, 0.5f),
                Keyframe(8f, 3f)
            )
        )

        assertEquals(
            SpeedEngine.sourcePositionAt(0L, 10_000L, 1f, 20_000L, slowerLater, 2_000L),
            SpeedEngine.sourcePositionAt(0L, 10_000L, 1f, 20_000L, fasterLater, 2_000L)
        )
        assertEquals(
            SpeedEngine.playbackSpeedAt(slowerLater, 1f, 20_000L, 2_000L),
            SpeedEngine.playbackSpeedAt(fasterLater, 1f, 20_000L, 2_000L),
            0.001f
        )
    }

    @Test
    fun rampDurationFollowsIntegratedKeyframedSpeed() {
        val halfSpeed = mapOf(
            KeyframeLibrary.SPEED to listOf(
                Keyframe(0f, 0.5f),
                Keyframe(10f, 0.5f)
            )
        )
        val durationMs = SpeedEngine.durationMsFor(10_000L, 1f, halfSpeed)

        assertTrue(abs(durationMs - 20_000L) < 50L)
        assertTrue(
            abs(
                SpeedEngine.sourcePositionAt(
                    0L, 10_000L, 1f, durationMs, halfSpeed, durationMs
                ) - 10_000L
            ) < 50L
        )
    }

    @Test
    fun sourceTrimPositionMapsBackToItsTimelineOffset() {
        val keyframes = mapOf(
            KeyframeLibrary.SPEED to listOf(
                Keyframe(time = 0f, value = 0.5f),
                Keyframe(time = 10f, value = 1.5f)
            )
        )
        val clip = com.moody.moodyvideoeditor.data.EditorClip(
            uri = TestUri(),
            name = "speed ramp",
            sourceStartMs = 0L,
            sourceEndMs = 10_000L,
            timelineStartMs = 0L,
            keyframes = keyframes
        )
        val sourcePosition = SpeedEngine.sourcePositionAt(clip, 5_000L)

        assertTrue(
            abs(SpeedEngine.timelineOffsetForSourcePosition(clip, sourcePosition) - 5_000L) < 30L
        )
    }
}
