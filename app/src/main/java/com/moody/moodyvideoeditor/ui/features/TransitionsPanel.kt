package com.moody.moodyvideoeditor.ui.features

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import com.moody.moodyvideoeditor.data.TransitionLibrary
import com.moody.moodyvideoeditor.data.TransitionState
import com.moody.moodyvideoeditor.ui.components.FeaturePanel

private data class TransitionCategory(
    val key: String,
    val label: String,
    val icon: String,
    val presets: List<String>
)

private val CATEGORIES = listOf(
    TransitionCategory(
        "fade", "Fade", "◐",
        listOf("fade", "dissolve", "fadeBlack", "fadeWhite", "blur")
    ),
    TransitionCategory(
        "slide", "Slide", "➡️",
        listOf("slideLeft", "slideRight", "slideUp", "slideDown")
    ),
    TransitionCategory(
        "push", "Push", "⬅️",
        listOf("pushLeft", "pushRight", "pushUp", "pushDown")
    ),
    TransitionCategory(
        "wipe", "Wipe", "▷",
        listOf("wipeLeft", "wipeRight", "wipeUp", "wipeDown")
    ),
    TransitionCategory(
        "shapes", "Shapes", "◯",
        listOf("circleIn", "irisBox", "clockWipe")
    ),
    TransitionCategory(
        "zoom", "Zoom", "🔍",
        listOf("zoomIn", "zoomOut", "crossZoom")
    ),
    TransitionCategory(
        "spin", "Spin", "🌀",
        listOf("spinCW", "spinCCW", "swirl")
    ),
    TransitionCategory(
        "fx", "FX", "⚡",
        listOf("rgbSplit", "glitch", "flashWhite")
    )
)

@Composable
fun TransitionsPanel(
    current: TransitionState,
    hasPairAvailable: Boolean,
    hintText: String,
    onTransitionChanged: (TransitionState) -> Unit,
    onRemove: () -> Unit,
    onClose: () -> Unit
) {
    var activeCategory by remember { mutableStateOf("fade") }

    FeaturePanel(title = "⇄ Transitions", onClose = onClose) {

        if (!hasPairAvailable) {
            EmptyState(
                icon = "⇄",
                title = "No transition target",
                text = hintText.ifBlank {
                    "Select a clip with a neighboring clip on the same track to add a transition."
                }
            )
            return@FeaturePanel
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 220.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {

            // ─── CURRENT STATUS ───
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF181818))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Current:",
                    color = Color(0xFF888888),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(60.dp)
                )
                Text(
                    TransitionLibrary.find(current.key)?.label ?: "None",
                    color = if (current.isActive) Color(0xFFA855F7) else Color(0xFF666666),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                if (current.isActive) {
                    Box(
                        modifier = Modifier
                            .height(24.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFFFF3B3B).copy(alpha = 0.2f))
                            .pointerInput(Unit) {
                                detectTapGestures { onRemove() }
                            }
                            .padding(horizontal = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "✕ Remove",
                            color = Color(0xFFFF3B3B),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // ─── INFO ───
            Text(
                "📌 Transition applies at the START of the selected clip.",
                color = Color(0xFF666666),
                fontSize = 9.sp,
                modifier = Modifier.padding(horizontal = 4.dp)
            )

            // ─── CATEGORY ───
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
                CATEGORIES.forEach { cat ->
                    val isActive = cat.key == activeCategory
                    Row(
                        modifier = Modifier
                            .height(30.dp)
                            .clip(RoundedCornerShape(15.dp))
                            .background(
                                if (isActive) Color(0xFF7C3AED) else Color(0xFF181818)
                            )
                            .pointerInput(cat.key) {
                                detectTapGestures { activeCategory = cat.key }
                            }
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

            // ─── PRESET GRID ───
            val cat = CATEGORIES.firstOrNull { it.key == activeCategory } ?: CATEGORIES[0]

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                cat.presets.chunked(3).forEach { rowPresets ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        rowPresets.forEach { presetKey ->
                            val preset = TransitionLibrary.find(presetKey)
                            val isActive = current.key == presetKey
                            TransitionCard(
                                icon = preset?.icon ?: "◐",
                                label = preset?.label ?: presetKey,
                                isActive = isActive,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    if (isActive) {
                                        onRemove()
                                    } else {
                                        onTransitionChanged(
                                            current.copy(key = presetKey)
                                        )
                                    }
                                }
                            )
                        }
                        repeat(3 - rowPresets.size) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }

            // ─── DURATION SLIDER ───
            if (current.isActive) {
                Spacer(Modifier.height(2.dp))
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
                        modifier = Modifier.width(64.dp)
                    )
                    Slider(
                        value = current.durationMs / 1000f,
                        onValueChange = {
                            onTransitionChanged(
                                current.copy(
                                    durationMs = (it * 1000f).toLong()
                                        .coerceIn(200L, 3000L)
                                )
                            )
                        },
                        valueRange = 0.2f..3.0f,
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFFA855F7),
                            activeTrackColor = Color(0xFFA855F7),
                            inactiveTrackColor = Color(0xFF303030)
                        )
                    )
                    Text(
                        String.format("%.2fs", current.durationMs / 1000f),
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(44.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun TransitionCard(
    icon: String,
    label: String,
    isActive: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .height(64.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (isActive) Color(0xFF2A1F4D) else Color(0xFF181818)
            )
            .then(
                if (isActive) {
                    Modifier.border(
                        width = 2.dp,
                        color = Color(0xFFA855F7),
                        shape = RoundedCornerShape(10.dp)
                    )
                } else Modifier
            )
            .pointerInput(label) {
                detectTapGestures { onClick() }
            }
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(
                    if (isActive) Color(0xFFA855F7) else Color(0xFF222222)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(icon, fontSize = 13.sp)
        }
        Spacer(Modifier.height(3.dp))
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
        Text(
            text,
            color = Color(0xFF888888),
            fontSize = 10.sp,
            textAlign = TextAlign.Center
        )
    }
}