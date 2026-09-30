package com.moody.moodyvideoeditor.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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

data class CodeBaseItem(
    val icon: String,
    val title: String,
    val desc: String
)

@Composable
fun CodeBaseShelf(onOpen: (String) -> Unit) {
    val items = listOf(
        CodeBaseItem(
            icon = "＋",
            title = "New Code Project",
            desc = "Start from scratch. Use text prompts to edit video."
        ),
        CodeBaseItem(
            icon = "📚",
            title = "Prompt Examples",
            desc = "Browse ready-made prompts — brightness, fade, zoom."
        )
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items.forEach { item ->
            Column(
                modifier = Modifier
                    .width(260.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF181818))
                    .pointerInput(item.title) {
                        detectTapGestures { onOpen(item.title) }
                    }
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(Color(0xFF7C3AED).copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(item.icon, color = Color(0xFFA78BFA), fontSize = 18.sp)
                }
                Text(
                    text = item.title,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = item.desc,
                    color = Color(0xFF888888),
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
            }
        }
    }
}