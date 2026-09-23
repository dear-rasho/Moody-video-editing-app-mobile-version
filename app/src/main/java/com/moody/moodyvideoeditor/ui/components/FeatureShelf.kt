package com.moody.moodyvideoeditor.ui.components

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

data class Feature(
    val key: String,
    val label: String,
    val icon: String
)

val FEATURES = listOf(
    Feature("trim", "Trim", "✂️"),
    Feature("speed", "Speed", "⏩"),
    Feature("text", "Text", "📝"),
    Feature("filters", "Filters", "🎨"),
    Feature("effects", "Effects", "✨"),
    Feature("adjustments", "Adjust", "🎚️"),
    Feature("stickers", "Stickers", "😀"),
    Feature("overlays", "Overlays", "🎬"),
    Feature("transform", "Transform", "🔲"),
    Feature("crop", "Crop", "📐"),
    Feature("volume", "Volume", "🔊"),
    Feature("ratio", "Ratio", "🖼️"),
    Feature("beats", "Beats", "🥁"),
    Feature("audioeffect", "Audio FX", "🎙️"),
    Feature("soundeffect", "Sound FX", "🔊"),
    Feature("duplicate", "Dup", "📋")
)

@Composable
fun FeatureShelf(
    onFeatureSelected: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0A0A0A))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = "Tools",
            color = Color(0xFF888888),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 4.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FEATURES.forEach { feature ->
                FeatureItem(
                    feature = feature,
                    onClick = { onFeatureSelected(feature.key) }
                )
            }
        }
    }
}

@Composable
fun FeatureItem(feature: Feature, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(72.dp)
            .height(72.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF181818))
            .pointerInput(feature.key) { detectTapGestures { onClick() } },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = feature.icon, fontSize = 22.sp)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = feature.label,
            color = Color.White,
            fontSize = 9.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}