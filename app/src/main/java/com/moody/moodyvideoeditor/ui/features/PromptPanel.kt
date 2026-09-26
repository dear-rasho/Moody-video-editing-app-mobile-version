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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moody.moodyvideoeditor.ui.components.FeaturePanel

private data class Cat(
    val label: String,
    val icon: String,
    val examples: List<String>
)

private val CATEGORIES = listOf(
    Cat(
        "Fonts", "🔤", listOf(
            "font handwriting size 24",
            "font titles size 60",
            "font \"Playfair Display\" size 48",
            "font Impact size 72"
        )
    ),
    Cat(
        "Align", "🎯", listOf(
            "align left", "align center", "align right"
        )
    ),
    Cat(
        "Anchor", "⚓", listOf(
            "anchor top-left", "anchor center",
            "anchor bottom-right", "anchor 30 70"
        )
    ),
    Cat(
        "Timeline", "🎬", listOf(
            "[00:00 - 00:05] \"Welcome\" animation typewriter",
            "tighten track"
        )
    ),
    Cat(
        "Adjust", "🎚️", listOf(
            "brightness 120", "contrast 110", "saturation 150",
            "shadows 30", "highlights -15",
            "temperature 30", "tint -20",
            "reds 50", "blues -40", "lal 30", "hara -20"
        )
    ),
    Cat(
        "Color", "🎨", listOf(
            "temperature 40", "tint -20",
            "vibrance 50", "clarity 30"
        )
    ),
    Cat(
        "Wheel", "🌈", listOf(
            "shadows red 50 40", "midtones blue 40 50",
            "highlights green 30 60", "hdr 120"
        )
    ),
    Cat(
        "Filters", "🔍", listOf(
            "grayscale", "sepia 80", "invert 100",
            "blur 5", "hue 90"
        )
    ),
    Cat(
        "Effects", "✨", listOf(
            "vintage", "cinematic", "warm", "cool", "bw",
            "dreamy", "vivid", "faded", "dramatic", "noir",
            "shake", "pulse", "glitch"
        )
    ),
    Cat(
        "Transform", "🔲", listOf(
            "scale 150", "rotation 45", "positionx 30",
            "positiony 70", "cropL 10", "cropR 10"
        )
    ),
    Cat(
        "Anim", "🎞️", listOf(
            "animation typewriter", "animation fadeIn",
            "animation popIn", "animation slideLeft",
            "animation bounceIn", "animation zoomIn",
            "animation glitch", "animation pulse"
        )
    ),
    Cat(
        "Transition", "⇄", listOf(
            "fade in 0.5", "dissolve 0.8", "slide left 0.6",
            "transition all fade 0.5",
            "transition at 3 dissolve 0.8",
            "layer v1 transitions dissolve, slide left, zoom in",
            "layer v1 transitions dissolve, null, zoom in loop"
        )
    ),
    Cat(
        "Chroma", "🟢", listOf(
            "green screen", "chroma #00ff00",
            "chroma #0000ff"
        )
    ),
    Cat(
        "Speed", "⏩", listOf(
            "speed 0.5x", "speed 1.5x", "speed 2x", "speed 4x"
        )
    ),
    Cat(
        "Trim", "✂️", listOf(
            "split", "trim left", "trim right"
        )
    ),
    Cat(
        "Text", "📝", listOf(
            "text \"Hello\"",
            "text \"Welcome\" size 48 color #ff0066",
            "text \"Moody\" size 72 animation typewriter",
            "text \"Hello\" size 48 bold italic"
        )
    ),
    Cat(
        "Sticker", "😀", listOf(
            "sticker 😀", "sticker 🔥", "sticker ⭐",
            "sticker ❤️", "sticker 🎬"
        )
    ),
    Cat(
        "Audio", "🔊", listOf(
            "audio echo", "audio reverb", "audio robot",
            "audio underwater", "audio studio"
        )
    ),
    Cat(
        "Special", "⚙️", listOf(
            "ratio 9:16", "ratio 16:9", "ratio 1:1",
            "tighten track", "clear keyframes"
        )
    )
)

@Composable
fun PromptPanel(
    feedback: String,
    feedbackType: String,
    onApply: (String) -> Unit,
    onClear: () -> Unit,
    onClose: () -> Unit
) {
    var promptText by remember { mutableStateOf("") }
    var activeCategory by remember { mutableStateOf(0) }

    FeaturePanel(title = "💻 Code Mode", onClose = onClose) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 280.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {

            // CATEGORY SHELF
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                CATEGORIES.forEachIndexed { idx, cat ->
                    val isActive = idx == activeCategory
                    Row(
                        modifier = Modifier
                            .height(30.dp)
                            .clip(RoundedCornerShape(15.dp))
                            .background(if (isActive) Color(0xFF7C3AED) else Color(0xFF181818))
                            .pointerInput(idx) { detectTapGestures { activeCategory = idx } }
                            .padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(cat.icon, fontSize = 11.sp)
                        Text(
                            cat.label,
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // EXAMPLES
            val group = CATEGORIES[activeCategory]
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                group.examples.forEach { ex ->
                    Box(
                        modifier = Modifier
                            .height(28.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF1A1A1A))
                            .pointerInput(ex) {
                                detectTapGestures {
                                    promptText = if (promptText.isBlank()) ex
                                    else "$promptText, $ex"
                                }
                            }
                            .padding(horizontal = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            ex,
                            color = Color(0xFFAAAAAA),
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            // TEXT INPUT
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 60.dp, max = 140.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF0F0F0F))
                    .padding(10.dp)
            ) {
                if (promptText.isBlank()) {
                    Text(
                        "Prompt likho… e.g. brightness 120, text \"Hello\", warm",
                        color = Color(0xFF555555),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
                BasicTextField(
                    value = promptText,
                    onValueChange = { promptText = it },
                    textStyle = TextStyle(
                        color = Color.White,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    ),
                    cursorBrush = SolidColor(Color(0xFF7C3AED)),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // ACTION ROW
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF181818))
                        .pointerInput(Unit) {
                            detectTapGestures {
                                promptText = ""
                                onClear()
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Clear",
                        color = Color(0xFFAAAAAA),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(2f)
                        .height(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (promptText.isNotBlank()) Color(0xFF7C3AED)
                            else Color(0xFF3A2A5D)
                        )
                        .pointerInput(promptText) {
                            detectTapGestures {
                                if (promptText.isNotBlank()) {
                                    onApply(promptText)
                                    promptText = ""   // 🆕 clear after apply
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "✨ Apply Prompt",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // FEEDBACK
            if (feedback.isNotBlank() && feedbackType != "none") {
                val bgColor = when (feedbackType) {
                    "success" -> Color(0xFF0F2A1A)
                    "warning" -> Color(0xFF2A2415)
                    "error" -> Color(0xFF2A0F0F)
                    else -> Color(0xFF181818)
                }
                val textColor = when (feedbackType) {
                    "success" -> Color(0xFF22C55E)
                    "warning" -> Color(0xFFF59E0B)
                    "error" -> Color(0xFFFF6B6B)
                    else -> Color(0xFF888888)
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(bgColor)
                        .padding(10.dp)
                ) {
                    Text(
                        feedback,
                        color = textColor,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 14.sp
                    )
                }
            }
        }
    }
}