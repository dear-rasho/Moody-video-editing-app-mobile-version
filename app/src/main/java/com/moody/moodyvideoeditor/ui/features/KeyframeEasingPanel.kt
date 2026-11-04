package com.moody.moodyvideoeditor.ui.features

import androidx.compose.runtime.Composable
import com.moody.moodyvideoeditor.data.Keyframe
import com.moody.moodyvideoeditor.ui.components.KeyframeGraphDialog

@Composable
fun KeyframeEasingPanel(
    clipName: String,
    clipId: String?,
    keyframeMap: Map<String, List<Keyframe>>,
    clipDurationSec: Float,
    onUpdateKeyframe: (String, Float, Float, Float) -> Unit,
    onDeleteKeyframe: (String, Float) -> Unit,
    onSetKeyframeEase: (String, Float, String) -> Unit,
    onKeyframeEditStart: () -> Unit,
    onKeyframeEditEnd: () -> Unit,
    onClose: () -> Unit
) {
    KeyframeGraphDialog(
        clipName = clipName,
        clipId = clipId,
        keyframeMap = keyframeMap,
        clipDurationSec = clipDurationSec,
        onUpdateKeyframe = onUpdateKeyframe,
        onDeleteKeyframe = onDeleteKeyframe,
        onSetKeyframeEase = onSetKeyframeEase,
        enableEaseEditing = true,
        inlinePanel = true,
        onKeyframeEditStart = onKeyframeEditStart,
        onKeyframeEditEnd = onKeyframeEditEnd,
        onClose = onClose
    )
}
