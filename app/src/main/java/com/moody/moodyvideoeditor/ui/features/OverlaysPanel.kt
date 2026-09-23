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

@Composable
fun OverlaysPanel(
    currentOverlay: String,
    onOverlaySelected: (String) -> Unit,
    onClose: () -> Unit
) {
    val overlays = listOf(
        Triple("none", "None", "∅"),
        Triple("rain", "Rain", "🌧️"),
        Triple("snow", "Snow", "❄️"),
        Triple("dust", "Dust", "🌫️"),
        Triple("sparks", "Sparks", "✨"),
        Triple("stars", "Stars", "⭐"),
        Triple("bokeh", "Bokeh", "🔮"),
        Triple("fog", "Fog", "🌁"),
        Triple("smoke", "Smoke", "💨"),
        Triple("noise", "Noise", "📡"),
        Triple("lightLeak", "Leak", "🌅"),
        Triple("lensFlare", "Flare", "💡"),
        Triple("vignette", "Vignette", "🕳️"),
        Triple("blackBars", "Bars", "📺"),
        Triple("flicker", "Flicker", "🕯️")
    )

    FeaturePanel(title = "🎬 Overlays", onClose = onClose) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            overlays.forEach { (key, label, icon) ->
                val isActive = currentOverlay == key
                Column(
                    modifier = Modifier
                        .width(60.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isActive) Color(0xFF7C3AED) else Color(0xFF181818))
                        .pointerInput(key) { detectTapGestures { onOverlaySelected(key) } }
                        .padding(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = icon, fontSize = 20.sp)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = label,
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                }
            }
        }
    }
}