package com.moody.moodyvideoeditor.ui.features

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moody.moodyvideoeditor.data.Keyframe
import com.moody.moodyvideoeditor.data.KeyframeEaseScope
import com.moody.moodyvideoeditor.ui.components.FeaturePanel

@Composable
fun KeyframeEasingPanel(
    clipName: String,
    clipId: String?,
    keyframeMap: Map<String, List<Keyframe>>,
    playheadTimeSec: Float,
    onApply: (String, KeyframeEaseScope, Float) -> Unit,
    onClose: () -> Unit
) {
    FeaturePanel(title = "Keyframe Easing", onClose = onClose) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScrollWithScrollbar(
                    androidx.compose.foundation.rememberScrollState()
                ),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                clipName,
                color = Color(0xFF888888),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
            KeyframeEasePresetsPanel(
                clipId = clipId,
                keyframeMap = keyframeMap,
                playheadTimeSec = playheadTimeSec,
                onApply = onApply
            )
        }
    }
}
