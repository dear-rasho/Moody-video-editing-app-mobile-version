package com.moody.moodyvideoeditor

import com.moody.moodyvideoeditor.data.MaskPoint
import com.moody.moodyvideoeditor.utils.MaskEngine
import org.junit.Assert.assertTrue
import org.junit.Test

class MaskEngineTest {

    @Test
    fun customMaskExpansionMovesOutwardForEitherPointWinding() {
        val clockwise = listOf(
            MaskPoint(0.25f, 0.25f),
            MaskPoint(0.75f, 0.25f),
            MaskPoint(0.75f, 0.75f),
            MaskPoint(0.25f, 0.75f)
        )
        val counterClockwise = clockwise.reversed()

        val expandedClockwise = MaskEngine.expandedPoints(clockwise, 0.1f)
        val expandedCounterClockwise = MaskEngine.expandedPoints(counterClockwise, 0.1f)

        assertTrue(expandedClockwise[1].y < clockwise[1].y)
        assertTrue(expandedCounterClockwise[3].y < counterClockwise[3].y)
    }
}
