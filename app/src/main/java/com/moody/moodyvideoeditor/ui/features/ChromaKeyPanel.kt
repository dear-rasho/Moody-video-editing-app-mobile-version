package com.moody.moodyvideoeditor.ui.features

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.moody.moodyvideoeditor.ui.components.FeaturePanel

@Composable
fun ChromaKeyPanel(
    chromaColor: Int,
    similarity: Float, smoothness: Float, spill: Float, intensity: Float,
    onColorChanged: (Int) -> Unit,
    onSimilarityChanged: (Float) -> Unit,
    onSmoothnessChanged: (Float) -> Unit,
    onSpillChanged: (Float) -> Unit,
    onIntensityChanged: (Float) -> Unit,
    onClose: () -> Unit
) {
    FeaturePanel(title = "🟢 Chroma Key", onClose = onClose) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)
        ) {
            ColorPreset("Green", Color(0xFF00FF00).value.toInt()) { onColorChanged(it) }
            ColorPreset("Blue", Color(0xFF0000FF).value.toInt()) { onColorChanged(it) }
            ColorPreset("Custom", chromaColor) { onColorChanged(it) }
        }
        SliderRow("Similarity", similarity, 0f..100f, onSimilarityChanged)
        SliderRow("Smooth", smoothness, 0f..100f, onSmoothnessChanged)
        SliderRow("Spill", spill, 0f..100f, onSpillChanged)
        SliderRow("Intensity", intensity, 0f..100f, onIntensityChanged)
    }
}

@Composable
private fun ColorPreset(label: String, color: Int, onClick: (Int) -> Unit) {
    Box(
        modifier = Modifier
            .width(80.dp)
            .height(40.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(color))
            .pointerInput(label) { detectTapGestures { onClick(color) } },
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (color == Color(0xFF00FF00).value.toInt()) Color.Black else Color.White,
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
    onChange: (Float) -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            color = Color(0xFF888888),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(70.dp)
        )
        Slider(
            value = value, onValueChange = onChange, valueRange = range,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFF7C3AED),
                activeTrackColor = Color(0xFF7C3AED)
            )
        )
        Text(
            "${value.toInt()}%",
            color = Color.White,
            fontSize = 10.sp,
            modifier = Modifier.width(40.dp)
        )
    }
}