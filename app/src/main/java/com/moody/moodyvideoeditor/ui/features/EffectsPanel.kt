package com.moody.moodyvideoeditor.ui.features

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moody.moodyvideoeditor.ui.components.FeaturePanel

data class EffectOption(val key: String, val label: String, val icon: String)

@Composable
fun EffectsPanel(
    currentEffect: String,
    onEffectSelected: (String) -> Unit,
    onClose: () -> Unit
) {
    val motionEffects = listOf(
        EffectOption("none", "None", "∅"),
        EffectOption("shake", "Shake", "📳"),
        EffectOption("tremor", "Tremor", "💥"),
        EffectOption("quake", "Quake", "🌋"),
        EffectOption("bounce", "Bounce", "🏀"),
        EffectOption("punch", "Punch", "🥊"),
        EffectOption("pulse", "Pulse", "💓"),
        EffectOption("heartbeat", "Heart", "❤️"),
        EffectOption("zoom", "Zoom", "🔍"),
        EffectOption("rush", "Rush", "⚡"),
        EffectOption("wobble", "Wobble", "🔄"),
        EffectOption("spin", "Spin", "🌪️"),
        EffectOption("glitch", "Glitch", "⚡"),
        EffectOption("vhs", "VHS", "📼"),
        EffectOption("flicker", "Flicker", "🕯️"),
        EffectOption("strobe", "Strobe", "💡")
    )

    val colorEffects = listOf(
        EffectOption("warm", "Warm", "🌅"),
        EffectOption("cool", "Cool", "❄️"),
        EffectOption("vintage", "Vintage", "📼"),
        EffectOption("cinematic", "Cine", "🎬"),
        EffectOption("bw", "B&W", "⚫"),
        EffectOption("dreamy", "Dreamy", "💭"),
        EffectOption("vivid", "Vivid", "🎨"),
        EffectOption("noir", "Noir", "🖤")
    )

    FeaturePanel(title = "✨ Effects", onClose = onClose) {
        Text(
            text = "Motion",
            color = Color(0xFF888888),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 4.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            motionEffects.forEach { fx ->
                EffectChip(fx, currentEffect == fx.key) { onEffectSelected(fx.key) }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Color",
            color = Color(0xFF888888),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 4.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            colorEffects.forEach { fx ->
                EffectChip(fx, currentEffect == fx.key) { onEffectSelected(fx.key) }
            }
        }
    }
}

@Composable
private fun EffectChip(fx: EffectOption, isActive: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(56.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isActive) Color(0xFF7C3AED) else Color(0xFF181818))
            .pointerInput(fx.key) { detectTapGestures { onClick() } }
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = fx.icon, fontSize = 18.sp)
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = fx.label,
            color = Color.White,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}