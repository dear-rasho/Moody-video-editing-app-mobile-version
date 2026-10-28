package com.moody.moodyvideoeditor

import com.moody.moodyvideoeditor.data.Keyframe
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
