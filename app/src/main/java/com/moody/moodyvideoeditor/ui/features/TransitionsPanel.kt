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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import com.moody.moodyvideoeditor.data.TransitionLibrary
import com.moody.moodyvideoeditor.data.TransitionState
import com.moody.moodyvideoeditor.ui.components.FeaturePanel

/**
 * Mirrors js/features/transitions.js
 * Transition stored on the RIGHT clip of a pair.
 */
@Composable
fun TransitionsPanel(
    current: TransitionState,
    hasPairAvailable: Boolean,
    hintText: String,
    onTransitionChanged: (TransitionState) -> Unit,
    onRemove: () -> Unit,
    onClose: () -> Unit
) {
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

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {

            // ─── CURRENT STATUS ─────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF181818))
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Current:",
                    color = Color(0xFF888888),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(70.dp)
                )
                Text(
                    TransitionLibrary.find(current.key)?.label ?: "None",
                    color = if (current.isActive) Color(0xFF7C3AED) else Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // ─── PRESET SHELF ───────────────────────
            Text(
                "Pick Transition",
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
                TransitionLibrary.PRESETS.forEach { preset ->
                    val isActive = current.key == preset.key
                    Column(
                        modifier = Modifier
                            .width(72.dp)
                            .height(78.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isActive) Color(0xFF2A1F4D) else Color(0xFF181818))
                            .pointerInput(preset.key) {
                                detectTapGestures {
                                    if (preset.key == "none") onRemove()
                                    else onTransitionChanged(current.copy(key = preset.key))
                                }
                            }
                            .padding(6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .height(32.dp)
                                .width(32.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (isActive) Color(0xFF7C3AED) else Color(0xFF222222)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(preset.icon, fontSize = 15.sp)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            preset.label,
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            maxLines = 2
                        )
                    }
                }
            }

            // ─── DURATION SLIDER ────────────────────
            if (current.isActive) {
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
                            onTransitionChanged(current.copy(durationMs = (it * 1000f).toLong()))
                        },
                        valueRange = 0.2f..2.5f,
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF7C3AED),
                            activeTrackColor = Color(0xFF7C3AED),
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

                Spacer(Modifier.height(4.dp))

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
                        "🗑 Remove Transition",
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
        Text(text, color = Color(0xFF888888), fontSize = 10.sp, textAlign = TextAlign.Center)
    }
}