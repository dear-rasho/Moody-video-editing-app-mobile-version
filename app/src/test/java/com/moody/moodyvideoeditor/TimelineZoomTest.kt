package com.moody.moodyvideoeditor

import com.moody.moodyvideoeditor.ui.components.TimelinePlayheadController
import com.moody.moodyvideoeditor.utils.TimelineRuler
import com.moody.moodyvideoeditor.utils.TimelineZoom
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineZoomTest {

    @Test
    fun defaultZoomShowsOneSecondRulerIntervals() {
        val pixelsPerSecond = TimelineZoom.ppsForSlider(TimelineZoom.SLIDER_DEFAULT)

        assertEquals(60f, pixelsPerSecond, 0.01f)
        assertEquals(1f, TimelineRuler.pickStepSec(pixelsPerSecond / 20f))
    }

    @Test
    fun zoomSliderIsTheOnlyControlForTimelineScale() {
        val defaultScale = TimelineZoom.ppsForSlider(TimelineZoom.SLIDER_DEFAULT)

        assertTrue(TimelineZoom.ppsForSlider(TimelineZoom.SLIDER_MIN) < defaultScale)
        assertTrue(TimelineZoom.ppsForSlider(TimelineZoom.SLIDER_MAX) > defaultScale)
    }

    @Test
    fun longTimelinesAndGraphsStayWithinComposeWidthLimit() {
        assertEquals(
            120f,
            TimelineZoom.safeContentWidthDp(120f, density = 2f),
            0.001f
        )
        assertEquals(
            TimelineZoom.MAX_COMPOSE_CONTENT_WIDTH_PX,
            TimelineZoom.safeContentWidthDp(200_000f, density = 2f) * 2f,
            0.001f
        )
    }

    @Test
    fun playheadScreenPositionIsFixedAsTimelineScrolls() {
        assertEquals(
            500f,
            TimelinePlayheadController.fixedPlayheadScreenX(1_000f),
            0.001f
        )

        assertEquals(
            0,
            TimelinePlayheadController.scrollForTime(
                currentPosMs = 0L,
                totalMs = 10_000L,
                contentWidthPx = 600f
            )
        )

        val scroll = TimelinePlayheadController.scrollForTime(
            currentPosMs = 2_000L,
            totalMs = 10_000L,
            contentWidthPx = 600f
        )
        val timeUnderPlayhead = TimelinePlayheadController.timeForScroll(
            scrollValue = scroll,
            totalMs = 10_000L,
            contentWidthPx = 600f
        )

        assertEquals(120, scroll)
        assertEquals(2_000L, timeUnderPlayhead)
    }

    @Test
    fun tappingClipSeeksToTappedTimelinePosition() {
        assertEquals(
            5_000L,
            TimelinePlayheadController.timeForClipTap(
                clipStartMs = 3_000L,
                clipDurationMs = 4_000L,
                localX = 200f,
                pixelsPerMs = 0.1f,
                totalMs = 10_000L
            )
        )
    }

    @Test
    fun addingTimelineDurationDoesNotStretchExistingTimes() {
        val pixelsPerSecond = TimelineZoom.ppsForSlider(TimelineZoom.SLIDER_DEFAULT)
        val positionOnShortTimeline = TimelinePlayheadController.playheadContentPx(
            currentPosMs = 2_000L,
            totalMs = 10_000L,
            contentWidthPx = 10f * pixelsPerSecond
        )
        val positionOnLongTimeline = TimelinePlayheadController.playheadContentPx(
            currentPosMs = 2_000L,
            totalMs = 20_000L,
            contentWidthPx = 20f * pixelsPerSecond
        )

        assertEquals(positionOnShortTimeline, positionOnLongTimeline, 0.001f)
    }
}
