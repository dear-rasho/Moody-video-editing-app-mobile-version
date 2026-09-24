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
import com.moody.moodyvideoeditor.data.EffectLibrary
import com.moody.moodyvideoeditor.data.EffectPreset
import com.moody.moodyvideoeditor.ui.components.FeaturePanel

/**
 * Mirrors js/features/effect.js
 * 3 sections: Motion / Color Grade / Overlays
 */
@Composable
fun EffectsPanel(
    currentEffectKey: String?,       // currently applied preset (if any)
    hasClipSelected: Boolean,
    onPresetSelected: (EffectPreset) -> Unit,
    onRemoveEffect: () -> Unit,
    onClose: () -> Unit
) {
    FeaturePanel(title = "✨ Effects", onClose = onClose) {

        if (!hasClipSelected) {
            EmptyState(
                icon = "👆",
                title = "No clip selected",
                text = "Pehle timeline pe ek clip select karo."
            )
            return@FeaturePanel
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {

            // ═══ MOTION ═══
            SectionHeader("Motion (${EffectLibrary.MOTION_EFFECTS.size})")
            EffectShelf(
                presets = EffectLibrary.MOTION_EFFECTS,
                activeKey = currentEffectKey,
                onTap = onPresetSelected
            )

            // ═══ COLOR ═══
            SectionHeader("Color Grade (${EffectLibrary.COLOR_EFFECTS.size})")
            EffectShelf(
                presets = EffectLibrary.COLOR_EFFECTS,
                activeKey = currentEffectKey,
                onTap = onPresetSelected
            )

            // ═══ OVERLAYS ═══
            SectionHeader("Overlays (${EffectLibrary.OVERLAY_EFFECTS.size})")
            EffectShelf(
                presets = EffectLibrary.OVERLAY_EFFECTS,
                activeKey = currentEffectKey,
                onTap = onPresetSelected
            )

            // ═══ REMOVE ═══
            if (currentEffectKey != null) {
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFFF6B6B).copy(alpha = 0.15f))
                        .pointerInput(Unit) { detectTapGestures { onRemoveEffect() } },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "🗑 Remove \"$currentEffectKey\"",
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
private fun SectionHeader(text: String) {
    Text(
        text = text.uppercase(),
        color = Color(0xFF7C3AED),
        fontSize = 9.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp)
    )
}

@Composable
private fun EffectShelf(
    presets: List<EffectPreset>,
    activeKey: String?,
    onTap: (EffectPreset) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        presets.forEach { preset ->
            EffectCard(
                preset = preset,
                isActive = preset.key == activeKey,
                onClick = { onTap(preset) }
            )
        }
    }
}

@Composable
private fun EffectCard(
    preset: EffectPreset,
    isActive: Boolean,
    onClick: () -> Unit
) {
    val bg = if (isActive) Color(0xFF2A1F4D) else Color(0xFF181818)
    val iconBg = if (isActive) Color(0xFF7C3AED) else Color(0xFF222222)

    Column(
        modifier = Modifier
            .width(76.dp)
            .height(84.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .pointerInput(preset.key) { detectTapGestures { onClick() } }
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(iconBg),
            contentAlignment = Alignment.Center
        ) {
            Text(preset.icon, fontSize = 16.sp)
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