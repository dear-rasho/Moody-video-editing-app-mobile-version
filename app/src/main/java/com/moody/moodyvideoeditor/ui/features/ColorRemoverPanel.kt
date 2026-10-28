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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moody.moodyvideoeditor.data.ChromaKeyPresets
import com.moody.moodyvideoeditor.data.ChromaKeyState
import com.moody.moodyvideoeditor.ui.components.FeaturePanel

@Composable
fun ColorRemoverPanel(
    current: ChromaKeyState,
    hasClipSelected: Boolean,
    onStateChanged: (ChromaKeyState) -> Unit,
    onPickFromScreen: () -> Unit,
    onClose: () -> Unit
) {
    FeaturePanel(title = "🟩 Chroma Key", onClose = onClose) {

        if (!hasClipSelected) {
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
                    "No layer selected",
                    color = Color.White, fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Pehle timeline pe ek clip select karo.",
                    color = Color(0xFF888888), fontSize = 10.sp
                )
            }
            return@FeaturePanel
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 480.dp)
                .verticalScrollWithScrollbar(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {

            // ─── ENABLE TOGGLE ───
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF1F1438))
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("🟩", fontSize = 20.sp)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Chroma Key",
                        color = Color.White, fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        if (current.enabled) "Removing color"
                        else "Tap to enable",
                        color = Color(0xFF888888), fontSize = 10.sp
                    )
                }
                Box(
                    modifier = Modifier
                        .height(30.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            if (current.enabled) Color(0xFF22C55E)
                            else Color(0xFF181818)
                        )
                        .pointerInput(current.enabled) {
                            detectTapGestures {
                                onStateChanged(current.copy(enabled = !current.enabled))
                            }
                        }
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (current.enabled) "ON" else "OFF",
                        color = Color.White, fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (!current.enabled) return@Column

            // ─── TARGET COLOR ───
            Text(
                "Target Color",
                color = Color(0xFF888888), fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 4.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Color box (shows picked color or empty)
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (current.hasColor) Color(current.targetColor)
                            else Color(0xFF0F0F0F)
                        )
                        .border(
                            2.dp,
                            if (current.hasColor) Color.White.copy(alpha = 0.4f)
                            else Color(0xFF2A2A2A),
                            RoundedCornerShape(8.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (!current.hasColor) {
                        Text("∅", color = Color(0xFF444444), fontSize = 22.sp)
                    }
                }

                // Hex code text + hint
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        if (current.hasColor)
                            "#%06X".format(current.targetColor and 0xFFFFFF)
                        else "No color picked",
                        color = if (current.hasColor) Color.White
                        else Color(0xFF666666),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        if (current.hasColor) "Tap 🎯 to pick a new color"
                        else "Tap 🎯 → tap on preview to pick color",
                        color = Color(0xFF666666), fontSize = 9.sp,
                        lineHeight = 12.sp
                    )
                }

                // Eyedropper button
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (current.enabled) Color(0xFF7C3AED)
                            else Color(0xFF333333)
                        )
                        .pointerInput(Unit) {
                            detectTapGestures { onPickFromScreen() }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text("🎯", fontSize = 24.sp)
                }
            }

            // Hint after eyedropper tapped
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF0F1A2A))
                    .padding(8.dp)
            ) {
                Text(
                    "💡 Tap 🎯 then tap anywhere on the video preview to " +
                            "pick the color from that pixel.",
                    color = Color(0xFF60EFFF),
                    fontSize = 9.sp,
                    lineHeight = 13.sp
                )
            }

            // ─── QUICK PRESETS ───
            Text(
                "Quick Colors",
                color = Color(0xFF888888), fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScrollWithTwoFinger(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ChromaKeyPresets.QUICK_PRESETS.forEach { (label, color) ->
                    val isActive = current.hasColor && current.targetColor == color
                    Box(
                        modifier = Modifier
                            .height(34.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isActive) Color(0xFF2A1F4D)
                                else Color(0xFF181818)
                            )
                            .border(
                                if (isActive) 1.5.dp else 0.dp,
                                if (isActive) Color(0xFF7C3AED)
                                else Color.Transparent,
                                RoundedCornerShape(8.dp)
                            )
                            .pointerInput(color) {
                                detectTapGestures {
                                    onStateChanged(
                                        current.copy(
                                            targetColor = color,
                                            hasColor = true,
                                            enabled = true
                                        )
                                    )
                                }
                            }
                            .padding(horizontal = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(14.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(Color(color))
                            )
                            Text(
                                label,
                                color = Color.White, fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // ═══════════════════════════════════════════════════
            //  SETTINGS
            // ═══════════════════════════════════════════════════
            Text(
                "Settings",
                color = Color(0xFF7C3AED), fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 4.dp, top = 6.dp)
            )

            // Settings presets
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScrollWithTwoFinger(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ChromaKeyPresets.SETTINGS_PRESETS.forEach { preset ->
                    val isActive = current.similarity == preset.similarity &&
                            current.smoothness == preset.smoothness &&
                            current.spillSuppression == preset.spill
                    Box(
                        modifier = Modifier
                            .height(30.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (isActive) Color(0xFF7C3AED)
                                else Color(0xFF181818)
                            )
                            .pointerInput(preset.key) {
                                detectTapGestures {
                                    onStateChanged(
                                        current.copy(
                                            similarity = preset.similarity,
                                            smoothness = preset.smoothness,
                                            spillSuppression = preset.spill
                                        )
                                    )
                                }
                            }
                            .padding(horizontal = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "${preset.icon} ${preset.label}",
                            color = Color.White, fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Similarity slider
            SettingRow(
                label = "Similarity",
                value = current.similarity,
                range = 0f..100f,
                decimals = 0,
                suffix = "%",
                hint = "How close color must be to be removed"
            ) { onStateChanged(current.copy(similarity = it)) }

            // Smoothness slider
            SettingRow(
                label = "Smoothness",
                value = current.smoothness,
                range = 0f..100f,
                decimals = 0,
                suffix = "%",
                hint = "Softer edges (reduces harsh cutout)"
            ) { onStateChanged(current.copy(smoothness = it)) }

            // Spill suppression
            SettingRow(
                label = "Spill Suppress",
                value = current.spillSuppression,
                range = 0f..100f,
                decimals = 0,
                suffix = "%",
                hint = "Removes green/blue tint from edges"
            ) { onStateChanged(current.copy(spillSuppression = it)) }

            // Opacity
            SettingRow(
                label = "Opacity",
                value = current.opacity,
                range = 0f..100f,
                decimals = 0,
                suffix = "%",
                hint = "Final layer opacity"
            ) { onStateChanged(current.copy(opacity = it)) }

            // ─── CLEAR BUTTON ───
            Spacer(Modifier.height(4.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFFF6B6B).copy(alpha = 0.15f))
                    .pointerInput(Unit) {
                        detectTapGestures {
                            onStateChanged(ChromaKeyState())
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "🗑 Clear Chroma Key",
                    color = Color(0xFFFF6B6B), fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun SettingRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    decimals: Int,
    suffix: String,
    hint: String,
    onChange: (Float) -> Unit
) {
    var textValue by remember(value) {
        mutableStateOf(
            if (decimals == 0) value.toInt().toString()
            else String.format("%.${decimals}f", value)
        )
    }
    var isFocused by remember { mutableStateOf(false) }

    LaunchedEffect(value, isFocused) {
        if (!isFocused) {
            textValue = if (decimals == 0) value.toInt().toString()
            else String.format("%.${decimals}f", value)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                label,
                color = Color(0xFFCCCCCC), fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(90.dp)
            )
            Slider(
                value = value.coerceIn(range.start, range.endInclusive),
                onValueChange = {
                    onChange(it)
                    textValue = if (decimals == 0) it.toInt().toString()
                    else String.format("%.${decimals}f", it)
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
                    .width(56.dp)
                    .height(28.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF0F0F0F))
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                BasicTextField(
                    value = textValue,
                    onValueChange = { input ->
                        val filtered = input.filter {
                            it.isDigit() || it == '.' || it == '-'
                        }
                        textValue = filtered
                        filtered.toFloatOrNull()?.takeIf { it.isFinite() }?.let {
                            onChange(it.coerceIn(range.start, range.endInclusive))
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { state ->
                            isFocused = state.isFocused
                            if (!state.isFocused) {
                                textValue.toFloatOrNull()
                                    ?.takeIf { it.isFinite() }
                                    ?.let { onChange(it.coerceIn(range.start, range.endInclusive)) }
                            }
                        }
                )
            }
            Text(suffix, color = Color(0xFF888888), fontSize = 10.sp)
        }
        Text(
            hint,
            color = Color(0xFF555555),
            fontSize = 8.sp,
            modifier = Modifier.padding(start = 90.dp)
        )
    }
}