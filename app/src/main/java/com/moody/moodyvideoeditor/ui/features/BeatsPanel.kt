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
import com.moody.moodyvideoeditor.data.BeatsLibrary
import com.moody.moodyvideoeditor.data.BeatsState
import com.moody.moodyvideoeditor.ui.components.FeaturePanel

@Composable
fun BeatsPanel(
    state: BeatsState,
    onDetect: (String) -> Unit,
    onClear: () -> Unit,
    onClose: () -> Unit
) {
    FeaturePanel(
        title = "🥁 Beats" + if (state.detected) " · ${state.count}" else "",
        onClose = onClose
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {

            Text(
                "Beat Range Filter",
                color = Color(0xFF888888), fontSize = 9.sp,
                fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                BeatsLibrary.FILTERS.forEach { (key, label) ->
                    val isActive = state.filter == key
                    Box(
                        modifier = Modifier
                            .height(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isActive) Color(0xFF7C3AED) else Color(0xFF181818))
                            .pointerInput(key) { detectTapGestures { onDetect(key) } }
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            label,
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(Modifier.height(4.dp))

            if (state.detected) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF0F2A1A))
                        .padding(10.dp)
                ) {
                    Text(
                        "✅ ${state.count} beats detected",
                        color = Color(0xFF22C55E), fontSize = 11.sp, fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "First: ${state.beatTimesMs.firstOrNull() ?: 0}ms  ·  Last: ${state.beatTimesMs.lastOrNull() ?: 0}ms",
                        color = Color(0xFF888888), fontSize = 10.sp
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFFF6B6B).copy(alpha = 0.15f))
                        .pointerInput(Unit) { detectTapGestures { onClear() } },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "🗑 Clear Beats",
                        color = Color(0xFFFF6B6B), fontSize = 12.sp, fontWeight = FontWeight.Bold
                    )
                }
            } else {
                Text(
                    "Tap a filter to detect beats",
                    color = Color(0xFF666666), fontSize = 10.sp,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
        }
    }
}