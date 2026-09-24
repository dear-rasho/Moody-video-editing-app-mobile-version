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
fun FreezePanel(onClose: () -> Unit) {
    FeaturePanel(title = "❄️ Freeze Frame", onClose = onClose) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            FreezeCard("❄️", "Freeze 1s", Modifier.weight(1f)) { }
            FreezeCard("❄️❄️", "Freeze 2s", Modifier.weight(1f)) { }
            FreezeCard("❄️❄️❄️", "Freeze 3s", Modifier.weight(1f)) { }
        }
        Text("Coming soon with FFmpeg", color = Color(0xFF666666), fontSize = 9.sp)
    }
}

@Composable
private fun FreezeCard(icon: String, label: String, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier = modifier
            .height(80.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF181818))
            .pointerInput(label) { detectTapGestures { onClick() } }
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(icon, fontSize = 22.sp)
        Spacer(modifier = Modifier.height(4.dp))
        Text(label, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}