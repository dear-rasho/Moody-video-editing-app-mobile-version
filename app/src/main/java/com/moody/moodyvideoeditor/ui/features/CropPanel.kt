package com.moody.moodyvideoeditor.ui.features

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moody.moodyvideoeditor.data.EditorClip
import com.moody.moodyvideoeditor.data.Keyframe
import com.moody.moodyvideoeditor.ui.components.FeaturePanel
import com.moody.moodyvideoeditor.utils.CropHelper
import com.moody.moodyvideoeditor.utils.KeyframeStore

@Composable
fun CropPanel(
    clip: EditorClip?,
    playheadMs: Long,
    hasClipSelected: Boolean,
    onCropChanged: (prop: String, value01: Float) -> Unit,
    onToggleKeyframe: (prop: String) -> Unit,
    onReset: () -> Unit,
    onClose: () -> Unit
) {
    FeaturePanel(title = "Crop", onClose = onClose) {
        if (!hasClipSelected || clip == null) {
            EmptyState()
            return@FeaturePanel
        }


        val currentTimeSec = remember(clip.id, playheadMs, clip.timelineStartMs, clip.durationMs) {
            KeyframeStore.clipLocalTimeSeconds(
                playheadMs, clip.timelineStartMs, clip.durationMs
            )
        }

        // 🔆 Sample crop values at playhead (respects keyframes)
        val sampled = remember(
            clip.id, clip.cropL, clip.cropR, clip.cropT, clip.cropB,
            clip.keyframes, currentTimeSec
        ) {
            com.moody.moodyvideoeditor.utils.TransformApplier
                .resolveLive(clip, currentTimeSec)
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 340.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                "Crop % (0..100) · keyframe each side",
                color = Color(0xFF888888),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
            )

            CropPropRow(
                label = "Left",
                propKey = "cropL",
                value01 = sampled.cropL,
                keyframeMap = clip.keyframes,
                currentTimeSec = currentTimeSec,
                onChanged = { v -> onCropChanged("cropL", v) },
                onToggleKf = { onToggleKeyframe("cropL") }
            )
            CropPropRow(
                label = "Right",
                propKey = "cropR",
                value01 = sampled.cropR,
                keyframeMap = clip.keyframes,
                currentTimeSec = currentTimeSec,
                onChanged = { v -> onCropChanged("cropR", v) },
                onToggleKf = { onToggleKeyframe("cropR") }
            )
            CropPropRow(
                label = "Top",
                propKey = "cropT",
                value01 = sampled.cropT,
                keyframeMap = clip.keyframes,
                currentTimeSec = currentTimeSec,
                onChanged = { v -> onCropChanged("cropT", v) },
                onToggleKf = { onToggleKeyframe("cropT") }
            )
            CropPropRow(
                label = "Bottom",
                propKey = "cropB",
                value01 = sampled.cropB,
                keyframeMap = clip.keyframes,
                currentTimeSec = currentTimeSec,
                onChanged = { v -> onCropChanged("cropB", v) },
                onToggleKf = { onToggleKeyframe("cropB") }
            )

            Spacer(Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFFF6B6B).copy(alpha = 0.15f))
                    .pointerInput(Unit) { detectTapGestures { onReset() } },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "↺ Reset All Crop",
                    color = Color(0xFFFF6B6B),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                "💡 Cropped area is transparent — layers below show through",
                color = Color(0xFF666666),
                fontSize = 9.sp,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp)
            )
        }
    }
}

@Composable
private fun CropPropRow(
    label: String,
    propKey: String,
    value01: Float,
    keyframeMap: Map<String, List<Keyframe>>,
    currentTimeSec: Float,
    onChanged: (Float) -> Unit,
    onToggleKf: () -> Unit
) {
    val hasKfAtTime = KeyframeStore.hasKeyframeAt(keyframeMap, propKey, currentTimeSec)
    val hasAnyKf = KeyframeStore.getKeyframes(keyframeMap, propKey).isNotEmpty()

    val percentValue = (value01 * 100f).coerceIn(0f, 100f)

    var textValue by remember(percentValue) {
        mutableStateOf("%.0f".format(percentValue))
    }
    var isFocused by remember { mutableStateOf(false) }

    LaunchedEffect(percentValue, isFocused) {
        if (!isFocused) textValue = "%.0f".format(percentValue)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF181818))
            .padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(
                    when {
                        hasKfAtTime -> Color(0xFF4F9DFF).copy(alpha = 0.25f)
                        hasAnyKf -> Color(0xFF4F9DFF).copy(alpha = 0.10f)
                        else -> Color.Transparent
                    }
                )
                .pointerInput(propKey) { detectTapGestures { onToggleKf() } },
            contentAlignment = Alignment.Center
        ) {
            Text(
                "◆",
                color = when {
                    hasKfAtTime -> Color(0xFF4F9DFF)
                    hasAnyKf -> Color.White
                    else -> Color.White.copy(alpha = 0.65f)
                },
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Text(
            label,
            color = Color(0xFFCCCCCC),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(56.dp)
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .height(32.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(Color(0xFF0F0F0F))
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                BasicTextField(
                    value = textValue,
                    onValueChange = { input ->
                        val filtered = input.filter { it.isDigit() || it == '.' }
                        textValue = filtered
                        filtered.toFloatOrNull()?.let { parsed ->
                            if (parsed.isFinite()) {
                                val clamped = parsed.coerceIn(0f, 100f)
                                onChanged(clamped / 100f)
                            }
                        }
                    },
                    singleLine = true,
                    textStyle = TextStyle(
                        color = Color(0xFF4F9DFF),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.End
                    ),
                    cursorBrush = SolidColor(Color(0xFF4F9DFF)),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            val parsed = textValue.toFloatOrNull()
                            if (parsed != null && parsed.isFinite()) {
                                val clamped = parsed.coerceIn(0f, 100f)
                                textValue = "%.0f".format(clamped)
                                onChanged(clamped / 100f)
                            }
                        }
                    ),
                    modifier = Modifier
                        .width(64.dp)
                        .onFocusChanged { state ->
                            isFocused = state.isFocused
                            if (!state.isFocused) {
                                val parsed = textValue.toFloatOrNull()
                                if (parsed != null && parsed.isFinite()) {
                                    val clamped = parsed.coerceIn(0f, 100f)
                                    textValue = "%.0f".format(clamped)
                                    onChanged(clamped / 100f)
                                } else {
                                    textValue = "%.0f".format(percentValue)
                                }
                            }
                        }
                )
                Text(
                    "%",
                    color = Color(0xFF888888),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF181818))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text("👆", fontSize = 24.sp)
        Text(
            "No clip selected",
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
        Text("Select a clip first.", color = Color(0xFF888888), fontSize = 10.sp)
    }
}