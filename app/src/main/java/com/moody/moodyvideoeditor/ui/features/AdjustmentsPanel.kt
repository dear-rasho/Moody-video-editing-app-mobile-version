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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
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
                .heightIn(max = 200.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // ═══ LIGHT ═══
            GroupLabel("Light")
            SliderRow("Brightness", adj.brightness) { onAdjChanged(adj.copy(brightness = it)) }
            SliderRow("Contrast", adj.contrast) { onAdjChanged(adj.copy(contrast = it)) }
            SliderRow("Exposure", adj.exposure) { onAdjChanged(adj.copy(exposure = it)) }
            SliderRow("Whites", adj.whites) { onAdjChanged(adj.copy(whites = it)) }
            SliderRow("Blacks", adj.blacks) { onAdjChanged(adj.copy(blacks = it)) }
            SliderRow("Shadows", adj.shadows) { onAdjChanged(adj.copy(shadows = it)) }
            SliderRow("Highlights", adj.highlights) { onAdjChanged(adj.copy(highlights = it)) }

            // ═══ COLOR ═══
            GroupLabel("Color")
            SliderRow("Saturation", adj.saturation) { onAdjChanged(adj.copy(saturation = it)) }
            SliderRow("Vibrance", adj.vibrance) { onAdjChanged(adj.copy(vibrance = it)) }
            SliderRow("Clarity", adj.clarity) { onAdjChanged(adj.copy(clarity = it)) }

            // ═══ TEMPERATURE ═══
            GroupLabel("Temperature")
            SliderRow("Temp", adj.temperature) { onAdjChanged(adj.copy(temperature = it)) }
            SliderRow("Tint", adj.tint) { onAdjChanged(adj.copy(tint = it)) }

            // ═══ DETAILS ═══
            GroupLabel("Details")
            SliderRow("Noise", adj.noise) { onAdjChanged(adj.copy(noise = it)) }
            SliderRow("Sharpen", adj.sharpen) { onAdjChanged(adj.copy(sharpen = it)) }
            SliderRow("Vignette", adj.vignette) { onAdjChanged(adj.copy(vignette = it)) }

            // ═══ COLOR CHANNELS ═══
            GroupLabel("Color Channels")
            SliderRow("Reds", adj.reds) { onAdjChanged(adj.copy(reds = it)) }
            SliderRow("Oranges", adj.oranges) { onAdjChanged(adj.copy(oranges = it)) }
            SliderRow("Yellows", adj.yellows) { onAdjChanged(adj.copy(yellows = it)) }
            SliderRow("Greens", adj.greens) { onAdjChanged(adj.copy(greens = it)) }
            SliderRow("Cyans", adj.cyans) { onAdjChanged(adj.copy(cyans = it)) }
            SliderRow("Blues", adj.blues) { onAdjChanged(adj.copy(blues = it)) }
            SliderRow("Purples", adj.purples) { onAdjChanged(adj.copy(purples = it)) }
            SliderRow("Magentas", adj.magentas) { onAdjChanged(adj.copy(magentas = it)) }
            SliderRow("Skin Tones", adj.skinTones) { onAdjChanged(adj.copy(skinTones = it)) }
        }

        // ═══ RESET BUTTON ═══
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

@Composable
private fun SliderRow(label: String, value: Float, onChange: (Float) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = Color(0xFFAAAAAA),
            fontSize = 10.sp,
            modifier = Modifier.width(64.dp)
        )
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = -100f..100f,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFF7C3AED),
                activeTrackColor = Color(0xFF7C3AED),
                inactiveTrackColor = Color(0xFF303030)
            )
        )
        Text(
            text = if (value > 0) "+${value.toInt()}" else value.toInt().toString(),
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(36.dp)
        )
    }
}