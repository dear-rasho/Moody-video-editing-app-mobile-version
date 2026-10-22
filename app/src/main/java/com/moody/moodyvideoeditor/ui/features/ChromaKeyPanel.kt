package com.moody.moodyvideoeditor.ui.features

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Colorize
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moody.moodyvideoeditor.data.ChromaState
import com.moody.moodyvideoeditor.ui.components.FeaturePanel

@Composable
fun ChromaKeyPanel(
    state: ChromaState,
    hasClipSelected: Boolean,
    isPickingColor: Boolean,
    onStartPicking: () -> Unit,
    onStateChanged: (ChromaState) -> Unit,
    onRemove: () -> Unit,
    onClose: () -> Unit
) {
    FeaturePanel(title = "🟢 Chroma Key", onClose = onClose) {

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
                    "No clip selected",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Select a video or image layer first.",
                    color = Color(0xFF888888),
                    fontSize = 10.sp
                )
            }
            return@FeaturePanel
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 340.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {

            // Eyedropper button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (isPickingColor) Color(0xFF22C55E)
                        else Color(0xFF7C3AED)
                    )
                    .clickable { if (!isPickingColor) onStartPicking() },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Colorize,
                    contentDescription = "Pick Color",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (isPickingColor) "Tap on Preview..."
                    else "Pick Color (Eyedropper)",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Hint banner while picking
            if (isPickingColor) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF0F2A1A))
                        .padding(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🎯", fontSize = 14.sp)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Preview pe kisi bhi color pe tap karo",
                            color = Color(0xFF22C55E),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Quick color presets
            Text(
                "Quick Colors",
                color = Color(0xFF888888),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 4.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(
                    0xFF00FF00L to "Green",
                    0xFF0000FFL to "Blue",
                    0xFFFF00FFL to "Magenta",
                    0xFF00E5FFL to "Cyan"
                ).forEach { (color, label) ->
                    val active = state.keyColor == color
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(32.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (active) Color(0xFF7C3AED)
                                else Color(0xFF181818)
                            )
                            .clickable {
                                onStateChanged(state.copy(keyColor = color))
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            label,
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Color Preview + Hex
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(state.keyColor))
                        .border(2.dp, Color.White, RoundedCornerShape(8.dp))
                )
                HexColorInput(
                    colorLong = state.keyColor,
                    onColorChanged = { onStateChanged(state.copy(keyColor = it)) },
                    modifier = Modifier.weight(1f)
                )
            }

            // Sliders
            SliderRow("Similarity", state.similarity) {
                onStateChanged(state.copy(similarity = it))
            }
            SliderRow("Smoothness", state.smoothness) {
                onStateChanged(state.copy(smoothness = it))
            }
            SliderRow("Spill", state.spill) {
                onStateChanged(state.copy(spill = it))
            }
            SliderRow("Intensity", state.intensity) {
                onStateChanged(state.copy(intensity = it))
            }

            // Remove button
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
                    "🗑 Remove Chroma",
                    color = Color(0xFFFF6B6B),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                "💡 Similarity 60-80 me rakho real greenscreen ke liye",
                color = Color(0xFF666666),
                fontSize = 9.sp,
                modifier = Modifier.padding(start = 4.dp)
            )
        }
    }
}

@Composable
private fun HexColorInput(
    colorLong: Long,
    onColorChanged: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var textValue by remember {
        mutableStateOf(String.format("#%06X", 0xFFFFFFL and colorLong))
    }
    var isFocused by remember { mutableStateOf(false) }

    LaunchedEffect(colorLong, isFocused) {
        if (!isFocused) {
            textValue = String.format("#%06X", 0xFFFFFFL and colorLong)
        }
    }

    Box(
        modifier = modifier
            .height(40.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF181818))
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        BasicTextField(
            value = textValue,
            onValueChange = { newText ->
                val filtered = newText.uppercase()
                    .filter { it.isLetterOrDigit() || it == '#' }
                    .take(7)
                textValue = if (!filtered.startsWith("#")) "#$filtered" else filtered
                if (textValue.length == 7) {
                    try {
                        onColorChanged(
                            0xFF000000L or textValue.removePrefix("#").toLong(16)
                        )
                    } catch (_: Exception) {
                    }
                }
            },
            singleLine = true,
            textStyle = TextStyle(
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            ),
            cursorBrush = SolidColor(Color(0xFF7C3AED)),
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focusState ->
                    isFocused = focusState.isFocused
                    if (!focusState.isFocused) {
                        textValue = String.format("#%06X", 0xFFFFFFL and colorLong)
                    }
                }
        )
    }
}

@Composable
private fun SliderRow(label: String, value: Float, onChange: (Float) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            label,
            color = Color(0xFF888888),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(64.dp)
        )
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = 0f..100f,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFF7C3AED),
                activeTrackColor = Color(0xFF7C3AED),
                inactiveTrackColor = Color(0xFF303030)
            )
        )
        Text(
            "${value.toInt()}%",
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(40.dp)
        )
    }
}