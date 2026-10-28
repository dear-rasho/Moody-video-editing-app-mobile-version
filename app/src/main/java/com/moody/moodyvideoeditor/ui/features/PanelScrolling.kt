package com.moody.moodyvideoeditor.ui.features

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitVerticalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.verticalDrag
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.dp

@Composable
fun Modifier.verticalScrollWithScrollbar(
    state: ScrollState = rememberScrollState()
): Modifier = this
    .verticalScroll(state)
    .drawWithContent {
        drawContent()
        val range = state.maxValue
        if (range > 0) {
            val thickness = 6.dp.toPx()
            val minimumThumb = 32.dp.toPx()
            val thumbHeight = (size.height * size.height / (size.height + range))
                .coerceIn(minimumThumb.coerceAtMost(size.height), size.height)
            val thumbTop = (size.height - thumbHeight) * state.value / range
            val x = size.width - thickness

            drawRoundRect(
                color = Color.White.copy(alpha = 0.14f),
                topLeft = Offset(x, 0f),
                size = androidx.compose.ui.geometry.Size(thickness, size.height),
                cornerRadius = CornerRadius(thickness)
            )
            drawRoundRect(
                color = Color(0xFF9B72FF).copy(alpha = 0.9f),
                topLeft = Offset(x, thumbTop),
                size = androidx.compose.ui.geometry.Size(thickness, thumbHeight),
                cornerRadius = CornerRadius(thickness)
            )
        }
    }
    .pointerInput(state) {
        val hitWidth = 24.dp.toPx()
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            if (down.position.x < size.width - hitWidth || state.maxValue == 0) {
                return@awaitEachGesture
            }

            val touchSlopChange = awaitVerticalTouchSlopOrCancellation(down.id) { change, _ ->
                change.consume()
            }
            if (touchSlopChange != null) {
                verticalDrag(touchSlopChange.id) { change ->
                    val contentHeight = size.height + state.maxValue
                    val scrollScale = contentHeight / size.height
                    state.dispatchRawDelta(change.positionChange().y * scrollScale)
                    change.consume()
                }
            }
        }
    }
    .twoFingerScroll(state, vertical = true)

@Composable
fun Modifier.horizontalScrollWithTwoFinger(
    state: ScrollState = rememberScrollState()
): Modifier = this
    .horizontalScroll(state)
    .twoFingerScroll(state, vertical = false)

private fun Modifier.twoFingerScroll(state: ScrollState, vertical: Boolean): Modifier =
    pointerInput(state, vertical) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            var previousCentroid: Offset? = null
            while (true) {
                val event = awaitPointerEvent()
                val pointers = event.changes.filter { it.pressed }
                if (pointers.isEmpty()) break
                val scrollRange = state.maxValue
                if (pointers.size >= 2 && scrollRange > 0) {
                    val centroid = pointers.fold(Offset.Zero) { sum, change ->
                        sum + change.position
                    } / pointers.size.toFloat()
                    val previous = previousCentroid
                    if (previous != null) {
                        val viewport = if (vertical) size.height else size.width
                        if (viewport > 0) {
                            val contentScale = (viewport + scrollRange) / viewport
                            val delta = if (vertical) {
                                centroid.y - previous.y
                            } else {
                                centroid.x - previous.x
                            }
                            state.dispatchRawDelta(-delta * contentScale)
                            pointers.forEach { it.consume() }
                        }
                    }
                    previousCentroid = centroid
                } else {
                    previousCentroid = null
                }
            }
        }
    }
