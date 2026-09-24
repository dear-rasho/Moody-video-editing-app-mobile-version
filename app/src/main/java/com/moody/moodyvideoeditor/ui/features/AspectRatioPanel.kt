package com.moody.moodyvideoeditor.ui.features

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import com.moody.moodyvideoeditor.data.RatioLibrary
import com.moody.moodyvideoeditor.ui.components.FeaturePanel

@Composable
fun AspectRatioPanel(
    currentRatio: String,
    onRatioSelected: (String) -> Unit,
    onClose: () -> Unit
) {
    FeaturePanel(title = "🖼️ Aspect Ratio", onClose = onClose) {

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "Choose Frame Ratio",
                color = Color(0xFF888888), fontSize = 9.sp,
                fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RatioLibrary.OPTIONS.forEach { r ->
                    val isActive = currentRatio == r.key
                    Column(
                        modifier = Modifier
                            .height(70.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isActive) Color(0xFF2A1F4D) else Color(0xFF181818))
                            .pointerInput(r.key) { detectTapGestures { onRatioSelected(r.key) } }
                            .padding(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        // Mini visual box
                        val boxMaxW = 36f
                        val boxMaxH = 26f
                        val ar = r.aspect
                        val (w, h) = if (ar >= 1f) boxMaxW to (boxMaxW / ar) else (boxMaxH * ar) to boxMaxH
                        Box(
                            modifier = Modifier
                                .height(h.dp)
                                .padding(horizontal = 2.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(if (isActive) Color(0xFF7C3AED) else Color(0xFF444444))
                                .fillMaxWidth(0.1f + (w / 100f))
                                .height(h.dp)
                        ) {}
                        Text(
                            r.key,
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}