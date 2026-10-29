package com.moody.moodyvideoeditor.ui.features

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import com.moody.moodyvideoeditor.data.TextState
import com.moody.moodyvideoeditor.ui.components.FeaturePanel

@Composable
fun TrackMattePanel(
    textState: TextState?,
    onPickTexture: () -> Unit,
    onTextStateChange: (TextState) -> Unit,
    onClose: () -> Unit
) {
    FeaturePanel(title = "🖼️ Track Matte", onClose = onClose) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 420.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (textState == null) {
                Text(
                    "Pehle timeline mein text layer select karein. Texture usi text par apply hogi.",
                    color = Color(0xFFFFCC80),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            } else {
                Text(
                    "Image ya video text ke andar texture ki tarah dikhai dega.",
                    color = Color(0xFF999999),
                    fontSize = 10.sp
                )
                ActionButton(
                    label = if (textState.textureUri == null) {
                        "＋ Choose Image / Video"
                    } else {
                        "＋ Change Image / Video"
                    },
                    onClick = onPickTexture
                )
                textState.textureUri?.let { uri ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            uri.substringAfterLast('/').takeLast(28),
                            color = Color.White,
                            fontSize = 10.sp,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            "Remove",
                            color = Color(0xFFFF7777),
                            fontSize = 10.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF241515))
                                .padding(horizontal = 10.dp, vertical = 7.dp)
                                .pointerInput(Unit) {
                                    detectTapGestures {
                                        onTextStateChange(textState.copy(textureUri = null))
                                    }
                                }
                        )
                    }
                    MatteSlider(
                        label = "Texture size",
                        value = textState.textureScale,
                        range = 10f..400f,
                        suffix = "%",
                        onValueChange = {
                            onTextStateChange(textState.copy(textureScale = it))
                        }
                    )
                    MatteSlider(
                        label = "Texture X",
                        value = textState.textureOffsetX,
                        range = -100f..100f,
                        suffix = "%",
                        onValueChange = {
                            onTextStateChange(textState.copy(textureOffsetX = it))
                        }
                    )
                    MatteSlider(
                        label = "Texture Y",
                        value = textState.textureOffsetY,
                        range = -100f..100f,
                        suffix = "%",
                        onValueChange = {
                            onTextStateChange(textState.copy(textureOffsetY = it))
                        }
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "Texture size aur X/Y position ko keyframe button se animate karein.",
                        color = Color(0xFF777777),
                        fontSize = 9.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(Color(0xFF7C3AED))
            .pointerInput(Unit) { detectTapGestures { onClick() } },
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun MatteSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    suffix: String,
    onValueChange: (Float) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, color = Color(0xFFAAAAAA), fontSize = 10.sp)
            Text("${value.toInt()}$suffix", color = Color.White, fontSize = 10.sp)
        }
        Slider(
            value = value.coerceIn(range),
            onValueChange = onValueChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFF7C3AED),
                activeTrackColor = Color(0xFF7C3AED),
                inactiveTrackColor = Color(0xFF303030)
            )
        )
    }
}
