package com.moody.moodyvideoeditor.ui.features

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moody.moodyvideoeditor.data.Keyframe
import com.moody.moodyvideoeditor.ui.components.EasingGraphPicker
import com.moody.moodyvideoeditor.ui.components.FeaturePanel
import com.moody.moodyvideoeditor.utils.KeyframeStore

@Composable
fun KeyframeEasingPanel(
    clipId: String?,
    keyframeMap: Map<String, List<Keyframe>>,
    playheadTimeSec: Float,
    hasTimelineKeyframes: Boolean,
    onApplySelected: (String) -> Unit,
    onApplyTimeline: (String) -> Unit,
    onClose: () -> Unit
) {
    var selectedEase by remember { mutableStateOf(Keyframe.DEFAULT_EASE) }
    var applyAll by remember { mutableStateOf(false) }

    val hasSelectedFrame = KeyframeStore.hasAnyKeyframeAt(keyframeMap, playheadTimeSec)

    FeaturePanel(title = "Keyframe Easing", onClose = onClose) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                when {
                    applyAll && hasTimelineKeyframes ->
                        "Selected graph: $selectedEase · applies to all timeline keyframes"
                    hasSelectedFrame ->
                        "Keyframe selected at ${"%.2f".format(playheadTimeSec)}s"
                    else -> "Move the playhead onto a keyframe to use Single mode."
                },
                color = Color(0xFFBDBDBD),
                fontSize = 10.sp,
                modifier = Modifier.padding(horizontal = 4.dp)
            )

            Text(
                "Apply easing to",
                color = Color(0xFF888888),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ScopeButton(
                    label = "Single keyframe",
                    selected = !applyAll,
                    enabled = clipId != null
                ) {
                    applyAll = false
                }
                ScopeButton(
                    label = "All timeline",
                    selected = applyAll,
                    enabled = hasTimelineKeyframes
                ) {
                    applyAll = true
                }
            }

            Text(
                "Choose graph · swipe left/right",
                color = Color(0xFF888888),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
            EasingGraphPicker(currentEase = selectedEase) { ease ->
                selectedEase = ease
                if (applyAll) {
                    onApplyTimeline(ease)
                } else if (hasSelectedFrame) {
                    onApplySelected(ease)
                }
            }

            if (!applyAll && !hasSelectedFrame) {
                Text(
                    "Tap a keyframe marker on the timeline to move the playhead onto it.",
                    color = Color(0xFF777777),
                    fontSize = 9.sp,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.ScopeButton(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .weight(1f)
            .height(34.dp),
        shape = RoundedCornerShape(7.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = 5.dp,
            vertical = 2.dp
        ),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) Color(0xFF1689FF) else Color(0xFF34304A),
            contentColor = Color.White,
            disabledContainerColor = Color(0xFF202020),
            disabledContentColor = Color(0xFF666666)
        )
    ) {
        Text(label, fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
}
