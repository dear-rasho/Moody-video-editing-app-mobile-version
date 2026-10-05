package com.moody.moodyvideoeditor

import com.moody.moodyvideoeditor.data.Keyframe
import com.moody.moodyvideoeditor.utils.KeyframeStore
import com.moody.moodyvideoeditor.ui.components.TimelinePlayheadController
import org.junit.Assert.assertEquals
import org.junit.Test

class TimelineKeyframeMappingTest {

    @Test
    fun screenXMapsToTimelineTimeIncludingLabelAndScroll() {
        val timeMs = TimelinePlayheadController.seekTimeFromScreenX(
            screenX = 300f,
            contentWidthPx = 1000f,
            totalMs = 10_000L,
            labelWidthPx = 100f,
            hScrollValue = 300
        )

        assertEquals(5_000L, timeMs)
    }

    @Test
    fun screenXMappingClampsToTimelineEnds() {
        assertEquals(
            0L,
            TimelinePlayheadController.seekTimeFromScreenX(
                screenX = 20f,
                contentWidthPx = 1000f,
                totalMs = 10_000L,
                labelWidthPx = 100f,
                hScrollValue = 0
            )
        )
        assertEquals(
            10_000L,
            TimelinePlayheadController.seekTimeFromScreenX(
                screenX = 1200f,
                contentWidthPx = 1000f,
                totalMs = 10_000L,
                labelWidthPx = 100f,
                hScrollValue = 0
            )
        )
    }

    @Test
    fun keyframeMarkerUsesSameProjectTimelineCoordinatesAsPlayhead() {
        val clipStartMs = 3_000L
        val keyframeLocalMs = 2_000L
        val totalMs = 12_000L
        val contentWidthPx = 1_200f

        val clipStartX = TimelinePlayheadController.contentXForTime(
            clipStartMs, totalMs, contentWidthPx
        )
        val markerLocalX = TimelinePlayheadController.clipLocalXForTime(
            clipStartMs, keyframeLocalMs, totalMs, contentWidthPx
        )
        val playheadAtKeyframeX = TimelinePlayheadController.contentXForTime(
            clipStartMs + keyframeLocalMs, totalMs, contentWidthPx
        )

        assertEquals(playheadAtKeyframeX, clipStartX + markerLocalX, 0.001f)
    }

    @Test
    fun playheadConvertsToClipLocalKeyframeTime() {
        assertEquals(
            2f,
            KeyframeStore.clipLocalTimeSeconds(
                playheadMs = 5_000L,
                clipStartMs = 3_000L,
                clipDurationMs = 8_000L
            ),
            0.001f
        )
        assertEquals(
            0f,
            KeyframeStore.clipLocalTimeSeconds(2_000L, 3_000L, 8_000L),
            0.001f
        )
        assertEquals(
            8f,
            KeyframeStore.clipLocalTimeSeconds(20_000L, 3_000L, 8_000L),
            0.001f
        )
    }

    @Test
    fun keyframeAddedAtPlayheadAppearsAtSameClipLocalPosition() {
        val clipStartMs = 0L
        val clipDurationMs = 10_000L
        val clipWidthPx = 500f
        val playheadMs = 1_000L

        val savedTimeSec = KeyframeStore.clipLocalTimeSeconds(
            playheadMs = playheadMs,
            clipStartMs = clipStartMs,
            clipDurationMs = clipDurationMs
        )
        val markerX = KeyframeStore.keyframeXInClip(
            keyframeTimeSec = savedTimeSec,
            clipDurationMs = clipDurationMs,
            clipWidthPx = clipWidthPx
        )

        assertEquals(1f, savedTimeSec, 0.001f)
        assertEquals(50f, markerX, 0.001f)
    }

    @Test
    fun autoKeyframeTransformChangesAreVisibleAtPlayhead() {
        var keyframes = mapOf(
            "scale" to listOf(Keyframe(time = 0f, value = 100f, ease = "linear")),
            "rotation" to listOf(Keyframe(time = 0f, value = 0f, ease = "linear"))
        )
        keyframes = KeyframeStore.autoKeyframeIfActive(
            keyframes, "scale", 2f, 180f
        )
        keyframes = KeyframeStore.autoKeyframeIfActive(
            keyframes, "rotation", 2f, 45f
        )

        val liveTransform = KeyframeStore.sampleScaleRotation(
            map = keyframes,
            timeSec = 2f,
            baseScale = 100f,
            baseRotation = 0f
        )

        assertEquals(180f, liveTransform.scale, 0.001f)
        assertEquals(45f, liveTransform.rotation, 0.001f)
    }

    @Test
    fun scaleAndRotationInterpolateBetweenKeyframes() {
        val values = KeyframeStore.sampleScaleRotation(
            map = mapOf(
                "scale" to listOf(
                    Keyframe(time = 0f, value = 100f, ease = "linear"),
                    Keyframe(time = 2f, value = 200f, ease = "linear")
                ),
                "rotation" to listOf(
                    Keyframe(time = 0f, value = 0f, ease = "linear"),
                    Keyframe(time = 2f, value = 90f, ease = "linear")
                )
            ),
            timeSec = 1f,
            baseScale = 100f,
            baseRotation = 0f
        )

        assertEquals(150f, values.scale, 0.001f)
        assertEquals(45f, values.rotation, 0.001f)
    }
}
