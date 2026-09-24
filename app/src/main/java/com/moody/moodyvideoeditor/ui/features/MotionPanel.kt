package com.moody.moodyvideoeditor.ui.features

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
fun MotionPanel(current: String, onSelected: (String) -> Unit, onClose: () -> Unit) {
    val motions = listOf(
        "none",
        "shake",
        "tremor",
        "quake",
        "earthquake",
        "hit",
        "impact",
        "jolt",
        "rumble",
        "vibration",
        "bounce",
        "punch",
        "kick",
        "throb",
        "beat",
        "drop",
        "spring",
        "elastic",
        "boing",
        "pulse",
        "heartbeat",
        "breath",
        "pump",
        "thump",
        "zoomPulse",
        "zoomHard",
        "zoomSoft",
        "push",
        "pull",
        "rush",
        "slam",
        "wobble",
        "swing",
        "sway",
        "rock",
        "spin",
        "roll",
        "whirl",
        "glitch",
        "noise",
        "vhs",
        "datamosh",
        "signalLoss",
        "flicker",
        "strobe",
        "flashFast",
        "tv",
        "lightning",
        "spark"
    )
    FeaturePanel(title = "💫 Motion (${motions.size - 1})", onClose = onClose) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            motions.forEach { m ->
                val isActive = current == m
                Box(
                    modifier = Modifier
                        .height(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isActive) Color(0xFF7C3AED) else Color(0xFF181818))
                        .pointerInput(m) { detectTapGestures { onSelected(m) } }
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(m, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}