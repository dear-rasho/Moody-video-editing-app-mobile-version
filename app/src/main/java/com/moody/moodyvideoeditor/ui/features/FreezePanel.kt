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
fun FreezePanel(
    hasClipSelected: Boolean,
    onFreeze: (Long) -> Unit,
    onClose: () -> Unit
) {
    FeaturePanel(title = "❄️ Freeze Frame", onClose = onClose) {

        if (!hasClipSelected) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text("👆", fontSize = 24.sp)
                Text(
                    "No clip selected",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Select a clip first.",
                    color = Color(0xFF888888),
                    fontSize = 10.sp
                )
            }
            return@FeaturePanel
        }

        Text(
            "Freeze duration at playhead",
            color = Color(0xFF888888),
            fontSize = 10.sp,
            modifier = Modifier.padding(start = 4.dp)
        )
        Spacer(Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FreezeCard(
                icon = "❄️",
                label = "1s",
                durMs = 1000L,
                modifier = Modifier.weight(1f),
                onClick = { onFreeze(it) }
            )
            FreezeCard(
                icon = "❄️❄️",
                label = "2s",
                durMs = 2000L,
                modifier = Modifier.weight(1f),
                onClick = { onFreeze(it) }
            )
            FreezeCard(
                icon = "❄️❄️❄️",
                label = "3s",
                durMs = 3000L,
                modifier = Modifier.weight(1f),
                onClick = { onFreeze(it) }
            )
        }
    }
}

@Composable
private fun FreezeCard(
    icon: String,
    label: String,
    durMs: Long,
    modifier: Modifier = Modifier,
    onClick: (Long) -> Unit
) {
    Column(
        modifier = modifier
            .height(76.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF181818))
            .pointerInput(label) { detectTapGestures { onClick(durMs) } }
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(icon, fontSize = 18.sp)
        Spacer(Modifier.height(4.dp))
        Text(
            "Freeze $label",
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}