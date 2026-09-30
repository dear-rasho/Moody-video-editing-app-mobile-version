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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import com.moody.moodyvideoeditor.data.ChromaState
import com.moody.moodyvideoeditor.ui.components.FeaturePanel

@Composable
fun ChromaKeyPanel(
    state: ChromaState,
    hasClipSelected: Boolean,
    onStateChanged: (ChromaState) -> Unit,
    onRemove: () -> Unit,
    onClose: () -> Unit
) {
    FeaturePanel(title = "🟢 Chroma Key", onClose = onClose) {

        if (!hasClipSelected) {
            EmptyState("👆", "No clip selected", "Select a clip first.")
            return@FeaturePanel
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {

            // ─── COLOR PRESETS ──────────────────────
            Text(
                "Key Color",
                color = Color(0xFF888888), fontSize = 9.sp,
                fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp)
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ColorSwatch("Green", 0xFF00FF00, state.keyColor == 0xFF00FF00) {
                    onStateChanged(state.copy(keyColor = 0xFF00FF00))
                }
                ColorSwatch("Blue", 0xFF0000FF, state.keyColor == 0xFF0000FF) {
                    onStateChanged(state.copy(keyColor = 0xFF0000FF))
                }
                ColorSwatch("Magenta", 0xFFFF00FF, state.keyColor == 0xFFFF00FF) {
                    onStateChanged(state.copy(keyColor = 0xFFFF00FF))
                }
            }

            Spacer(Modifier.height(4.dp))

            // ─── SLIDERS ────────────────────────────
            SliderRow(
                "Similarity",
                state.similarity
            ) { onStateChanged(state.copy(similarity = it)) }
            SliderRow("Smooth", state.smoothness) { onStateChanged(state.copy(smoothness = it)) }
            SliderRow("Spill", state.spill) { onStateChanged(state.copy(spill = it)) }
            SliderRow("Intensity", state.intensity) { onStateChanged(state.copy(intensity = it)) }

            // ─── REMOVE ─────────────────────────────
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
                    "🗑 Remove Chroma",
                    color = Color(0xFFFF6B6B), fontSize = 12.sp, fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun ColorSwatch(label: String, color: Long, isActive: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(if (isActive) 46.dp else 42.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(color))
                .pointerInput(label) { detectTapGestures { onClick() } }
        )
        Text(label, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
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
            label, color = Color(0xFF888888), fontSize = 10.sp,
            fontWeight = FontWeight.Bold, modifier = Modifier.width(64.dp)
        )
        Slider(
            value = value, onValueChange = onChange, valueRange = 0f..100f,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFF7C3AED),
                activeTrackColor = Color(0xFF7C3AED),
                inactiveTrackColor = Color(0xFF303030)
            )
        )
        Text(
            "${value.toInt()}%", color = Color.White, fontSize = 10.sp,
            fontWeight = FontWeight.Bold, modifier = Modifier.width(40.dp)
        )
    }
}

@Composable
private fun EmptyState(icon: String, title: String, text: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF181818))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(icon, fontSize = 24.sp)
        Text(title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text(text, color = Color(0xFF888888), fontSize = 10.sp)
    }
}