package com.moody.moodyvideoeditor.ui.features

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moody.moodyvideoeditor.data.Keyframe
import com.moody.moodyvideoeditor.data.KeyframeEaseScope
import com.moody.moodyvideoeditor.ui.components.EasingGraphPicker
import kotlin.math.abs

@Composable
fun KeyframeEasePresetsPanel(
    clipId: String?,
    keyframeMap: Map<String, List<Keyframe>>,
    playheadTimeSec: Float,
    onApply: (ease: String, scope: KeyframeEaseScope, timeSec: Float) -> Unit
) {
    var scope by remember(clipId) { mutableStateOf(KeyframeEaseScope.SINGLE_KEYFRAME) }
    val matchingFrames = remember(keyframeMap, playheadTimeSec) {
        keyframeMap.values.mapNotNull { frames ->
            frames.minByOrNull { abs(it.time - playheadTimeSec) }
                ?.takeIf { abs(it.time - playheadTimeSec) <= 0.1f }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(
            "Apply easing to",
            color = Color(0xFFAAAAAA),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(
                KeyframeEaseScope.SINGLE_KEYFRAME to "Single Keyframe",
                KeyframeEaseScope.SELECTED_CLIP to "All in Clip",
                KeyframeEaseScope.ENTIRE_TIMELINE to "Entire Timeline"
            ).forEach { (targetScope, label) ->
                val active = scope == targetScope
                Text(
                    text = label,
                    color = if (active) Color.White else Color(0xFFAAAAAA),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(7.dp))
                        .background(if (active) Color(0xFF5B3DA5) else Color(0xFF181818))
                        .pointerInput(targetScope) {
                            detectTapGestures { scope = targetScope }
                        }
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                )
            }
        }

        if (scope == KeyframeEaseScope.SINGLE_KEYFRAME) {
            Text(
                text = if (matchingFrames.isEmpty()) {
                    "Move the playhead to a keyframe to apply a single-keyframe preset."
                } else {
                    "${matchingFrames.size} keyframe properties near ${"%.2f".format(playheadTimeSec)}s"
                },
                color = if (matchingFrames.isEmpty()) Color(0xFFFFB36B) else Color(0xFF888888),
                fontSize = 9.sp
            )
        }

        EasingGraphPicker(
            currentEase = matchingFrames.firstOrNull()?.ease.orEmpty(),
            onPick = { ease ->
                if (scope != KeyframeEaseScope.SINGLE_KEYFRAME || matchingFrames.isNotEmpty()) {
                    onApply(ease, scope, playheadTimeSec)
                }
            }
        )
    }
}
