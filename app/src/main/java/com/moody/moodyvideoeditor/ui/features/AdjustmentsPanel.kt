package com.moody.moodyvideoeditor.ui.features

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moody.moodyvideoeditor.data.AdjustmentData
import com.moody.moodyvideoeditor.ui.components.FeaturePanel

@Composable
fun AdjustmentsPanel(
    adj: AdjustmentData,
    onAdjChanged: (AdjustmentData) -> Unit,
    onReset: () -> Unit,
    onClose: () -> Unit
) {
    FeaturePanel(title = "🎚️ Adjustments", onClose = onClose) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 220.dp)
                .verticalScrollWithScrollbar(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            GroupLabel("Light")
            AdjustmentRow("Brightness", adj.brightness) { onAdjChanged(adj.copy(brightness = it)) }
            AdjustmentRow("Contrast", adj.contrast) { onAdjChanged(adj.copy(contrast = it)) }
            AdjustmentRow("Exposure", adj.exposure) { onAdjChanged(adj.copy(exposure = it)) }
            AdjustmentRow("Whites", adj.whites) { onAdjChanged(adj.copy(whites = it)) }
            AdjustmentRow("Blacks", adj.blacks) { onAdjChanged(adj.copy(blacks = it)) }
            AdjustmentRow("Shadows", adj.shadows) { onAdjChanged(adj.copy(shadows = it)) }
            AdjustmentRow("Highlights", adj.highlights) { onAdjChanged(adj.copy(highlights = it)) }

            GroupLabel("Color")
            AdjustmentRow("Saturation", adj.saturation) { onAdjChanged(adj.copy(saturation = it)) }
            AdjustmentRow("Vibrance", adj.vibrance) { onAdjChanged(adj.copy(vibrance = it)) }
            AdjustmentRow("Clarity", adj.clarity) { onAdjChanged(adj.copy(clarity = it)) }

            GroupLabel("Temperature")
            AdjustmentRow("Temp", adj.temperature) { onAdjChanged(adj.copy(temperature = it)) }
            AdjustmentRow("Tint", adj.tint) { onAdjChanged(adj.copy(tint = it)) }

            GroupLabel("Details — slider 50 max, box unlimited")
            AdjustmentRow("Noise", adj.noise, maxSlider = 50f) { onAdjChanged(adj.copy(noise = it)) }
            AdjustmentRow("Sharpen", adj.sharpen, maxSlider = 50f) { onAdjChanged(adj.copy(sharpen = it)) }
            AdjustmentRow("Vignette", adj.vignette, maxSlider = 50f) { onAdjChanged(adj.copy(vignette = it)) }

            GroupLabel("🎨 Color Channels — slider 50 max, box unlimited")
            AdjustmentRow("Reds", adj.reds, maxSlider = 50f) { onAdjChanged(adj.copy(reds = it)) }
            AdjustmentRow("Oranges", adj.oranges, maxSlider = 50f) { onAdjChanged(adj.copy(oranges = it)) }
            AdjustmentRow("Yellows", adj.yellows, maxSlider = 50f) { onAdjChanged(adj.copy(yellows = it)) }
            AdjustmentRow("Greens", adj.greens, maxSlider = 50f) { onAdjChanged(adj.copy(greens = it)) }
            AdjustmentRow("Cyans", adj.cyans, maxSlider = 50f) { onAdjChanged(adj.copy(cyans = it)) }
            AdjustmentRow("Blues", adj.blues, maxSlider = 50f) { onAdjChanged(adj.copy(blues = it)) }
            AdjustmentRow("Purples", adj.purples, maxSlider = 50f) { onAdjChanged(adj.copy(purples = it)) }
            AdjustmentRow("Magentas", adj.magentas, maxSlider = 50f) { onAdjChanged(adj.copy(magentas = it)) }
            AdjustmentRow("Skin Tones", adj.skinTones, maxSlider = 50f) { onAdjChanged(adj.copy(skinTones = it)) }
        }

        Box(
            modifier = Modifier
                .height(30.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFFFF6B6B).copy(alpha = 0.2f))
                .pointerInput(Unit) { detectTapGestures { onReset() } }
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("↺ Reset All", color = Color(0xFFFF6B6B), fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun GroupLabel(text: String) {
    Text(
        text = text.uppercase(),
        color = Color(0xFF7C3AED),
        fontSize = 9.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 6.dp, start = 2.dp)
    )
}

/**
 * Adjustment row with slider + unlimited text input.
 * - Slider range: -maxSlider..maxSlider (default 200 for normal, 50 for strong channels)
 * - Text input: accepts ANY number (100, 500, -300) — no clamp
 */
@Composable
private fun AdjustmentRow(
    label: String,
    value: Float,
    maxSlider: Float = 200f,
    onChange: (Float) -> Unit
) {
    var sliderValue by remember(value, maxSlider) {
        mutableFloatStateOf(value.coerceIn(-maxSlider, maxSlider))
    }
    var textValue by remember {
        mutableStateOf(formatValue(value))
    }
    var isFocused by remember { mutableStateOf(false) }

    LaunchedEffect(value, isFocused) {
        if (!isFocused) {
            textValue = formatValue(value)
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = label,
            color = Color(0xFFAAAAAA),
            fontSize = 10.sp,
            modifier = Modifier.width(58.dp)
        )

        Slider(
            value = sliderValue,
            onValueChange = {
                sliderValue = it
                onChange(it)
                textValue = formatValue(it)
            },
            valueRange = -maxSlider..maxSlider,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFF7C3AED),
                activeTrackColor = Color(0xFF7C3AED),
                inactiveTrackColor = Color(0xFF303030)
            )
        )

        Box(
            modifier = Modifier
                .width(60.dp)
                .height(30.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF0F0F0F))
                .padding(horizontal = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            BasicTextField(
                value = textValue,
                onValueChange = { newText ->
                    val filtered = newText.filter {
                        it.isDigit() || it == '-' || it == '.' || it == '+'
                    }
                    textValue = filtered
                    filtered.toFloatOrNull()?.let { parsed ->
                        if (parsed.isFinite()) {
                            onChange(parsed)
                        }
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
                    keyboardType = KeyboardType.Decimal
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { focusState ->
                        isFocused = focusState.isFocused
                        if (!focusState.isFocused) {
                            textValue.toFloatOrNull()?.let { parsed ->
                                if (parsed.isFinite()) onChange(parsed)
                            }
                        }
                    }
            )
        }
    }
}

private fun formatValue(v: Float): String {
    val rounded = if (v == v.toInt().toFloat()) v.toInt().toString()
    else "%.1f".format(v)
    return if (v > 0) "+$rounded" else rounded
}