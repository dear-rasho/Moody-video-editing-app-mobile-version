package com.moody.moodyvideoeditor.ui.features

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
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
fun VolumePanel(
    volume: Float,
    isMuted: Boolean,
    onVolumeChanged: (Float) -> Unit,
    onMuteToggle: () -> Unit,
    onClose: () -> Unit
) {
    FeaturePanel(
        title = "🔊 Volume" + if (isMuted) " (Muted)" else "",
        onClose = onClose
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Vol",
                color = Color(0xFF888888),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(30.dp)
            )
            Slider(
                value = if (isMuted) 0f else volume,
                onValueChange = { onVolumeChanged(it) },
                valueRange = 0f..1f,
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFF7C3AED),
                    activeTrackColor = Color(0xFF7C3AED)
                )
            )
            Text(
                text = "${((if (isMuted) 0f else volume) * 100).toInt()}%",
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(45.dp)
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(
                    if (isMuted) Color(0xFFFF6B6B).copy(alpha = 0.2f)
                    else Color(0xFF181818)
                )
                .pointerInput(isMuted) {
                    detectTapGestures { onMuteToggle() }
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (isMuted) "🔇 Unmute" else "🔊 Mute",
                color = if (isMuted) Color(0xFFFF6B6B) else Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}