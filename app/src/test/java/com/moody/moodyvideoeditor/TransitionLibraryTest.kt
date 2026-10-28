package com.moody.moodyvideoeditor

import com.moody.moodyvideoeditor.data.TransitionLibrary
import com.moody.moodyvideoeditor.data.TransitionState
import com.moody.moodyvideoeditor.utils.TransitionExportPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TransitionLibraryTest {

    @Test
    fun includesAtLeastFiftyAdditionalExportableTransitions() {
        val additionalTransitions = TransitionLibrary.PRESETS.filter {
            it.key.startsWith("xfade-")
        }

        assertTrue(additionalTransitions.size >= 50)
        assertTrue(additionalTransitions.all { it.ffmpegXfade.isNotBlank() })
        assertTrue(additionalTransitions.all { preset ->
            TransitionLibrary.CATEGORIES.any { it.first == preset.category }
        })
        assertEquals(
            additionalTransitions.size,
            additionalTransitions.map { it.key }.distinct().size
        )
    }

    @Test
    fun includesAtLeastFiftyStyledTransitionsFor3dShakeBlurAndLightLeak() {
        val styledTransitions = TransitionLibrary.PRESETS.filter {
            it.key.startsWith("style-")
        }

        assertTrue(styledTransitions.size >= 50)
        assertTrue(
            styledTransitions.all {
                it.category in setOf("3d", "shake", "blur", "lightleak") &&
                        it.ffmpegXfade.isNotBlank()
            }
        )
        assertTrue(styledTransitions.all { preset ->
            TransitionLibrary.CATEGORIES.any { it.first == preset.category }
        })
    }

    @Test
    fun lightLeakExportsIncludeAColoredMovingOverlayInsteadOfFadeOnly() {
        val preset = TransitionLibrary.PRESETS.first {
            it.key == "style-leak-amber"
        }
        val plan = TransitionExportPlan.create(
            clipDurationsMs = listOf(5_000L, 5_000L),
            transitions = listOf(null, TransitionState(preset.key, 1_000L)),
            fps = 30
        )

        assertEquals(1, plan.size)
        assertEquals("fade", plan.single().ffmpegTransition)
        assertEquals("FFA23A", plan.single().lightLeakColor)
        assertTrue(plan.single().lightLeakMovesRight)
    }

    @Test
    fun rightSidedFlareExportsSweepTowardTheLeft() {
        val preset = TransitionLibrary.PRESETS.first {
            it.key == "style-flare-sweep-right"
        }
        val step = TransitionExportPlan.create(
            clipDurationsMs = listOf(5_000L, 5_000L),
            transitions = listOf(null, TransitionState(preset.key, 1_000L)),
            fps = 30
        ).single()

        assertEquals("FFA23A", step.lightLeakColor)
        assertTrue(!step.lightLeakMovesRight)
    }
}
