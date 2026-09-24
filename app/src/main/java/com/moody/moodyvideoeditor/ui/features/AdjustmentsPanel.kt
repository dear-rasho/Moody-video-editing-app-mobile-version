package com.moody.moodyvideoeditor.ui.features

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
fun AdjustmentsPanel(
    brightness: Float,
    contrast: Float,
    saturation: Float,
    exposure: Float,
    temperature: Float,
    tint: Float,
    vignette: Float,
    grain: Float,
    onBrightnessChanged: (Float) -> Unit,
    onContrastChanged: (Float) -> Unit,
    onSaturationChanged: (Float) -> Unit,
    onExposureChanged: (Float) -> Unit,
    onTemperatureChanged: (Float) -> Unit,
    onTintChanged: (Float) -> Unit,
    onVignetteChanged: (Float) -> Unit,
    onGrainChanged: (Float) -> Unit,
    onReset: () -> Unit,
    onClose: () -> Unit
) {
    FeaturePanel(title = "🎚️ Adjustments", onClose = onClose) {
        AdjustRow("Bright", brightness, 0.5f..1.5f, onBrightnessChanged)
        AdjustRow("Contrast", contrast, 0.5f..1.5f, onContrastChanged)
        AdjustRow("Saturate", saturation, 0f..2f, onSaturationChanged)
        AdjustRow("Exposure", exposure, 0.5f..1.5f, onExposureChanged)
        AdjustRow("Temp", temperature, -100f..100f, onTemperatureChanged)
        AdjustRow("Tint", tint, -100f..100f, onTintChanged)
        AdjustRow("Vignette", vignette, 0f..100f, onVignetteChanged)
        AdjustRow("Grain", grain, 0f..100f, onGrainChanged)

        Box(
            modifier = Modifier
                .height(30.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFFFF6B6B).copy(alpha = 0.2f))
                .pointerInput(Unit) { detectTapGestures { onReset() } }
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "↺ Reset All",
                color = Color(0xFFFF6B6B),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun AdjustRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = Color(0xFF888888),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(60.dp)
        )
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFF7C3AED),
                activeTrackColor = Color(0xFF7C3AED)
            )
        )
        Text(
            text = value.toInt().toString(),
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(40.dp)
        )
    }
}