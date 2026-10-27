package com.moody.moodyvideoeditor.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class Feature(val key: String, val label: String, val icon: String)

val FEATURES = listOf(
    Feature("spotlight", "Spotlight", "🔦"),
    Feature("matte", "Matte", "🎨"),
    Feature("chromakey", "Chroma", "🟩"),
    Feature("trim", "Trim", "✂️"),
    Feature("speed", "Speed", "⏩"),
    Feature("text", "Text", "📝"),
    Feature("blend", "Blend", "◐"),
    Feature("captions", "Auto Cap", "🎙️"),
    Feature("manual_captions", "Manual Cap", "✍️"),
    Feature("animations", "Animate", "🎞️"),
    Feature("keyframe_easing", "Keyframe Ease", "📈"),
    Feature("filters", "Filters", "🎨"),
    Feature("effects", "Effects", "✨"),
    Feature("adjustments", "Adjust", "🎚️"),
    Feature("wheel", "Wheels", "🎨"), // Icon text replaced by Canvas draw
    Feature("stickers", "Stickers", "😀"),
    Feature("overlays", "Overlays", "🎬"),
    Feature("transitions", "Transitions", "⇄"),
    Feature("mask", "Mask", "▣"),
    Feature("visualizer", "Visualizer", "🎵"),
    Feature("transform", "Transform", "🔲"),
    Feature("crop", "Crop", "📐"),
    Feature("volume", "Volume", "🔊"),
    Feature("audiofx", "Audio FX", "🎧"),
    Feature("beats", "Beats", "🥁"),

    Feature("freeze", "Freeze", "❄️"),
    Feature("ratio", "Ratio", "🖼️"),
    Feature("duplicate", "Dup", "📋"),
    Feature("delete", "Delete", "🗑"),
    Feature("save_template", "Save Tpl", "💾"),
    Feature("replace_media", "Replace", "🔄"),
    Feature("code", "Code", "💻"),
    Feature("export", "Export", "💾")
)

@Composable
fun FeatureShelf(
    onFeatureSelected: (String) -> Unit,
    scrollState: androidx.compose.foundation.ScrollState = rememberScrollState()
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0A0A0A))
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(
            "Tools",
            color = Color(0xFF888888),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 2.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FEATURES.forEach { feature ->
                FeatureItem(feature) { onFeatureSelected(feature.key) }
            }
        }
    }
}

@Composable
private fun FeatureItem(feature: Feature, onClick: () -> Unit) {
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
        if (feature.key == "mask") {
            Canvas(modifier = Modifier.size(22.dp)) {
                val stroke = Stroke(width = 1.8.dp.toPx())
                drawRoundRect(
                    color = Color.White,
                    topLeft = Offset(1.dp.toPx(), 1.dp.toPx()),
                    size = Size(20.dp.toPx(), 20.dp.toPx()),
                    cornerRadius = CornerRadius(2.dp.toPx()),
                    style = stroke
                )
                drawCircle(
                    color = Color.White,
                    radius = 6.dp.toPx(),
                    center = Offset(14.dp.toPx(), 8.dp.toPx()),
                    style = stroke
                )
                drawLine(
                    color = Color.White,
                    start = Offset(8.dp.toPx(), 14.dp.toPx()),
                    end = Offset(17.dp.toPx(), 14.dp.toPx()),
                    strokeWidth = stroke.width
                )
            }
        } else if (feature.key == "wheel") {
            Box(
                modifier = Modifier.size(22.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val radius = size.minDimension / 2f
                    val center = Offset(size.width / 2f, size.height / 2f)

                    // Pure Hue Wheel (No white center, just like your image)
                    val hueColors = listOf(
                        Color.Red,
                        Color.Yellow,
                        Color.Green,
                        Color.Cyan,
                        Color.Blue,
                        Color.Magenta,
                        Color.Red
                    )
                    val sweep = Brush.sweepGradient(colors = hueColors, center = center)
                    drawCircle(brush = sweep, radius = radius, center = center)
                }
            }
        } else if (feature.key == "crop") {
            Icon(
                imageVector = Icons.Filled.Crop,
                contentDescription = "Crop",
                tint = Color.White,
                modifier = Modifier.size(22.dp)
            )
        } else {
            // Normal emojis for other features
            Text(feature.icon, fontSize = 22.sp)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            feature.label,
            color = Color.White,
            fontSize = 9.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}