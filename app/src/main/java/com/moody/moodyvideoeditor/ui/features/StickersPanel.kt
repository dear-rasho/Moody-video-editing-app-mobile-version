package com.moody.moodyvideoeditor.ui.features

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moody.moodyvideoeditor.data.StickerLibrary
import com.moody.moodyvideoeditor.data.StickerState
import com.moody.moodyvideoeditor.ui.components.FeaturePanel
import com.moody.moodyvideoeditor.utils.AnimationsEngine

@Composable
fun StickersPanel(
    current: StickerState,
    hasStickerSelected: Boolean,
    playheadMs: Long,
    onStickerChanged: (StickerState) -> Unit,
    onEmojiTapped: (String) -> Unit,
    onRemove: () -> Unit,
    onClose: () -> Unit
) {
    var selectedCategoryKey by remember { mutableStateOf(StickerLibrary.CATEGORIES[0].key) }
    var subView by remember { mutableStateOf("emoji") }

    FeaturePanel(title = "😀 Stickers", onClose = onClose) {

        when (subView) {
            "emoji" -> EmojiView(
                selectedCategoryKey = selectedCategoryKey,
                onCategorySelected = { selectedCategoryKey = it },
                currentEmoji = current.emoji,
                onEmojiTapped = onEmojiTapped,
                onRemove = onRemove,
                hasSticker = hasStickerSelected,
                onGotoPosition = { subView = "position" },
                onGotoScale = { subView = "scale" },
                onGotoRotation = { subView = "rotation" },
                onGotoAnimation = { subView = "animation" }
            )

            "position" -> SliderSubView(
                title = "Position",
                back = { subView = "emoji" },
                onClose = onClose
            ) {
                SliderRow("X (%)", current.x, 0f..100f) { v ->
                    onStickerChanged(current.copy(x = v))
                }
                SliderRow("Y (%)", current.y, 0f..100f) { v ->
                    onStickerChanged(current.copy(y = v))
                }
            }

            "scale" -> SliderSubView(
                title = "Scale",
                back = { subView = "emoji" },
                onClose = onClose
            ) {
                SliderRow("Size (%)", current.scale, 10f..500f) { v ->
                    onStickerChanged(current.copy(scale = v))
                }
            }

            "rotation" -> SliderSubView(
                title = "Rotation",
                back = { subView = "emoji" },
                onClose = onClose
            ) {
                SliderRow("Angle (°)", current.rotation, -180f..180f) { v ->
                    onStickerChanged(current.copy(rotation = v))
                }
            }

            // 🆕 Animation sub-view
            "animation" -> AnimationSubView(
                current = current,
                onChanged = onStickerChanged,
                onBack = { subView = "emoji" }
            )
        }
    }
}


//  EMOJI VIEW

@Composable
private fun EmojiView(
    selectedCategoryKey: String,
    onCategorySelected: (String) -> Unit,
    currentEmoji: String,
    onEmojiTapped: (String) -> Unit,
    onRemove: () -> Unit,
    hasSticker: Boolean,
    onGotoPosition: () -> Unit,
    onGotoScale: () -> Unit,
    onGotoRotation: () -> Unit,
    onGotoAnimation: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            "Category",
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
            StickerLibrary.CATEGORIES.forEach { cat ->
                val isActive = cat.key == selectedCategoryKey
                Column(
                    modifier = Modifier
                        .width(64.dp)
                        .height(58.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isActive) Color(0xFF7C3AED) else Color(0xFF181818))
                        .pointerInput(cat.key) {
                            detectTapGestures { onCategorySelected(cat.key) }
                        }
                        .padding(4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(cat.icon, fontSize = 20.sp)
                    Text(
                        cat.label,
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                }
            }
        }

        val category = StickerLibrary.CATEGORIES.firstOrNull { it.key == selectedCategoryKey }
            ?: StickerLibrary.CATEGORIES[0]

        Text(
            "${category.label} (${category.emojis.size})",
            color = Color(0xFF7C3AED),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 4.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            category.emojis.forEach { emoji ->
                val isActive = currentEmoji == emoji
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isActive) Color(0xFF7C3AED) else Color(0xFF181818))
                        .pointerInput(emoji) {
                            detectTapGestures { onEmojiTapped(emoji) }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(emoji, fontSize = 22.sp)
                }
            }
        }

        if (hasSticker) {
            Spacer(Modifier.height(2.dp))
            Text(
                "Adjust Sticker",
                color = Color(0xFF888888),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 4.dp)
            )

            // 🆕 4 buttons now (added Animate)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                PropertyButton("📍", "Position", Modifier.weight(1f), onGotoPosition)
                PropertyButton("🔍", "Scale", Modifier.weight(1f), onGotoScale)
                PropertyButton("🔄", "Rotate", Modifier.weight(1f), onGotoRotation)
                PropertyButton("🎞️", "Animate", Modifier.weight(1f), onGotoAnimation)
            }

            Spacer(Modifier.height(2.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFFF6B6B).copy(alpha = 0.15f))
                    .pointerInput(Unit) { detectTapGestures { onRemove() } },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "🗑 Remove Sticker",
                    color = Color(0xFFFF6B6B),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}


//  🆕 ANIMATION SUB-VIEW

@Composable
private fun AnimationSubView(
    current: StickerState,
    onChanged: (StickerState) -> Unit,
    onBack: () -> Unit
) {
    var activeCategory by remember { mutableStateOf("basic") }
    val categories = AnimationsEngine.CATEGORIES

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 280.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Header with back
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF181818))
                    .pointerInput(Unit) { detectTapGestures { onBack() } },
                contentAlignment = Alignment.Center
            ) {
                Text("‹", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(8.dp))
            Text(
                "🎞️ Animation (${AnimationsEngine.ALL_ANIMATIONS.size})",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // Category chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            categories.forEach { cat ->
                val isActive = activeCategory == cat.key
                Box(
                    modifier = Modifier
                        .height(30.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isActive) Color(0xFF7C3AED) else Color(0xFF181818))
                        .pointerInput(cat.key) {
                            detectTapGestures { activeCategory = cat.key }
                        }
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "${cat.label} (${cat.animations.size})",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Animation chips
        val cat = categories.firstOrNull { it.key == activeCategory } ?: categories[0]
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 140.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            cat.animations.chunked(3).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    row.forEach { anim ->
                        val isActive = current.animation == anim.key
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isActive) Color(0xFF7C3AED) else Color(0xFF181818)
                                )
                                .pointerInput(anim.key) {
                                    detectTapGestures {
                                        onChanged(current.copy(animation = anim.key))
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                anim.label,
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                maxLines = 2
                            )
                        }
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }

        // Duration slider
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "Duration",
                color = Color(0xFF888888),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(60.dp)
            )
            Slider(
                value = current.animationDuration.coerceIn(0.2f, 5f),
                onValueChange = { onChanged(current.copy(animationDuration = it)) },
                valueRange = 0.2f..5f,
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFF7C3AED),
                    activeTrackColor = Color(0xFF7C3AED),
                    inactiveTrackColor = Color(0xFF303030)
                )
            )
            Text(
                String.format("%.1fs", current.animationDuration),
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(40.dp)
            )
        }

        // Opacity slider
        SliderRow("Opacity", current.opacity, 0f..100f) { v ->
            onChanged(current.copy(opacity = v))
        }
    }
}

@Composable
private fun PropertyButton(icon: String, label: String, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier = modifier
            .height(50.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF181818))
            .pointerInput(label) { detectTapGestures { onClick() } },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(icon, fontSize = 16.sp)
        Text(label, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SliderSubView(
    title: String,
    back: () -> Unit,
    onClose: () -> Unit,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF181818))
                    .pointerInput(Unit) { detectTapGestures { back() } },
                contentAlignment = Alignment.Center
            ) {
                Text("‹", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(8.dp))
            Text(title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        content()
    }
}

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            label,
            color = Color(0xFF888888),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(58.dp)
        )
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = { onChange(it) },
            valueRange = range,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFF7C3AED),
                activeTrackColor = Color(0xFF7C3AED),
                inactiveTrackColor = Color(0xFF303030)
            )
        )
        Text(
            String.format("%.0f", value),
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(38.dp)
        )
    }
}