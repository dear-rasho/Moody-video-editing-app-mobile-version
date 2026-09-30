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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moody.moodyvideoeditor.ui.components.FeaturePanel
import com.moody.moodyvideoeditor.utils.SpeedEngine

/**
 * Mirrors js/features/speed.js
 * Shows: info bar + presets + fine slider + reset.
 */
@Composable
fun SpeedPanel(
    clipName: String,
    baseDurationMs: Long,
    currentSpeed: Float,
    hasClipSelected: Boolean,
    onSpeedChanged: (Float) -> Unit,
    onReset: () -> Unit,
    onClose: () -> Unit
) {
    FeaturePanel(title = "⏩ Speed · ${String.format("%.2f", currentSpeed)}x", onClose = onClose) {

        // ─── NO CLIP ──────────────────────────────────
        if (!hasClipSelected) {
            EmptyState(
                icon = "👆",
                title = "No clip selected",
                text = "Tap a video or audio clip on the timeline first."
            )
            return@FeaturePanel
        }

        var sliderValue by remember(currentSpeed) { mutableFloatStateOf(currentSpeed) }

        // ─── INFO BAR ─────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF181818))
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            InfoRow("Clip:", clipName)
            InfoRow("Base:", "${baseDurationMs / 1000.0}s")
            InfoRow(
                "Duration:",
                String.format("%.2fs", (baseDurationMs / currentSpeed) / 1000.0)
            )
        }

        Spacer(Modifier.height(8.dp))

        // ─── PRESETS ROW ──────────────────────────────
        Text(
            "Presets",
            color = Color(0xFF888888),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 4.dp)
        )
        Spacer(Modifier.height(4.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            SpeedEngine.PRESETS.forEach { p ->
                val isActive = kotlin.math.abs(currentSpeed - p) < 0.01f
                Box(
                    modifier = Modifier
                        .height(34.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (isActive) Color(0xFF7C3AED) else Color(0xFF181818)
                        )
                        .pointerInput(p) {
                            detectTapGestures {
                                sliderValue = p
                                onSpeedChanged(p)
                            }
                        }
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "${p}x",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // ─── FINE SLIDER ──────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "Fine",
                color = Color(0xFF888888),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(36.dp)
            )
            Slider(
                value = sliderValue,
                onValueChange = {
                    sliderValue = it
                    onSpeedChanged(it)
                },
                valueRange = SpeedEngine.MIN_SPEED..SpeedEngine.MAX_SPEED,
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFF7C3AED),
                    activeTrackColor = Color(0xFF7C3AED),
                    inactiveTrackColor = Color(0xFF303030)
                )
            )
            Text(
                String.format("%.2fx", sliderValue),
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(52.dp)
            )
        }

        Spacer(Modifier.height(8.dp))

        // ─── RESET ────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFFFF6B6B).copy(alpha = 0.15f))
                .pointerInput(Unit) { detectTapGestures { onReset() } },
            contentAlignment = Alignment.Center
        ) {
            Text(
                "↺ Reset Speed",
                color = Color(0xFFFF6B6B),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Color(0xFF888888), fontSize = 10.sp)
        Text(value, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
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