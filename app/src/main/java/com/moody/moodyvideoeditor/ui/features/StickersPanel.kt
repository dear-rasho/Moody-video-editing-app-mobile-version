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
fun StickersPanel(
    currentSticker: String,
    onStickerSelected: (String) -> Unit,
    onClear: () -> Unit,
    onClose: () -> Unit
) {
    val stickers = listOf(
        "😀", "😂", "😍", "🤩", "😎", "🥳", "😇", "🤪",
        "❤️", "🔥", "⭐", "✨", "💯", "🎉", "🎊", "💥",
        "👍", "👏", "🙌", "🤙", "👌", "✌️", "🤘", "💪",
        "🌈", "☀️", "🌸", "🌹", "🍀", "🎈", "🎁", "🏆"
    )

    FeaturePanel(title = "😀 Stickers", onClose = onClose) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            stickers.forEach { s ->
                val isActive = currentSticker == s
                Box(
                    modifier = Modifier
                        .height(44.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isActive) Color(0xFF7C3AED) else Color(0xFF181818))
                        .pointerInput(s) { detectTapGestures { onStickerSelected(s) } }
                        .padding(horizontal = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = s, fontSize = 22.sp)
                }
            }
        }

        if (currentSticker.isNotBlank()) {
            Box(
                modifier = Modifier
                    .height(30.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFFF6B6B).copy(alpha = 0.2f))
                    .pointerInput(Unit) { detectTapGestures { onClear() } }
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "🗑 Clear Sticker",
                    color = Color(0xFFFF6B6B),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}