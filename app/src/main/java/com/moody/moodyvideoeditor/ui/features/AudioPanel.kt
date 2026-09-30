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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import com.moody.moodyvideoeditor.ui.components.FeaturePanel
import com.moody.moodyvideoeditor.utils.AudioEngine

@Composable
fun AudioPanel(
    onPreviewFx: (fx: String, intensity: Float) -> Unit,
    onClearPreview: () -> Unit,
    onApplyAudioFx: (fx: String, intensity: Float) -> Unit,
    onClose: () -> Unit
) {
    var selectedFx by remember { mutableStateOf<String?>(null) }
    var intensity by remember { mutableFloatStateOf(100f) }

    LaunchedEffect(Unit) {
        selectedFx = null
        onClearPreview()
    }

    FeaturePanel(title = "🎧 Audio FX", onClose = onClose) {

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 300.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {

            // ═══ FX CHIPS — LEFT/RIGHT SCROLLABLE ═══
            val filtered = AudioEngine.AUDIO_FX.filter { it.key != "none" }

            Text(
                "Tap to preview (${filtered.size})",
                color = Color(0xFF888888),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 4.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                filtered.forEach { preset ->
                    val isSelected = selectedFx == preset.key
                    ChipItem(
                        icon = preset.icon,
                        label = preset.label,
                        isSelected = isSelected,
                        onClick = {
                            selectedFx = preset.key
                            onPreviewFx(preset.key, intensity)
                        }
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            // ═══ INTENSITY + APPLY ═══
            val current = selectedFx
            if (current != null) {
                val preset = filtered.firstOrNull { it.key == current }
                val label = preset?.label ?: current

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF1F1438))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🎧", fontSize = 14.sp, modifier = Modifier.padding(end = 8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Previewing: $label",
                            color = Color(0xFFA78BFA),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Layer nahi banegi jab tak Apply na karo",
                            color = Color(0xFF666666),
                            fontSize = 9.sp
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Intensity",
                        color = Color(0xFF888888),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(60.dp)
                    )
                    Slider(
                        value = intensity.coerceIn(0f, 200f),
                        onValueChange = {
                            intensity = it
                            onPreviewFx(current, it)
                        },
                        valueRange = 0f..200f,
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF7C3AED),
                            activeTrackColor = Color(0xFF7C3AED),
                            inactiveTrackColor = Color(0xFF303030)
                        )
                    )
                    Text(
                        "${intensity.toInt()}%",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(44.dp)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF181818))
                            .pointerInput(Unit) {
                                detectTapGestures {
                                    selectedFx = null
                                    onClearPreview()
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "✕ Cancel",
                            color = Color(0xFFAAAAAA),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(2f)
                            .height(42.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF7C3AED))
                            .pointerInput(Unit) {
                                detectTapGestures {
                                    onApplyAudioFx(current, intensity)
                                    selectedFx = null
                                    intensity = 100f
                                    onClearPreview()
                                    onClose()
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "✨ Apply as Layer",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))

                Text(
                    "💡 Layer timeline pe banegi — drag, trim, stretch kar sakte ho",
                    color = Color(0xFF666666),
                    fontSize = 9.sp,
                    modifier = Modifier.padding(start = 4.dp)
                )
            } else {
                Text(
                    "👆 Tap any FX chip above to preview",
                    color = Color(0xFF666666),
                    fontSize = 10.sp,
                    modifier = Modifier.padding(start = 4.dp, top = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun TabChip(
    key: String,
    label: String,
    active: String,
    onPick: (String) -> Unit
) {
    val isActive = active == key
    Box(
        modifier = Modifier
            .height(30.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isActive) Color(0xFF7C3AED) else Color(0xFF181818))
            .pointerInput(key) { detectTapGestures { onPick(key) } }
            .padding(horizontal = 14.dp),
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
private fun ChipItem(
    icon: String,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(72.dp)
            .height(66.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (isSelected) Color(0xFF2A1F4D) else Color(0xFF181818))
            .then(
                if (isSelected) Modifier.border(
                    1.5.dp, Color(0xFF7C3AED), RoundedCornerShape(10.dp)
                ) else Modifier
            )
            .pointerInput(label) { detectTapGestures { onClick() } }
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(icon, fontSize = 18.sp)
        Spacer(Modifier.height(2.dp))
        Text(
            label,
            color = Color.White,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}