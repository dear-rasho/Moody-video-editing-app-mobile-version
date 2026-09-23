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
fun TransformPanel(
    currentScale: Float,
    currentRotation: Float,
    currentOffsetX: Float,
    currentOffsetY: Float,
    onScaleChanged: (Float) -> Unit,
    onRotationChanged: (Float) -> Unit,
    onOffsetChanged: (Float, Float) -> Unit,
    onReset: () -> Unit,
    onClose: () -> Unit
) {
    FeaturePanel(title = "🔲 Transform", onClose = onClose) {
        SliderRow(
            label = "Scale",
            value = currentScale,
            valueRange = 0.1f..3f,
            display = "${(currentScale * 100).toInt()}%",
            onValueChange = onScaleChanged
        )

        SliderRow(
            label = "Rotate",
            value = currentRotation,
            valueRange = -180f..180f,
            display = "${currentRotation.toInt()}°",
            onValueChange = onRotationChanged
        )

        SliderRow(
            label = "X",
            value = currentOffsetX,
            valueRange = -1f..1f,
            display = "${(currentOffsetX * 100).toInt()}",
            onValueChange = { onOffsetChanged(it, currentOffsetY) }
        )

        SliderRow(
            label = "Y",
            value = currentOffsetY,
            valueRange = -1f..1f,
            display = "${(currentOffsetY * 100).toInt()}",
            onValueChange = { onOffsetChanged(currentOffsetX, it) }
        )

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
                text = "↺ Reset",
                color = Color(0xFFFF6B6B),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    display: String,
    onValueChange: (Float) -> Unit
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
            modifier = Modifier.width(50.dp)
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFF7C3AED),
                activeTrackColor = Color(0xFF7C3AED)
            )
        )
        Text(
            text = display,
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(50.dp)
        )
    }
}