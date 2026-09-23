package com.moody.moodyvideoeditor.ui.features

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
fun TextPanel(
    currentText: String,
    currentColor: Color,
    currentSize: Int,
    onTextChanged: (String, Color, Int) -> Unit,
    onClear: () -> Unit,
    onClose: () -> Unit
) {
    var inputText by remember { mutableStateOf(currentText) }
    var selectedColor by remember { mutableStateOf(currentColor) }
    var selectedSize by remember { mutableStateOf(currentSize) }

    val colors = listOf(
        Color.White, Color.Yellow, Color.Red, Color.Green,
        Color.Cyan, Color.Magenta, Color(0xFFFFD166), Color(0xFF7C3AED)
    )
    val sizes = listOf(16, 24, 32, 48, 64)

    FeaturePanel(title = "📝 Text Overlay", onClose = onClose) {
        // Text input
        OutlinedTextField(
            value = inputText,
            onValueChange = {
                inputText = it
                onTextChanged(it, selectedColor, selectedSize)
            },
            placeholder = { Text("Type text…", color = Color(0xFF666666), fontSize = 12.sp) },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = Color(0xFF7C3AED),
                unfocusedBorderColor = Color(0xFF303030)
            ),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(6.dp))

        // Colors
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Color",
                color = Color(0xFF888888),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(36.dp)
            )
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                colors.forEach { c ->
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(c)
                            .pointerInput(c) {
                                detectTapGestures {
                                    selectedColor = c
                                    onTextChanged(inputText, c, selectedSize)
                                }
                            }
                    )
                }
            }
        }

        // Sizes
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Size",
                color = Color(0xFF888888),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(36.dp)
            )
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                sizes.forEach { sz ->
                    val isActive = selectedSize == sz
                    Box(
                        modifier = Modifier
                            .height(28.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isActive) Color(0xFF7C3AED) else Color(0xFF181818))
                            .pointerInput(sz) {
                                detectTapGestures {
                                    selectedSize = sz
                                    onTextChanged(inputText, selectedColor, sz)
                                }
                            }
                            .padding(horizontal = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "$sz",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Clear button
        if (currentText.isNotBlank()) {
            Box(
                modifier = Modifier
                    .height(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFFF6B6B).copy(alpha = 0.2f))
                    .pointerInput(Unit) { detectTapGestures { onClear() } }
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "🗑 Clear Text",
                    color = Color(0xFFFF6B6B),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}