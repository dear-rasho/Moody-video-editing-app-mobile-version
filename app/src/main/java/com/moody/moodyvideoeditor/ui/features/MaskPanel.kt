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
import com.moody.moodyvideoeditor.data.MaskAnimatableProps
import com.moody.moodyvideoeditor.data.MaskLibrary
import com.moody.moodyvideoeditor.data.MaskState
import com.moody.moodyvideoeditor.data.MaskType
import com.moody.moodyvideoeditor.ui.components.FeaturePanel
import com.moody.moodyvideoeditor.viewmodel.EditorViewModel

@Composable
fun MaskPanel(
    current: MaskState,
    hasClipSelected: Boolean,
    hasKeyframeAtPlayhead: Boolean,
    isPenMode: Boolean,
    isHandMode: Boolean,
    viewModel: EditorViewModel,
    onTypeSelected: (MaskType) -> Unit,
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
                .heightIn(max = 380.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {

            // ═══════════════════════════════════════════════════════
            //  TEMPLATE PICKER
            // ═══════════════════════════════════════════════════════
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

            // ═══════════════════════════════════════════════════════
            //  COMMON PROPERTIES
            // ═══════════════════════════════════════════════════════
            Text(
                "Common Properties",
                color = Color(0xFF7C3AED), fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp)
            )

            MaskPropertyRow(
                label = "Position X",
                prop = "positionX",
                value = current.positionX,
                range = MaskAnimatableProps.rangeFor("positionX"),
                decimals = 2,
                currentMask = current,
                viewModel = viewModel
            )

            MaskPropertyRow(
                label = "Position Y",
                prop = "positionY",
                value = current.positionY,
                range = MaskAnimatableProps.rangeFor("positionY"),
                decimals = 2,
                currentMask = current,
                viewModel = viewModel
            )

            MaskPropertyRow(
                label = "Rotation",
                prop = "rotation",
                value = current.rotation,
                range = MaskAnimatableProps.rangeFor("rotation"),
                decimals = 1,
                currentMask = current,
                viewModel = viewModel
            )

            // ═══════════════════════════════════════════════════════
            //  TYPE-SPECIFIC PROPERTIES
            // ═══════════════════════════════════════════════════════

            when (current.type) {
                MaskType.CIRCLE -> {
                    Text(
                        "Circle",
                        color = Color(0xFF7C3AED), fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                    )
                    MaskPropertyRow(
                        label = "Radius",
                        prop = "radius",
                        value = current.radius,
                        range = MaskAnimatableProps.rangeFor("radius"),
                        decimals = 2,
                        currentMask = current,
                        viewModel = viewModel
                    )
                }

                MaskType.RECTANGLE -> {
                    Text(
                        "Rectangle",
                        color = Color(0xFF7C3AED), fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                    )
                    MaskPropertyRow(
                        label = "Width",
                        prop = "width",
                        value = current.width,
                        range = MaskAnimatableProps.rangeFor("width"),
                        decimals = 2,
                        currentMask = current,
                        viewModel = viewModel
                    )
                    MaskPropertyRow(
                        label = "Height",
                        prop = "height",
                        value = current.height,
                        range = MaskAnimatableProps.rangeFor("height"),
                        decimals = 2,
                        currentMask = current,
                        viewModel = viewModel
                    )
                    MaskPropertyRow(
                        label = "Corner",
                        prop = "cornerRadius",
                        value = current.cornerRadius,
                        range = MaskAnimatableProps.rangeFor("cornerRadius"),
                        decimals = 3,
                        currentMask = current,
                        viewModel = viewModel
                    )
                }

                MaskType.LINEAR -> {
                    Text(
                        "Linear",
                        color = Color(0xFF7C3AED), fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                    )
                    MaskPropertyRow(
                        label = "Line Position",
                        prop = "linearPosition",
                        value = current.linearPosition,
                        range = MaskAnimatableProps.rangeFor("linearPosition"),
                        decimals = 2,
                        currentMask = current,
                        viewModel = viewModel
                    )
                }

                MaskType.HEART -> {
                    Text(
                        "Heart",
                        color = Color(0xFF7C3AED), fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                    )
                    MaskPropertyRow(
                        label = "Scale",
                        prop = "scale",
                        value = current.scale,
                        range = MaskAnimatableProps.rangeFor("scale"),
                        decimals = 2,
                        currentMask = current,
                        viewModel = viewModel
                    )
                }

                MaskType.CUSTOM -> {
                    CustomSection(
                        current = current,
                        isPenMode = isPenMode,
                        viewModel = viewModel,
                        onPenToolToggle = onPenToolToggle,
                        onUndoPoint = onUndoPoint,
                        onClearPoints = onClearPoints,
                        onClosedToggle = onClosedToggle
                    )
                }

                MaskType.NONE -> {}
            }

            // ═══════════════════════════════════════════════════════
            //  EXPANSION
            // ═══════════════════════════════════════════════════════
            Text(
                "Shape",
                color = Color(0xFF7C3AED), fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp)
            )

            MaskPropertyRow(
                label = "Expand",
                prop = "expansion",
                value = current.expansion,
                range = MaskAnimatableProps.rangeFor("expansion"),
                decimals = 0,
                currentMask = current,
                viewModel = viewModel
            )

            // ═══════════════════════════════════════════════════════
            //  HAND MODE
            // ═══════════════════════════════════════════════════════
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
                            if (isHandMode) Color(0xFF22C55E) else Color(0xFF181818)
                        )
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
            }

            // ═══════════════════════════════════════════════════════
            //  BLENDING
            // ═══════════════════════════════════════════════════════
            Text(
                "Blending",
                color = Color(0xFF7C3AED), fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp)
            )

            MaskPropertyRow(
                label = "Feather",
                prop = "feather",
                value = current.feather,
                range = MaskAnimatableProps.rangeFor("feather"),
                decimals = 0,
                currentMask = current,
                viewModel = viewModel
            )

            MaskPropertyRow(
                label = "Opacity",
                prop = "opacity",
                value = current.opacity,
                range = MaskAnimatableProps.rangeFor("opacity"),
                decimals = 0,
                currentMask = current,
                viewModel = viewModel
            )

            // ═══════════════════════════════════════════════════════
            //  INVERT
            // ═══════════════════════════════════════════════════════
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "Invert",
                    color = Color(0xFF888888), fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(64.dp)
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
                                viewModel.updateMask(
                                    current.copy(isInverted = !current.isInverted)
                                )
                            }
                        }
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (current.isInverted) "ON (hide inside)"
                        else "OFF (hide outside)",
                        color = Color.White, fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // ═══════════════════════════════════════════════════════
            //  KEYFRAME STATUS
            // ═══════════════════════════════════════════════════════
            val totalKfs = current.propertyKeyframes.values.sumOf { it.size }
            if (totalKfs > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF1F1438))
                        .padding(8.dp)
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            "◆ $totalKfs keyframes across properties",
                            color = Color(0xFFA78BFA),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                        current.propertyKeyframes.forEach { (prop, kfs) ->
                            if (kfs.isNotEmpty()) {
                                Text(
                                    "  • $prop: ${kfs.size} kf",
                                    color = Color(0xFF888888),
                                    fontSize = 9.sp
                                )
                            }
                        }
                    }
                }
            }

            // ═══════════════════════════════════════════════════════
            //  ACTION BUTTONS
            // ═══════════════════════════════════════════════════════
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
                        .background(Color(0xFF181818))
                        .pointerInput(Unit) {
                            detectTapGestures {
                                viewModel.clearMaskKeyframes()
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Clear All ◆ ($totalKfs)",
                        color = Color(0xFFAAAAAA), fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(34.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFFF6B6B).copy(alpha = 0.15f))
                        .pointerInput(Unit) { detectTapGestures { onRemove() } },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "🗑 Remove",
                        color = Color(0xFFFF6B6B), fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  CUSTOM MASK SECTION
// ═══════════════════════════════════════════════════════════════

@Composable
private fun CustomSection(
    current: MaskState,
    isPenMode: Boolean,
    viewModel: EditorViewModel,
    onPenToolToggle: () -> Unit,
    onUndoPoint: () -> Unit,
    onClearPoints: () -> Unit,
    onClosedToggle: () -> Unit
) {
    Text(
        "Custom Path",
        color = Color(0xFF7C3AED), fontSize = 9.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp)
    )

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
                current.isReadyToClose -> "Tap a node to close"
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

    MaskPropertyRow(
        label = "Corner",
        prop = "cornerRadius",
        value = current.cornerRadius,
        range = MaskAnimatableProps.rangeFor("cornerRadius"),
        decimals = 3,
        currentMask = current,
        viewModel = viewModel
    )

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

// ═══════════════════════════════════════════════════════════════
//  MASK PROPERTY ROW
// ═══════════════════════════════════════════════════════════════

@Composable
private fun MaskPropertyRow(
    label: String,
    prop: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    decimals: Int,
    currentMask: MaskState,
    viewModel: EditorViewModel
) {
    val isUnboundedPosition = prop == "positionX" || prop == "positionY"
    val kfs = currentMask.propertyKeyframes[prop] ?: emptyList()
    val hasKf = viewModel.hasMaskPropertyKeyframeAtPlayhead(prop)
    val hasAnyKf = kfs.isNotEmpty()

    var inputText by remember(value) {
        mutableStateOf(formatNum(value, decimals))
    }
    var isFocused by remember { mutableStateOf(false) }

    if (!isFocused && inputText != formatNum(value, decimals)) {
        inputText = formatNum(value, decimals)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(
                    when {
                        hasKf -> Color(0xFF4F9DFF).copy(alpha = 0.25f)
                        hasAnyKf -> Color(0xFF4F9DFF).copy(alpha = 0.10f)
                        else -> Color(0xFF181818)
                    }
                )
                .pointerInput(prop) {
                    detectTapGestures {
                        viewModel.toggleMaskPropertyKeyframe(prop)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                "◆",
                color = when {
                    hasKf -> Color(0xFF4F9DFF)
                    hasAnyKf -> Color.White
                    else -> Color.White.copy(alpha = 0.65f)
                },
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Text(
            label,
            color = Color(0xFF888888),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            modifier = Modifier.width(60.dp)
        )

        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = {
                viewModel.updateMaskProperty(prop, it)
                inputText = formatNum(it, decimals)
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
                .padding(horizontal = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            BasicTextField(
                value = inputText,
                onValueChange = { newText ->
                    val filtered = newText.filter {
                        it.isDigit() || it == '-' || it == '.' || it == '+'
                    }
                    inputText = filtered
                    filtered.toFloatOrNull()
                        ?.takeIf { it.isFinite() }
                        ?.let { v ->
                            val accepted = if (isUnboundedPosition) v
                            else v.coerceIn(range.start, range.endInclusive)
                            viewModel.updateMaskProperty(prop, accepted)
                        }
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
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        inputText.toFloatOrNull()
                            ?.takeIf { it.isFinite() }
                            ?.let { v ->
                                val clamped = v.coerceIn(range.start, range.endInclusive)
                                viewModel.updateMaskProperty(prop, clamped)
                            }
                    }
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { state ->
                        isFocused = state.isFocused
                        if (!state.isFocused) {
                            inputText.toFloatOrNull()
                                ?.takeIf { it.isFinite() }
                                ?.let { v ->
                                    val clamped = v.coerceIn(range.start, range.endInclusive)
                                    viewModel.updateMaskProperty(prop, clamped)
                                }
                        }
                    }
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  HELPERS
// ═══════════════════════════════════════════════════════════════

private fun formatNum(v: Float, decimals: Int): String {
    return if (decimals <= 0) v.toInt().toString()
    else String.format("%.${decimals}f", v)
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