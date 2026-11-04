package com.moody.moodyvideoeditor

import com.moody.moodyvideoeditor.data.MaskPoint
import com.moody.moodyvideoeditor.data.MaskAnimatableProps
import com.moody.moodyvideoeditor.data.MaskState
import com.moody.moodyvideoeditor.utils.MaskEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MaskEngineTest {

    @Test
    fun maskPositionValuesClampToThreeInEitherDirection() {
        val moved = MaskState().withValue("positionX", 8f)
            .withValue("positionY", -8f)

        assertEquals(3f, moved.positionX, 0f)
        assertEquals(-3f, moved.positionY, 0f)
        assertEquals(-3f..3f, MaskAnimatableProps.positionRange)
    }

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
