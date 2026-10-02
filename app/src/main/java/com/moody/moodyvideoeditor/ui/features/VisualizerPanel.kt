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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moody.moodyvideoeditor.data.VisualizerPreset
import com.moody.moodyvideoeditor.data.VisualizerState
import com.moody.moodyvideoeditor.ui.components.ColorPickerField
import com.moody.moodyvideoeditor.ui.components.FeaturePanel

@Composable
fun VisualizerPanel(
    current: VisualizerState,
    hasAudio: Boolean,
    onStateChanged: (VisualizerState) -> Unit,
    onPickImage: () -> Unit,
    onClearImage: () -> Unit,
    onRemove: () -> Unit,
    onClose: () -> Unit
) {
    var subView by remember { mutableStateOf("main") }

    // ═══════════════════════════════════════════════════════════
    //  SUB-VIEW: Full text editor (reuses TextPanel)
    // ═══════════════════════════════════════════════════════════
    if (subView == "text") {
        TextPanel(
            currentText = current.textState,
            hasTextClipSelected = true,
            onTextChanged = { newText ->
                onStateChanged(
                    current.copy(
                        textState = newText,
                        textContent = newText.content
                    )
                )
            },
            onCreateNew = {},
            onRemove = {
                onStateChanged(
                    current.copy(
                        showText = false,
                        textContent = "",
                        textState = current.textState.copy(content = "")
                    )
                )
                subView = "main"
            },
            onApplyTemplate = {},
            onClose = { subView = "main" }
        )
        return
    }

    FeaturePanel(title = "🎵 Visualizer", onClose = onClose) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 380.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {

            if (!hasAudio) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF2A0F0F))
                        .padding(10.dp)
                ) {
                    Text(
                        "⚠️ Select an audio clip first",
                        color = Color(0xFFFF6B6B),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // ═══ BEAT STATUS ═══
            if (current.hasBeats) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF0F2A1A))
                        .padding(10.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            "✅ ${current.beatTimesMs.size} beats detected",
                            color = Color(0xFF22C55E),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Visualizer will pulse on every beat",
                            color = Color(0xFF888888),
                            fontSize = 9.sp
                        )
                    }
                }
            }

            // ═══ PRESET CHIPS ═══
            Text(
                "Preset (${VisualizerPreset.values().size})",
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
                VisualizerPreset.values().forEach { p ->
                    val isActive = current.preset == p
                    Column(
                        modifier = Modifier
                            .width(78.dp)
                            .height(72.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (isActive) Color(0xFF2A1F4D) else Color(0xFF181818)
                            )
                            .pointerInput(p) {
                                detectTapGestures {
                                    onStateChanged(current.copy(preset = p))
                                }
                            }
                            .padding(4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(p.icon, fontSize = 20.sp)
                        Spacer(Modifier.height(3.dp))
                        Text(
                            p.label,
                            color = if (isActive) Color(0xFFFFD166) else Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            lineHeight = 10.sp
                        )
                    }
                }
            }

            // ═══ BEAT REACTION ═══
            SliderRow("Beat Reaction", current.beatReaction, 0f..2f, "%.2f") {
                onStateChanged(current.copy(beatReaction = it))
            }

            // ═══════════════════════════════════════════════════════
            //  🆕 CIRCLE CONTENT (Image + Text toggles)
            // ═══════════════════════════════════════════════════════
            Spacer(Modifier.height(4.dp))
            Text(
                "Circle Content",
                color = Color(0xFF60EFFF), fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 4.dp)
            )

            // Content mode toggles
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ToggleChip(
                    label = "🖼️ Image",
                    active = current.showImage,
                    modifier = Modifier.weight(1f)
                ) {
                    onStateChanged(current.copy(showImage = !current.showImage))
                }
                ToggleChip(
                    label = "📝 Text",
                    active = current.showText,
                    modifier = Modifier.weight(1f)
                ) {
                    onStateChanged(current.copy(showText = !current.showText))
                }
            }

            // If both enabled → stacking order toggle
            if (current.showImage && current.showText) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Order",
                        color = Color(0xFF888888),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(60.dp)
                    )
                    ToggleChip(
                        label = "Image → Text",
                        active = current.textOnTopOfImage,
                        modifier = Modifier.weight(1f)
                    ) {
                        onStateChanged(current.copy(textOnTopOfImage = true))
                    }
                    ToggleChip(
                        label = "Text → Image",
                        active = !current.textOnTopOfImage,
                        modifier = Modifier.weight(1f)
                    ) {
                        onStateChanged(current.copy(textOnTopOfImage = false))
                    }
                }
            }

            // ─── TEXT SUB-SECTION ───
            if (current.showText) {
                // Text preview + Edit button
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF0F0F0F))
                        .padding(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Text:",
                                color = Color(0xFF888888),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                current.textState.content.ifBlank { "(empty)" },
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2
                            )
                        }
                        Box(
                            modifier = Modifier
                                .height(32.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF7C3AED))
                                .pointerInput(Unit) {
                                    detectTapGestures { subView = "text" }
                                }
                                .padding(horizontal = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "✏️ Edit",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Quick text size slider
                SliderRow(
                    "Text Size",
                    current.textState.fontSize.toFloat(),
                    8f..200f,
                    "%.0f"
                ) { v ->
                    val newTextState = current.textState.copy(fontSize = v.toInt())
                    onStateChanged(
                        current.copy(
                            textState = newTextState,
                            textContent = newTextState.content
                        )
                    )
                }
            }

            // ─── IMAGE SUB-SECTION ───
            if (current.showImage) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF7C3AED))
                            .pointerInput(Unit) {
                                detectTapGestures { onPickImage() }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (current.imageUri == null) "📷 Choose Image"
                            else "📷 Change Image",
                            color = Color.White, fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (current.imageUri != null) {
                        Box(
                            modifier = Modifier
                                .height(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFFF6B6B).copy(alpha = 0.2f))
                                .pointerInput(Unit) {
                                    detectTapGestures { onClearImage() }
                                }
                                .padding(horizontal = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "✕ Remove", color = Color(0xFFFF6B6B),
                                fontSize = 10.sp, fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                if (current.imageUri != null) {
                    ToggleRow("Bass-only Pulse", current.imageBassOnly) {
                        onStateChanged(current.copy(imageBassOnly = it))
                    }
                    ToggleRow("Idle Rotation", current.imageIdleRotation) {
                        onStateChanged(current.copy(imageIdleRotation = it))
                    }
                    SliderRow("Img Size", current.imageScale, 0.2f..1f, "%.2f") {
                        onStateChanged(current.copy(imageScale = it))
                    }
                    SliderRow("Img Pulse", current.imagePulseAmount, 0f..0.5f, "%.2f") {
                        onStateChanged(current.copy(imagePulseAmount = it))
                    }
                    SliderRow("Img Opacity", current.imageOpacity, 0f..1f, "%.2f") {
                        onStateChanged(current.copy(imageOpacity = it))
                    }
                }
            }

            // ═══════════════════════════════════════════════════════
            //  VISUALIZER STYLING
            // ═══════════════════════════════════════════════════════
            Spacer(Modifier.height(4.dp))
            Text(
                "Ring Style",
                color = Color(0xFF888888), fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 4.dp)
            )

            ColorPickerField(
                label = "Color A",
                colorLong = current.color1,
                onChange = { onStateChanged(current.copy(color1 = it)) }
            )
            ColorPickerField(
                label = "Color B",
                colorLong = current.color2,
                onChange = { onStateChanged(current.copy(color2 = it)) }
            )

            SliderRow("Size", current.size, 0.15f..0.6f, "%.2f") {
                onStateChanged(current.copy(size = it))
            }
            SliderRow("Pos X", current.positionX, 0f..1f, "%.2f") {
                onStateChanged(current.copy(positionX = it))
            }
            SliderRow("Pos Y", current.positionY, 0f..1f, "%.2f") {
                onStateChanged(current.copy(positionY = it))
            }
            SliderRow("Opacity", current.opacity, 0f..1f, "%.2f") {
                onStateChanged(current.copy(opacity = it))
            }

            ToggleRow("Glow", current.glow) {
                onStateChanged(current.copy(glow = it))
            }

            Spacer(Modifier.height(4.dp))

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
                    "🗑 Remove Visualizer",
                    color = Color(0xFFFF6B6B), fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  HELPERS
// ═══════════════════════════════════════════════════════════════
@Composable
private fun ToggleChip(
    label: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(36.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (active) Color(0xFF7C3AED) else Color(0xFF181818))
            .pointerInput(label) { detectTapGestures { onClick() } },
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    format: String,
    onChange: (Float) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            label, color = Color(0xFF888888), fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(80.dp)
        )
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onChange,
            valueRange = range,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFFFFD166),
                activeTrackColor = Color(0xFFFFD166),
                inactiveTrackColor = Color(0xFF303030)
            )
        )
        Text(
            String.format(format, value),
            color = Color.White, fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(44.dp)
        )
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label, color = Color(0xFF888888), fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
        Box(
            modifier = Modifier
                .height(28.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(if (checked) Color(0xFFFFD166) else Color(0xFF181818))
                .pointerInput(checked) {
                    detectTapGestures { onChange(!checked) }
                }
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                if (checked) "ON" else "OFF",
                color = if (checked) Color.Black else Color.White,
                fontSize = 10.sp, fontWeight = FontWeight.Bold
            )
        }
    }
}