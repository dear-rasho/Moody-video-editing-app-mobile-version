package com.moody.moodyvideoeditor.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import com.moody.moodyvideoeditor.utils.FontLibrary

@Composable
fun FontPreviewRow(
    fontName: String,
    isActive: Boolean,
    onPick: () -> Unit
) {
    val meta = FontLibrary.metaFor(fontName)
    val family = FontLibrary.familyFor(fontName)

    val flag = meta?.flag ?: "🔤"
    val langLabel = meta?.language ?: "Custom"
    val sample = meta?.sampleText ?: "Aa Bb 123"
    val displayName = meta?.displayName ?: fontName

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (isActive) Color(0xFF2A1F4D) else Color(0xFF181818))
            .then(
                if (isActive) Modifier.border(
                    1.5.dp, Color(0xFF7C3AED), RoundedCornerShape(8.dp)
                ) else Modifier
            )
            .pointerInput(fontName) {
                detectTapGestures { onPick() }
            }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Language badge
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF0F0F0F))
                .padding(horizontal = 6.dp, vertical = 3.dp)
        ) {
            Text(
                "$flag $langLabel",
                color = Color(0xFFCCCCCC),
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }

        // Font name + sample (rendered in that font)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = displayName,
                color = Color.White,
                fontSize = 15.sp,
                fontFamily = family,
                maxLines = 1
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = sample,
                color = Color(0xFFAAAAAA),
                fontSize = 12.sp,
                fontFamily = family,
                maxLines = 1
            )
        }

        if (isActive) {
            Text(
                "✓",
                color = Color(0xFF7C3AED),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}