package com.moody.moodyvideoeditor.ui.components

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
import androidx.compose.foundation.layout.widthIn
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
import com.moody.moodyvideoeditor.utils.TimelineZoom

@Composable
fun TimelineToolbar(
    onSelectBackward: () -> Unit,
    onSelectForward: () -> Unit,
    onMagnet: () -> Unit,
    onAddVisualLayer: () -> Unit = {},     // 🆕
    onAddAudioLayer: () -> Unit = {},      // 🆕
    zoomSlider: Float,
    totalSec: Float,
    viewportContentWidthDp: Float,
    onZoomChange: (Float) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0A0A0A))
            .padding(horizontal = 6.dp, vertical = 3.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            IconBtn("⏪") { onSelectBackward() }
            IconBtn("⏩") { onSelectForward() }
            IconBtn("🧲") { onMagnet() }
            // 🆕 Layer add buttons
            IconBtn("＋V") { onAddVisualLayer() }
            IconBtn("＋A") { onAddAudioLayer() }
            Spacer(Modifier.weight(1f))
            Text(
                TimelineZoom.formatLabel(zoomSlider, totalSec, viewportContentWidthDp),
                color = Color(0xFFCCCCCC),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("🔍", fontSize = 10.sp, color = Color(0xFF888888))
            Slider(
                value = zoomSlider,
                onValueChange = onZoomChange,
                valueRange = TimelineZoom.SLIDER_MIN..TimelineZoom.SLIDER_MAX,
                modifier = Modifier
                    .weight(1f)
                    .height(20.dp),
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFF4F9DFF),
                    activeTrackColor = Color(0xFF4F9DFF),
                    inactiveTrackColor = Color(0xFF303030)
                )
            )
            Text("🎚️", fontSize = 10.sp, color = Color(0xFF888888))
        }
    }
}

@Composable
private fun IconBtn(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .height(30.dp)
            .widthIn(min = 30.dp)      // 🆕 wider for +V, +A
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF181818))
            .pointerInput(text) { detectTapGestures { onClick() } }
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, fontSize = 12.sp, color = Color.White)
    }
}