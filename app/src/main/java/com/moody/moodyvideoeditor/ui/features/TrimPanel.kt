package com.moody.moodyvideoeditor.ui.features

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
fun TrimPanel(
    clipName: String,
    clipStartMs: Long,
    clipEndMs: Long,
    playheadMs: Long,
    sourceInMs: Long,
    sourceOutMs: Long,
    hasClipSelected: Boolean,
    playheadInsideClip: Boolean,
    onClose: () -> Unit,
    onTrimLeft: () -> Unit,
    onTrimRight: () -> Unit,
    onSplit: () -> Unit
) {
    FeaturePanel(title = "✂️ Trim & Split", onClose = onClose) {

        if (!hasClipSelected) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF181818))
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text("👆", fontSize = 24.sp)
                Text("No clip selected", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text("Tap a clip on the timeline first.", color = Color(0xFF888888), fontSize = 10.sp)
            }
            return@FeaturePanel
        }

        // 🆕 Sirf 3 buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ActionCard(
                icon = "✂️",
                label = "Split",
                desc = "Beech se",
                modifier = Modifier.weight(1f),
                onClick = onSplit
            )
            ActionCard(
                icon = "⬅️",
                label = "Trim Left",
                desc = "Left hatao",
                modifier = Modifier.weight(1f),
                onClick = onTrimLeft
            )
            ActionCard(
                icon = "➡️",
                label = "Trim Right",
                desc = "Right hatao",
                modifier = Modifier.weight(1f),
                danger = true,
                onClick = onTrimRight
            )
        }
    }
}

@Composable
private fun ActionCard(
    icon: String,
    label: String,
    desc: String,
    modifier: Modifier = Modifier,
    danger: Boolean = false,
    onClick: () -> Unit
) {
    val bg = if (danger) Color(0xFF2A1414) else Color(0xFF181818)

    Column(
        modifier = modifier
            .height(88.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .pointerInput(label) { detectTapGestures { onClick() } }
            .padding(10.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(icon, fontSize = 22.sp)
        Spacer(Modifier.height(2.dp))
        Text(label, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Text(desc, color = Color(0xFF888888), fontSize = 9.sp)
    }
}