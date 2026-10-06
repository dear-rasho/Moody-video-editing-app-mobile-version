package com.moody.moodyvideoeditor

import com.moody.moodyvideoeditor.data.TransitionState
import com.moody.moodyvideoeditor.utils.TransitionExportPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TransitionExportPlanTest {
    @Test
    fun placesTransitionAcrossAdjacentClipJoin() {
        val plan = TransitionExportPlan.create(
            clipDurationsMs = listOf(5000L, 4000L),
            transitions = listOf(null, TransitionState("slideLeft", 800L)),
            fps = 30
        )

        assertEquals(1, plan.size)
        assertEquals(800L, plan.single().durationMs)
        assertEquals(4200L, plan.single().offsetMs)
        assertEquals("slideleft", plan.single().ffmpegTransition)
    }

    @Test
    fun clampsTransitionToShorterClipAndLeavesFrameForStableTiming() {
        val plan = TransitionExportPlan.create(
            clipDurationsMs = listOf(300L, 2500L),
            transitions = listOf(null, TransitionState("fadeblack", 3000L)),
            fps = 30
        )

        assertEquals(266L, plan.single().durationMs)
        assertTrue(plan.single().offsetMs > 0L)
    }

    @Test
    fun preservesAJoinWithoutAnExplicitTransitionAsOneFrame() {
        val plan = TransitionExportPlan.create(
            clipDurationsMs = listOf(2000L, 2000L, 2000L),
            transitions = listOf(null, null, TransitionState()),
            fps = 30
        )

        assertEquals(2, plan.size)
        assertEquals(34L, plan[0].durationMs)
        assertEquals(34L, plan[1].durationMs)
    }
}
