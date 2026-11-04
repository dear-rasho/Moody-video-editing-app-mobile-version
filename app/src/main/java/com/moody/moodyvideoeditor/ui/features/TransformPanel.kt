package com.moody.moodyvideoeditor.ui.features

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moody.moodyvideoeditor.data.Keyframe
import com.moody.moodyvideoeditor.ui.components.FeaturePanel
import com.moody.moodyvideoeditor.data.KeyframeEaseScope
import com.moody.moodyvideoeditor.utils.KeyframeStore
import com.moody.moodyvideoeditor.utils.TransformValues
import kotlin.math.abs

@Composable
fun TransformPanel(
    clipName: String,
    clipId: String? = null,
    hasClipSelected: Boolean,
    base: TransformValues,
    keyframeMap: Map<String, List<Keyframe>>,
    currentTimeSec: Float,
    onApplyKeyframeEase: (String, KeyframeEaseScope, Float) -> Unit = { _, _, _ -> },
    onPropertyChanged: (String, Float) -> Unit,
    onToggleKeyframe: (String) -> Unit,
    onResetAll: () -> Unit,
    onClose: () -> Unit
) {
    var uniformScale by remember(clipId) { mutableStateOf(true) }
    val focusManager = LocalFocusManager.current

    FeaturePanel(title = "🔲 Transform", onClose = onClose) {

        if (!hasClipSelected) {
            EmptyState()
            return@FeaturePanel
        }

        val hasAnyKf = KeyframeStore.hasAnyKeyframes(keyframeMap)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScrollWithScrollbar(rememberScrollState())
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.type ==
                                androidx.compose.ui.input.pointer.PointerEventType.Press
                            ) {
                                focusManager.clearFocus()
                            }
                        }
                    }
                }
        ) {


            Spacer(Modifier.height(8.dp))


            //  TRANSFORM PROPERTIES

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 180.dp)
                    .verticalScrollWithScrollbar(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                SectionLabel("Position")
                PropRow(
                    "X", "x", base.x, 1f, keyframeMap, currentTimeSec,
                    onChanged = { onPropertyChanged("x", it) },
                    onToggleKf = { onToggleKeyframe("x") })
                PropRow(
                    "Y", "y", base.y, 1f, keyframeMap, currentTimeSec,
                    onChanged = { onPropertyChanged("y", it) },
                    onToggleKf = { onToggleKeyframe("y") })

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(24.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .clickable {
                            val enableUniformScale = !uniformScale
                            uniformScale = enableUniformScale
                            if (enableUniformScale) {
                                onPropertyChanged("scale", (base.scaleX + base.scaleY) / 2f)
                            }
                        }
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(15.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(
                                if (uniformScale) Color(0xFF4F9DFF)
                                else Color(0xFF181818)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (uniformScale) {
                            Text("✓", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Text(
                        "Uniform Scale",
                        color = Color(0xFFCCCCCC),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (uniformScale) {
                    val uniformValue = (base.scaleX + base.scaleY) / 2f
                    PropRow(
                        "Scale %", "scale", uniformValue, 1f, keyframeMap, currentTimeSec,
                        onChanged = { onPropertyChanged("scale", it) },
                        onToggleKf = { onToggleKeyframe("scale") })
                } else {
                    PropRow(
                        "Width %", "scaleX", base.scaleX, 1f, keyframeMap, currentTimeSec,
                        onChanged = { onPropertyChanged("scaleX", it) },
                        onToggleKf = { onToggleKeyframe("scaleX") })
                    PropRow(
                        "Height %", "scaleY", base.scaleY, 1f, keyframeMap, currentTimeSec,
                        onChanged = { onPropertyChanged("scaleY", it) },
                        onToggleKf = { onToggleKeyframe("scaleY") })
                }

                SectionLabel("Rotation")
                PropRow(
                    "Angle °",
                    "rotation",
                    base.rotation,
                    1f,
                    keyframeMap,
                    currentTimeSec,
                    onChanged = { onPropertyChanged("rotation", it) },
                    onToggleKf = { onToggleKeyframe("rotation") })

                SectionLabel("Anchor")
                PropRow(
                    "Anchor X", "anchorX", base.anchorX, 1f, keyframeMap, currentTimeSec,
                    onChanged = { onPropertyChanged("anchorX", it) },
                    onToggleKf = { onToggleKeyframe("anchorX") })
                PropRow(
                    "Anchor Y", "anchorY", base.anchorY, 1f, keyframeMap, currentTimeSec,
                    onChanged = { onPropertyChanged("anchorY", it) },
                    onToggleKf = { onToggleKeyframe("anchorY") })
            }

            Spacer(Modifier.height(8.dp))


            if (hasAnyKf) {
                KeyframeEasePresetsPanel(
                    clipId = clipId,
                    keyframeMap = keyframeMap,
                    playheadTimeSec = currentTimeSec,
                    onApply = onApplyKeyframeEase
                )
            }

            Spacer(Modifier.height(6.dp))


            //  RESET

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFFFF6B6B).copy(alpha = 0.15f))
                    .pointerInput(Unit) {
                        detectTapGestures {
                            focusManager.clearFocus()
                            onResetAll()
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "↺ Reset All",
                    color = Color(0xFFFF6B6B), fontSize = 10.sp, fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(), color = Color(0xFF4F9DFF), fontSize = 8.sp,
        fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp, top = 2.dp)
    )
}

@Composable
private fun PropRow(
    label: String,
    propKey: String,
    value: Float,
    step: Float,
    keyframeMap: Map<String, List<Keyframe>>,
    currentTimeSec: Float,
    onChanged: (Float) -> Unit,
    onToggleKf: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    val hasKfAtTime = KeyframeStore.hasKeyframeAt(keyframeMap, propKey, currentTimeSec)
    val hasAnyKf = KeyframeStore.getKeyframes(keyframeMap, propKey).isNotEmpty()
    val valueRange = when (propKey) {
        "x", "y", "anchorX", "anchorY" -> -100f..200f
        "scale", "scaleX", "scaleY" -> 0f..500f
        "rotation" -> -360f..360f
        else -> -100f..200f
    }

    var inputText by remember(value) { mutableStateOf(formatNum(value)) }
    var pendingText by remember(value) { mutableStateOf("") }
    var hasFocus by remember { mutableStateOf(false) }

    if (!hasFocus && inputText != formatNum(value)) {
        inputText = formatNum(value)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF181818))
            .padding(horizontal = 3.dp, vertical = 2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(
                        when {
                            hasKfAtTime -> Color(0xFF4F9DFF).copy(alpha = 0.25f)
                            hasAnyKf -> Color(0xFF4F9DFF).copy(alpha = 0.10f)
                            else -> Color.Transparent
                        }
                    )
                    .clickable {
                        focusManager.clearFocus()
                        onToggleKf()
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "◆",
                    color = when {
                        hasKfAtTime -> Color(0xFF4F9DFF)
                        hasAnyKf -> Color.White
                        else -> Color.White.copy(alpha = 0.65f)
                    },
                    fontSize = 12.sp, fontWeight = FontWeight.Bold
                )
            }

            Text(
                label, color = Color(0xFFCCCCCC), fontSize = 10.sp,
                fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.width(52.dp)
            )

            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(Color(0xFF2A1414))
                    .clickable {
                        focusManager.clearFocus()
                        val nv = value - step
                        inputText = formatNum(nv)
                        onChanged(nv)
                    },
                contentAlignment = Alignment.Center
            ) {
                Text("−", color = Color(0xFFFF6B6B), fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }

            Box(
                modifier = Modifier
                    .width(68.dp)
                    .height(26.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(Color(0xFF0F0F0F))
                    .padding(horizontal = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                BasicTextField(
                    value = inputText,
                    onValueChange = { newText ->
                        val filtered = newText.filter { it.isDigit() || it == '-' || it == '.' }
                        inputText = filtered
                        pendingText = filtered
                    },
                    singleLine = true,
                    textStyle = TextStyle(
                        color = Color(0xFF4F9DFF),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    ),
                    cursorBrush = SolidColor(Color(0xFF4F9DFF)),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            val parsed = pendingText.toFloatOrNull()
                            if (parsed != null && parsed.isFinite()) {
                                inputText = formatNum(parsed)
                                onChanged(parsed)
                            }
                            pendingText = ""
                        }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { focusState ->
                            hasFocus = focusState.isFocused
                            if (!focusState.isFocused && pendingText.isNotEmpty()) {
                                val parsed = pendingText.toFloatOrNull()
                                if (parsed != null && parsed.isFinite()) {
                                    inputText = formatNum(parsed)
                                    onChanged(parsed)
                                }
                                pendingText = ""
                            }
                        }
                )
            }

            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(Color(0xFF0F2A1A))
                    .clickable {
                        focusManager.clearFocus()
                        val nv = value + step
                        inputText = formatNum(nv)
                        onChanged(nv)
                    },
                contentAlignment = Alignment.Center
            ) {
                Text("+", color = Color(0xFF22C55E), fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }

        Slider(
            value = value.coerceIn(valueRange),
            onValueChange = { newValue ->
                val steppedValue = (kotlin.math.round(newValue / step) * step)
                    .coerceIn(valueRange)
                inputText = formatNum(steppedValue)
                pendingText = ""
                onChanged(steppedValue)
            },
            valueRange = valueRange,
            onValueChangeFinished = { focusManager.clearFocus() },
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp),
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFF4F9DFF),
                activeTrackColor = Color(0xFF4F9DFF),
                inactiveTrackColor = Color(0xFF383838)
            )
        )
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
        Text(
            "Select a video or text layer first.",
            color = Color(0xFF888888), fontSize = 10.sp
        )
    }
}

private fun formatNum(v: Float): String {
    val r = Math.round(v * 10) / 10f
    return if (abs(r - r.toInt()) < 0.01f) r.toInt().toString() else r.toString()
}