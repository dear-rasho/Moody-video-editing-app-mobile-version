package com.moody.moodyvideoeditor

import com.moody.moodyvideoeditor.data.Keyframe
import com.moody.moodyvideoeditor.data.KeyframeLibrary
import com.moody.moodyvideoeditor.utils.KeyframeStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun manualTransformEditDoesNotCreateKeyframeBetweenExistingKeys() {
        val keyframes = mapOf(
            "x" to listOf(
                Keyframe(time = 0f, value = 50f),
                Keyframe(time = 2f, value = 70f)
            )
        )

        val updated = KeyframeStore.updateKeyframeIfPresent(
            keyframes, "x", time = 1f, value = 90f
        )

        assertEquals(keyframes, updated)
    }

    @Test
    fun manualTransformEditUpdatesKeyAtPlayheadWithoutChangingEase() {
        val keyframes = mapOf(
            "x" to listOf(
                Keyframe(time = 0f, value = 50f, ease = "easeIn"),
                Keyframe(time = 2f, value = 70f)
            )
        )

        val updated = KeyframeStore.updateKeyframeIfPresent(
            keyframes, "x", time = 0.04f, value = 90f
        )

        assertEquals(
            listOf(
                Keyframe(time = 0f, value = 90f, ease = "easeIn"),
                Keyframe(time = 2f, value = 70f)
            ),
            updated["x"]
        )
    }

    @Test
    fun setKeyframeAddsKeyBetweenExistingKeysAndPreservesExistingEase() {
        val keyframes = mapOf(
            "x" to listOf(
                Keyframe(time = 0f, value = 50f, ease = "easeIn"),
                Keyframe(time = 2f, value = 70f)
            )
        )

        val updated = KeyframeStore.setKeyframe(keyframes, "x", 1f, 90f)
        val updatedAtExistingKey = KeyframeStore.setKeyframe(updated, "x", 0.04f, 60f)

        assertEquals(
            listOf(
                Keyframe(time = 0f, value = 60f, ease = "easeIn"),
                Keyframe(time = 1f, value = 90f),
                Keyframe(time = 2f, value = 70f)
            ),
            updatedAtExistingKey["x"]
        )
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

    @Test
    fun singleKeyframeEaseAutomaticallyUpdatesEveryPropertyAtPlayhead() {
        val map = mapOf(
            "x" to listOf(
                Keyframe(time = 1f, value = 40f),
                Keyframe(time = 2f, value = 60f)
            ),
            "rotation" to listOf(
                Keyframe(time = 1.04f, value = 20f),
                Keyframe(time = 3f, value = 90f)
            ),
            "anchorX" to listOf(Keyframe(time = 1.3f, value = 50f))
        )

        val updated = KeyframeStore.applyEasePreset(
            map, "easeOut", singleKeyframe = true, timeSec = 1f
        )

        assertEquals("easeOut", updated.getValue("x").first().ease)
        assertEquals("easeOut", updated.getValue("rotation").first().ease)
        assertEquals("linear", updated.getValue("anchorX").first().ease)
        assertEquals("linear", updated.getValue("x").last().ease)
    }

    @Test
    fun clipEasePresetUpdatesEveryKeyframeOnEveryProperty() {
        val map = mapOf(
            "x" to listOf(Keyframe(time = 0f, value = 40f), Keyframe(time = 2f, value = 60f)),
            "rotation" to listOf(Keyframe(time = 1f, value = 90f))
        )

        val updated = KeyframeStore.applyEasePreset(
            map, "cubicInOut", singleKeyframe = false, timeSec = 0f
        )

        assertTrue(updated.values.flatten().all { it.ease == "cubicInOut" })
    }

    @Test
    fun ffmpegExpressionSupportsEveryEasingPresetWithoutLinearFallback() {
        val map = mapOf(
            "x" to listOf(
                Keyframe(time = 0f, value = 0f),
                Keyframe(time = 1f, value = 100f)
            )
        )
        val linear = KeyframeStore.ffmpegExpression(
            mapOf("x" to map.getValue("x").map { it.copy(ease = "linear") }),
            "x",
            0f
        )

        KeyframeLibrary.EASING_OPTIONS.filterNot { it == "linear" }.forEach { ease ->
            val expression = KeyframeStore.ffmpegExpression(
                mapOf("x" to map.getValue("x").map { it.copy(ease = ease) }),
                "x",
                0f
            )
            assertFalse("$ease fell back to linear", expression == linear)
            assertEquals(
                "$ease has unbalanced expression syntax",
                expression.count { it == '(' },
                expression.count { it == ')' }
            )
        }

        val circular = KeyframeStore.ffmpegExpression(
            mapOf("x" to map.getValue("x").map { it.copy(ease = "circInOut") }),
            "x",
            0f
        )
        assertTrue(circular.contains("sqrt(max(0,"))
    }

    @Test
    fun bounceEaseUsesCompactFfmpegExpression() {
        val expression = KeyframeStore.ffmpegExpression(
            mapOf(
                "scaleX" to listOf(
                    Keyframe(time = 0.263f, value = 89.65f, ease = "bounceOut"),
                    Keyframe(time = 2.344f, value = 32.788f),
                    Keyframe(time = 4.425f, value = 66.92f)
                )
            ),
            "scaleX",
            100f
        )

        assertTrue(expression.contains("st(0,clip("))
        assertTrue(expression.contains("ld(0)"))
        assertTrue(expression.length < 2_000)
        assertEquals(expression.count { it == '(' }, expression.count { it == ')' })
    }

    @Test
    fun ffmpegFilterExpressionEscapesCommasWithoutDoubleEscaping() {
        val expression = "min(max(t,0),1)"

        assertEquals(
            "min(max(t\\,0)\\,1)",
            KeyframeStore.escapeFfmpegExpression(expression)
        )
        assertEquals(
            "min(max(t\\,0)\\,1)",
            KeyframeStore.escapeFfmpegExpression("min(max(t\\,0)\\,1)")
        )
    }
}
