package com.moody.moodyvideoeditor.ui.features

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moody.moodyvideoeditor.data.MaskLibrary
import com.moody.moodyvideoeditor.data.MaskState
import com.moody.moodyvideoeditor.data.MaskType
import com.moody.moodyvideoeditor.ui.components.FeaturePanel

@Composable
fun MaskPanel(
    current: MaskState,
    hasClipSelected: Boolean,
    hasKeyframeAtPlayhead: Boolean,
    isPenMode: Boolean,
    isHandMode: Boolean,
    onStateChanged: (MaskState) -> Unit,
    onTypeSelected: (MaskType) -> Unit,
    onAddKeyframe: () -> Unit,
    onClearKeyframes: () -> Unit,
    onPenToolToggle: () -> Unit,
    onHandToolToggle: () -> Unit,
    onUndoPoint: () -> Unit,
    onClearPoints: () -> Unit,
    onClosedToggle: () -> Unit,
    onRemove: () -> Unit,
    onClose: () -> Unit
) {
    FeaturePanel(title = "🎭 Mask", onClose = onClose) {

        if (!hasClipSelected) {
            EmptyState()
            return@FeaturePanel
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 320.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {

            Text(
                "Template",
                color = Color(0xFF888888), fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 4.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                MaskLibrary.PRESETS.forEach { preset ->
                    val isActive = current.type == preset.type
                    Column(
                        modifier = Modifier
                            .width(72.dp)
                            .height(64.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (isActive) Color(0xFF2A1F4D) else Color(0xFF181818)
                            )
                            .pointerInput(preset.type) {
                                detectTapGestures { onTypeSelected(preset.type) }
                            }
                            .padding(4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    if (isActive) Color(0xFF7C3AED) else Color(0xFF222222)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(preset.icon, fontSize = 14.sp)
                        }
                        Spacer(Modifier.height(3.dp))
                        Text(
                            preset.label,
                            color = Color.White, fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            if (!current.isActive) {
                Text(
                    "Select a template above",
                    color = Color(0xFF666666), fontSize = 10.sp,
                    modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                )
                return@FeaturePanel
            }

            TextInputRow("Center X", current.centerX, 0f..1f, 0.01f) {
                onStateChanged(current.copy(centerX = it))
            }
            TextInputRow("Center Y", current.centerY, 0f..1f, 0.01f) {
                onStateChanged(current.copy(centerY = it))
            }
            TextInputRow("Rotation", current.rotation, -180f..180f, 1f) {
                onStateChanged(current.copy(rotation = it))
            }

            when (current.type) {
                MaskType.CIRCLE -> {
                    TextInputRow("Radius", current.radius, 0.01f..2f, 0.01f) {
                        onStateChanged(current.copy(radius = it))
                    }
                }

                MaskType.RECTANGLE -> {
                    TextInputRow("Width", current.width, 0.01f..2f, 0.01f) {
                        onStateChanged(current.copy(width = it))
                    }
                    TextInputRow("Height", current.height, 0.01f..2f, 0.01f) {
                        onStateChanged(current.copy(height = it))
                    }
                    TextInputRow("Corner", current.cornerRadius, 0f..0.5f, 0.01f) {
                        onStateChanged(current.copy(cornerRadius = it))
                    }
                }

                MaskType.LINEAR -> {
                    TextInputRow("Position Y", current.positionY, 0f..1f, 0.01f) {
                        onStateChanged(current.copy(positionY = it))
                    }
                }

                MaskType.HEART -> {
                    TextInputRow("Scale", current.scale, 0.3f..2.5f, 0.05f) {
                        onStateChanged(current.copy(scale = it))
                    }
                }

                MaskType.CUSTOM -> {
                    // Status
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            "Points: ${current.customPoints.size}",
                            color = when {
                                current.customClosed -> Color(0xFF22C55E)
                                current.isReadyToClose -> Color(0xFF60EFFF)
                                else -> Color(0xFFFFD166)
                            },
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            when {
                                current.customClosed -> "✅ Closed"
                                current.isReadyToClose -> "Click first point to close"
                                else -> "Need ≥3 points"
                            },
                            color = when {
                                current.customClosed -> Color(0xFF22C55E)
                                current.isReadyToClose -> Color(0xFF60EFFF)
                                else -> Color(0xFFFF6B6B)
                            },
                            fontSize = 9.sp
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(32.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    if (isPenMode) Color(0xFF22C55E) else Color(0xFF7C3AED)
                                )
                                .pointerInput(Unit) {
                                    detectTapGestures { onPenToolToggle() }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                if (isPenMode) "✏️ Pen ON" else "✏️ Enable Pen",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(32.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF181818))
                                .pointerInput(Unit) {
                                    detectTapGestures { onUndoPoint() }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "↶ Undo",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(32.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFFFF6B6B).copy(alpha = 0.15f))
                                .pointerInput(Unit) {
                                    detectTapGestures { onClearPoints() }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "🗑 Clear",
                                color = Color(0xFFFF6B6B),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    TextInputRow(
                        label = "Expand",
                        value = current.expansion,
                        range = -200f..200f,
                        step = 1f
                    ) {
                        onStateChanged(current.copy(expansion = it))
                    }

                    // Reopen path (unclose)
                    if (current.customClosed) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(30.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF181818))
                                .pointerInput(Unit) {
                                    detectTapGestures { onClosedToggle() }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "🔓 Re-open Path",
                                color = Color(0xFFFFD166),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                MaskType.NONE -> {}
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(32.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isHandMode) Color(0xFF22C55E) else Color(0xFF181818))
                        .pointerInput(isHandMode) {
                            detectTapGestures { onHandToolToggle() }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (isHandMode) "✋ Move Mask ON" else "✋ Move Mask",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (current.type != MaskType.CUSTOM) {
                    Spacer(Modifier.weight(1f))
                }
            }

            TextInputRow(
                label = "Feather",
                value = current.feather,
                range = 0f..500f,
                step = 1f
            ) {
                onStateChanged(current.copy(feather = it))
            }

            TextInputRow(
                label = "Opacity",
                value = current.opacity,
                range = 0f..100f,
                step = 1f
            ) {
                onStateChanged(current.copy(opacity = it))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "Invert",
                    color = Color(0xFF888888), fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    modifier = Modifier
                        .height(30.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            if (current.isInverted) Color(0xFF7C3AED)
                            else Color(0xFF181818)
                        )
                        .pointerInput(current.isInverted) {
                            detectTapGestures {
                                onStateChanged(current.copy(isInverted = !current.isInverted))
                            }
                        }
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (current.isInverted) "ON" else "OFF",
                        color = Color.White, fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(34.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (hasKeyframeAtPlayhead) Color(0xFF4F9DFF).copy(alpha = 0.3f)
                            else Color(0xFF181818)
                        )
                        .pointerInput(Unit) { detectTapGestures { onAddKeyframe() } },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "◆ Keyframe",
                        color = if (hasKeyframeAtPlayhead) Color(0xFF4F9DFF) else Color.White,
                        fontSize = 11.sp, fontWeight = FontWeight.Bold
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(34.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF181818))
                        .pointerInput(Unit) { detectTapGestures { onClearKeyframes() } },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Clear KFs (${current.keyframes.size})",
                        color = Color(0xFFAAAAAA), fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFFF6B6B).copy(alpha = 0.15f))
                    .pointerInput(Unit) { detectTapGestures { onRemove() } },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "🗑 Remove Mask",
                    color = Color(0xFFFF6B6B), fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun TextInputRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    step: Float,
    onChange: (Float) -> Unit
) {
    var inputText by remember(value) { mutableStateOf(formatNum(value)) }
    var isFocused by remember { mutableStateOf(false) }

    if (!isFocused && inputText != formatNum(value)) {
        inputText = formatNum(value)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            label,
            color = Color(0xFF888888), fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(64.dp)
        )

        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = {
                inputText = formatNum(it)
                onChange(it)
            },
            valueRange = range,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFF7C3AED),
                activeTrackColor = Color(0xFF7C3AED),
                inactiveTrackColor = Color(0xFF303030)
            )
        )

        Box(
            modifier = Modifier
                .width(64.dp)
                .height(28.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF0F0F0F))
                .padding(horizontal = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            BasicTextField(
                value = inputText,
                onValueChange = { newText ->
                    inputText = newText.filter { it.isDigit() || it == '-' || it == '.' }
                },
                singleLine = true,
                textStyle = TextStyle(
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                ),
                cursorBrush = SolidColor(Color(0xFF7C3AED)),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        inputText.toFloatOrNull()?.let { onChange(it) }
                    }
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { focusState ->
                        isFocused = focusState.isFocused
                        if (!focusState.isFocused) {
                            inputText.toFloatOrNull()?.let { onChange(it) }
                        }
                    }
            )
        }
    }
}

private fun formatNum(v: Float): String {
    return if (kotlin.math.abs(v - v.toInt()) < 0.01f) v.toInt().toString()
    else String.format("%.2f", v)
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