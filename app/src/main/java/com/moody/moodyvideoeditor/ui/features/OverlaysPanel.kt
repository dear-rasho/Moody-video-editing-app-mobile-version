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
import com.moody.moodyvideoeditor.data.OverlayLibrary
import com.moody.moodyvideoeditor.data.OverlayState
import com.moody.moodyvideoeditor.ui.components.FeaturePanel

/**
 * Mirrors js/features/overlays.js
 * Category shelf → preset grid → intensity + color.
 */
@Composable
fun OverlaysPanel(
    current: OverlayState,
    hasClipSelected: Boolean,
    onStateChanged: (OverlayState) -> Unit,
    onRemove: () -> Unit,
    onClose: () -> Unit
) {
    FeaturePanel(title = "🎬 Overlays", onClose = onClose) {

        if (!hasClipSelected) {
            EmptyState(
                icon = "👆",
                title = "No clip selected",
                text = "Pehle timeline pe ek clip select karo."
            )
            return@FeaturePanel
        }

        var selectedCategoryKey by remember { mutableStateOf(OverlayLibrary.CATEGORIES[0].key) }
        val currentCategory =
            OverlayLibrary.CATEGORIES.firstOrNull { it.key == selectedCategoryKey }
                ?: OverlayLibrary.CATEGORIES[0]

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {

            // ─── CATEGORY SHELF ────────────────────────
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
                OverlayLibrary.CATEGORIES.forEach { cat ->
                    val isActive = cat.key == selectedCategoryKey
                    Box(
                        modifier = Modifier
                            .height(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isActive) Color(0xFF7C3AED) else Color(0xFF181818))
                            .pointerInput(cat.key) {
                                detectTapGestures { selectedCategoryKey = cat.key }
                            }
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "${cat.label} (${cat.presets.size})",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // ─── PRESET GRID ──────────────────────────
            Text(
                currentCategory.label,
                color = Color(0xFF7C3AED),
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
                currentCategory.presets.forEach { preset ->
                    val isActive = current.type == preset.key
                    OverlayCard(
                        icon = preset.icon,
                        label = preset.label,
                        isActive = isActive,
                        onClick = {
                            if (isActive) {
                                // Toggle off
                                onStateChanged(OverlayState(type = "none"))
                            } else {
                                onStateChanged(
                                    OverlayState(
                                        type = preset.key,
                                        intensity = preset.defaultIntensity,
                                        color = preset.defaultColor
                                    )
                                )
                            }
                        }
                    )
                }
            }

            // ─── INTENSITY + COLOR (only if active) ───
            if (current.isActive) {
                Spacer(Modifier.height(2.dp))

                // Intensity slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Intensity",
                        color = Color(0xFF888888),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(64.dp)
                    )
                    Slider(
                        value = current.intensity,
                        onValueChange = { onStateChanged(current.copy(intensity = it)) },
                        valueRange = 0f..200f,
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF7C3AED),
                            activeTrackColor = Color(0xFF7C3AED),
                            inactiveTrackColor = Color(0xFF303030)
                        )
                    )
                    Text(
                        "${current.intensity.toInt()}%",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(42.dp)
                    )
                }

                // Color picker row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Color",
                        color = Color(0xFF888888),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(64.dp)
                    )
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val presets = listOf(
                            0xFFFFFFFF, 0xFFFFD166, 0xFFFF6B6B, 0xFF22C55E,
                            0xFF60EFFF, 0xFFFF00FF, 0xFF7C3AED, 0xFF000000
                        )
                        presets.forEach { c ->
                            val isSelected = current.color == c
                            Box(
                                modifier = Modifier
                                    .size(if (isSelected) 30.dp else 26.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(c))
                                    .pointerInput(c) {
                                        detectTapGestures {
                                            onStateChanged(current.copy(color = c))
                                        }
                                    }
                            )
                        }
                    }
                }

                // Remove button
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
                        "🗑 Remove Overlay",
                        color = Color(0xFFFF6B6B),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun OverlayCard(
    icon: String,
    label: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    val bg = if (isActive) Color(0xFF2A1F4D) else Color(0xFF181818)
    val iconBg = if (isActive) Color(0xFF7C3AED) else Color(0xFF222222)

    Column(
        modifier = Modifier
            .width(72.dp)
            .height(78.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .pointerInput(label) { detectTapGestures { onClick() } }
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(iconBg),
            contentAlignment = Alignment.Center
        ) {
            Text(icon, fontSize = 15.sp)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            color = Color.White,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2
        )
    }
}

@Composable
private fun EmptyState(icon: String, title: String, text: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF181818))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(icon, fontSize = 24.sp)
        Text(title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text(text, color = Color(0xFF888888), fontSize = 10.sp)
    }
}