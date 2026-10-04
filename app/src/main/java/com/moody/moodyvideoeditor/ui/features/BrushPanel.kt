package com.moody.moodyvideoeditor.ui.features

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moody.moodyvideoeditor.data.BrushGradient
import com.moody.moodyvideoeditor.data.BrushLibrary
import com.moody.moodyvideoeditor.data.BrushType
import com.moody.moodyvideoeditor.ui.components.ColorPickerField
import com.moody.moodyvideoeditor.ui.components.FeaturePanel

@Composable
fun BrushPanel(
    isDrawing: Boolean,
    activeType: BrushType,
    activeColor: Long,
    activeWidth: Float,
    activeOpacity: Float,
    activeGradient: BrushGradient,
    strokeCount: Int,
    hasBrushLayer: Boolean,
    onToggleDrawing: () -> Unit,
    onTypeChanged: (BrushType) -> Unit,
    onColorChanged: (Long) -> Unit,
    onWidthChanged: (Float) -> Unit,
    onOpacityChanged: (Float) -> Unit,
    onGradientChanged: (BrushGradient) -> Unit,
    onUndoStroke: () -> Unit,
    onClearStrokes: () -> Unit,
    onCreateLayer: () -> Unit,
    onClose: () -> Unit
) {
    var showGradientPanel by remember { mutableStateOf(false) }

    FeaturePanel(title = "🖌️ Brush", onClose = onClose) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 320.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF181818))
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    if (hasBrushLayer) "✅ Brush layer active"
                    else "⚪ No brush layer",
                    color = if (hasBrushLayer) Color(0xFF22C55E) else Color(0xFF888888),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                if (!hasBrushLayer) {
                    Box(
                        modifier = Modifier
                            .height(28.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF7C3AED))
                            .pointerInput(Unit) {
                                detectTapGestures { onCreateLayer() }
                            }
                            .padding(horizontal = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Create",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        when {
                            !hasBrushLayer -> Color(0xFF333333)
                            isDrawing -> Color(0xFF22C55E)
                            else -> Color(0xFF7C3AED)
                        }
                    )
                    .pointerInput(isDrawing, hasBrushLayer) {
                        detectTapGestures {
                            if (!hasBrushLayer) onCreateLayer()
                            else onToggleDrawing()
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    when {
                        !hasBrushLayer -> "Create layer first"
                        isDrawing -> "✏️ Drawing ON  ·  Tap to stop"
                        else -> "✏️ Start Drawing"
                    },
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                "Brush Type",
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
                BrushLibrary.PRESETS.forEach { preset ->
                    val isActive = activeType == preset.type
                    Column(
                        modifier = Modifier
                            .width(68.dp)
                            .height(60.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (isActive) Color(0xFF2A1F4D) else Color(0xFF181818)
                            )
                            .then(
                                if (isActive) Modifier.border(
                                    1.dp, Color(0xFF7C3AED),
                                    RoundedCornerShape(10.dp)
                                ) else Modifier
                            )
                            .pointerInput(preset.type) {
                                detectTapGestures { onTypeChanged(preset.type) }
                            }
                            .padding(4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(RoundedCornerShape(13.dp))
                                .background(
                                    if (isActive) Color(0xFF7C3AED)
                                    else Color(0xFF222222)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(preset.icon, fontSize = 13.sp)
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(
                            preset.label,
                            color = Color.White, fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Color Mode Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "Color Mode",
                    color = Color(0xFF888888), fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    modifier = Modifier
                        .height(28.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            if (!activeGradient.enabled) Color(0xFF7C3AED)
                            else Color(0xFF181818)
                        )
                        .pointerInput(Unit) {
                            detectTapGestures {
                                onGradientChanged(activeGradient.copy(enabled = false))
                            }
                        }
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Solid",
                        color = Color.White, fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Box(
                    modifier = Modifier
                        .height(28.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            if (activeGradient.enabled) Color(0xFF7C3AED)
                            else Color(0xFF181818)
                        )
                        .pointerInput(Unit) {
                            detectTapGestures {
                                onGradientChanged(activeGradient.copy(enabled = true))
                            }
                        }
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Gradient",
                        color = Color.White, fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Solid Color Palette (only when NOT gradient)
            if (!activeGradient.enabled) {
                Text(
                    "Color",
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
                    BrushLibrary.COLOR_PRESETS.forEach { c ->
                        val isActive = activeColor == c
                        Box(
                            modifier = Modifier
                                .size(if (isActive) 34.dp else 30.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(c))
                                .then(
                                    if (isActive) Modifier.border(
                                        2.dp, Color.White,
                                        RoundedCornerShape(8.dp)
                                    ) else Modifier
                                )
                                .pointerInput(c) {
                                    detectTapGestures { onColorChanged(c) }
                                }
                        )
                    }
                }
            } else {
                // Gradient Configuration
                Text(
                    "Gradient Ramp",
                    color = Color(0xFF888888), fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 4.dp)
                )

                // Preview bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(24.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            if (activeGradient.hasMid) {
                                Brush.linearGradient(
                                    colors = listOf(
                                        Color(activeGradient.color1),
                                        Color(activeGradient.color3),
                                        Color(activeGradient.color2)
                                    )
                                )
                            } else {
                                Brush.linearGradient(
                                    colors = listOf(
                                        Color(activeGradient.color1),
                                        Color(activeGradient.color2)
                                    )
                                )
                            }
                        )
                )

                ColorPickerField(
                    label = "Start",
                    colorLong = activeGradient.color1,
                    onChange = {
                        onGradientChanged(activeGradient.copy(color1 = it))
                    }
                )

                ColorPickerField(
                    label = "End",
                    colorLong = activeGradient.color2,
                    onChange = {
                        onGradientChanged(activeGradient.copy(color2 = it))
                    }
                )

                // Mid-stop toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Mid Stop",
                        color = Color(0xFF888888), fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    Box(
                        modifier = Modifier
                            .height(28.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (activeGradient.hasMid) Color(0xFF7C3AED)
                                else Color(0xFF181818)
                            )
                            .pointerInput(activeGradient.hasMid) {
                                detectTapGestures {
                                    onGradientChanged(
                                        activeGradient.copy(hasMid = !activeGradient.hasMid)
                                    )
                                }
                            }
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (activeGradient.hasMid) "ON" else "OFF",
                            color = Color.White, fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (activeGradient.hasMid) {
                    ColorPickerField(
                        label = "Mid",
                        colorLong = activeGradient.color3,
                        onChange = {
                            onGradientChanged(activeGradient.copy(color3 = it))
                        }
                    )
                }

                // Direction toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Direction",
                        color = Color(0xFF888888), fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    Box(
                        modifier = Modifier
                            .height(28.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (activeGradient.mode == "linear") Color(0xFF7C3AED)
                                else Color(0xFF181818)
                            )
                            .pointerInput(Unit) {
                                detectTapGestures {
                                    onGradientChanged(
                                        activeGradient.copy(mode = "linear")
                                    )
                                }
                            }
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Forward",
                            color = Color.White, fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Box(
                        modifier = Modifier
                            .height(28.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (activeGradient.mode == "reverse") Color(0xFF7C3AED)
                                else Color(0xFF181818)
                            )
                            .pointerInput(Unit) {
                                detectTapGestures {
                                    onGradientChanged(
                                        activeGradient.copy(mode = "reverse")
                                    )
                                }
                            }
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Reverse",
                            color = Color.White, fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            SliderRow(
                label = "Width",
                value = activeWidth,
                range = 2f..100f,
                displayFormat = "%.0f",
                onChange = onWidthChanged
            )

            SliderRow(
                label = "Opacity",
                value = activeOpacity * 100f,
                range = 10f..100f,
                displayFormat = "%.0f%%",
                onChange = { onOpacityChanged(it / 100f) }
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF181818))
                        .pointerInput(strokeCount) {
                            detectTapGestures { onUndoStroke() }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "↶ Undo ($strokeCount)",
                        color = Color.White, fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFFF6B6B).copy(alpha = 0.15f))
                        .pointerInput(strokeCount) {
                            detectTapGestures { onClearStrokes() }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "🗑 Clear All",
                        color = Color(0xFFFF6B6B), fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(Modifier.height(4.dp))
            Text(
                "💡 Brush layer supports: drag on-screen, keyframes, gradients, prompts",
                color = Color(0xFF666666), fontSize = 9.sp,
                modifier = Modifier.padding(start = 4.dp)
            )
        }
    }
}

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    displayFormat: String,
    onChange: (Float) -> Unit
) {
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
            onValueChange = onChange,
            valueRange = range,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFF7C3AED),
                activeTrackColor = Color(0xFF7C3AED),
                inactiveTrackColor = Color(0xFF303030)
            )
        )
        Text(
            String.format(displayFormat, value),
            color = Color.White, fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(46.dp)
        )
    }
}