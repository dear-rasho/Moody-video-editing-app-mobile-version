package com.moody.moodyvideoeditor

import com.moody.moodyvideoeditor.data.Keyframe
import com.moody.moodyvideoeditor.data.KeyframeLibrary
import com.moody.moodyvideoeditor.utils.KeyframeStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyframeStoreTest {

    @Test
    fun transformEditDoesNotCreateKeyframesForUnanimatedProperties() {
        val keyframes = mapOf(
            "scale" to listOf(Keyframe(time = 0f, value = 100f))
        )

        val updated = KeyframeStore.autoKeyframeIfActive(
            keyframes, "x", time = 1f, value = 2f
        )

        assertTrue(updated["x"].isNullOrEmpty())
        assertEquals(keyframes, updated)
    }

    @Test
    fun transformEditStoresExactValueOnAnAnimatedProperty() {
        val keyframes = mapOf(
            "x" to listOf(Keyframe(time = 0f, value = 50f))
        )

        val updated = KeyframeStore.autoKeyframeIfActive(
            keyframes, "x", time = 1f, value = 2f
        )

        assertEquals(2f, KeyframeStore.sample(updated, "x", 1f, 50f), 0.001f)
    }

    @Test
    fun textStylePropertiesAreAnimatableAndInterpolate() {
        val map = mapOf(
            KeyframeLibrary.TEXT_SIZE to listOf(
                Keyframe(time = 0f, value = -100f),
                Keyframe(time = 2f, value = 200f)
            ),
            KeyframeLibrary.TEXT_LETTER_SPACING to listOf(
                Keyframe(time = 0f, value = -200f),
                Keyframe(time = 2f, value = 300f)
            ),
            KeyframeLibrary.TEXT_OPACITY to listOf(
                Keyframe(time = 0f, value = 0f),
                Keyframe(time = 2f, value = 100f)
            )
        )

        assertTrue(KeyframeLibrary.ANIMATABLE_PROPS.containsAll(map.keys))
        assertEquals(-100f, KeyframeStore.sample(map, KeyframeLibrary.TEXT_SIZE, 0f, 36f), 0.001f)
        assertEquals(50f, KeyframeStore.sample(map, KeyframeLibrary.TEXT_SIZE, 1f, 36f), 0.001f)
        assertEquals(50f, KeyframeStore.sample(map, KeyframeLibrary.TEXT_OPACITY, 1f, 100f), 0.001f)
    }

    @Test
    fun layerOpacityKeyframesInterpolateAndClampToPercentRange() {
        val map = mapOf(
            KeyframeLibrary.LAYER_OPACITY to listOf(
                Keyframe(time = 0f, value = 120f),
                Keyframe(time = 2f, value = -20f)
            )
        )

        assertTrue(KeyframeLibrary.ANIMATABLE_PROPS.contains(KeyframeLibrary.LAYER_OPACITY))
        assertEquals(100f, KeyframeStore.layerOpacityAt(map, 65f, 0f), 0.001f)
        assertEquals(50f, KeyframeStore.layerOpacityAt(map, 65f, 1f), 0.001f)
        assertEquals(0f, KeyframeStore.layerOpacityAt(map, 65f, 2f), 0.001f)
    }

    @Test
    fun ffmpegExpressionForManyEasedKeyframesHasLinearSizeAndBalancedSyntax() {
        val keyframes = (0..24).map { index ->
            Keyframe(time = index.toFloat(), value = 50f + index, ease = "easeInOut")
        }

        val expression = KeyframeStore.ffmpegExpression(
            mapOf("x" to keyframes),
            "x",
            baseValue = 50f
        )

        assertTrue(expression.length < 20_000)
        assertEquals(expression.count { it == '(' }, expression.count { it == ')' })
        assertTrue(expression.contains("pow("))
    }
}
