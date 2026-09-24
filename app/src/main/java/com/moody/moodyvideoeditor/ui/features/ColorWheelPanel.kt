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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moody.moodyvideoeditor.ui.components.FeaturePanel

@Composable
fun ColorWheelPanel(
    shadowsHue: Float, shadowsSat: Float,
    midtonesHue: Float, midtonesSat: Float,
    highlightsHue: Float, highlightsSat: Float,
    hdrWhite: Float,
    onShadowsChanged: (Float, Float) -> Unit,
    onMidtonesChanged: (Float, Float) -> Unit,
    onHighlightsChanged: (Float, Float) -> Unit,
    onHdrChanged: (Float) -> Unit,
    onClose: () -> Unit
) {
    FeaturePanel(title = "🌈 Color Wheels", onClose = onClose) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            WheelItem("Shadows", shadowsHue, shadowsSat, onShadowsChanged)
            WheelItem("Midtones", midtonesHue, midtonesSat, onMidtonesChanged)
            WheelItem("Highlights", highlightsHue, highlightsSat, onHighlightsChanged)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "HDR",
                color = Color(0xFF888888),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(40.dp)
            )
            Slider(
                value = hdrWhite, onValueChange = onHdrChanged,
                valueRange = 0f..200f,
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFF7C3AED),
                    activeTrackColor = Color(0xFF7C3AED)
                )
            )
            Text(
                "${hdrWhite.toInt()}",
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(35.dp)
            )
        }
    }
}

@Composable
private fun WheelItem(label: String, hue: Float, sat: Float, onChange: (Float, Float) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .width(80.dp)
                .height(80.dp)
                .clip(CircleShape)
                .background(
                    Brush.sweepGradient(
                        listOf(
                            Color.Red,
                            Color.Yellow,
                            Color.Green,
                            Color.Cyan,
                            Color.Blue,
                            Color.Magenta,
                            Color.Red
                        )
                    )
                )
                .pointerInput(label) {
                    detectTapGestures { offset ->
                        val cx = 40f
                        val cy = 40f
                        val dx = offset.x - cx
                        val dy = offset.y - cy
                        val dist = kotlin.math.sqrt(dx * dx + dy * dy)
                        val newSat = (dist / 40f * 100f).coerceIn(0f, 100f)
                        var angle = Math.toDegrees(kotlin.math.atan2(dy.toDouble(), dx.toDouble()))
                            .toFloat()
                        if (angle < 0) angle += 360f
                        onChange(angle, newSat)
                    }
                }
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text("H: ${hue.toInt()}° S: ${sat.toInt()}%", color = Color(0xFF888888), fontSize = 9.sp)
    }
}