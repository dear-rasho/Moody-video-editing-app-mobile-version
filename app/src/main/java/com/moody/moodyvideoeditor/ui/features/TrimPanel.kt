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
    onClose: () -> Unit,
    onTrimLeft: () -> Unit,
    onTrimRight: () -> Unit,
    onSplit: () -> Unit
) {
    FeaturePanel(title = "✂️ Trim", onClose = onClose) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ActionCard(
                icon = "⬅️",
                label = "Trim Left",
                desc = "Start se kaato",
                modifier = Modifier.weight(1f),
                onClick = onTrimLeft
            )
            ActionCard(
                icon = "➡️",
                label = "Trim Right",
                desc = "End se kaato",
                modifier = Modifier.weight(1f),
                onClick = onTrimRight
            )
            ActionCard(
                icon = "✂️",
                label = "Split",
                desc = "Beech se todo",
                modifier = Modifier.weight(1f),
                onClick = onSplit
            )
        }
    }
}

@Composable
fun ActionCard(
    icon: String,
    label: String,
    desc: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .height(90.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF181818))
            .pointerInput(label) { detectTapGestures { onClick() } }
            .padding(10.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = icon, fontSize = 24.sp)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = desc,
            color = Color(0xFF888888),
            fontSize = 9.sp
        )
    }
}