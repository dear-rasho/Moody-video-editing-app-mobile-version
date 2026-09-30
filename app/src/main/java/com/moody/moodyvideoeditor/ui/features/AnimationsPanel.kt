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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moody.moodyvideoeditor.ui.components.FeaturePanel
import com.moody.moodyvideoeditor.utils.AnimationsEngine

/**
 * Mirrors js/features/animations.js
 * Standalone animations panel — text clip ke animation property ko set karta hai.
 */
@Composable
fun AnimationsPanel(
    currentAnimation: String,
    currentDuration: Float,
    hasTextClipSelected: Boolean,
    onAnimationSelected: (String) -> Unit,
    onDurationChanged: (Float) -> Unit,
    onPreview: () -> Unit,
    onClose: () -> Unit
) {
    FeaturePanel(
        title = "🎞️ Animations (${AnimationsEngine.ALL_ANIMATIONS.size})",
        onClose = onClose
    ) {
        if (!hasTextClipSelected) {
            EmptyState(
                icon = "📝",
                title = "No text clip selected",
                text = "Pehle Text panel se ek text clip add karo, phir yahan animation lagao."
            )
            return@FeaturePanel
        }

        var selectedCategory by remember { mutableStateOf(AnimationsEngine.CATEGORIES[0].key) }
        val currentCategory = AnimationsEngine.CATEGORIES.firstOrNull { it.key == selectedCategory }
            ?: AnimationsEngine.CATEGORIES[0]

        // ─── CATEGORY SHELF ─────────────────────────────
        Text(
            "Category",
            color = Color(0xFF888888),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 4.dp)
        )
        Spacer(Modifier.height(4.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            AnimationsEngine.CATEGORIES.forEach { cat ->
                val isActive = cat.key == selectedCategory
                Box(
                    modifier = Modifier
                        .height(30.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isActive) Color(0xFF7C3AED) else Color(0xFF181818))
                        .pointerInput(cat.key) { detectTapGestures { selectedCategory = cat.key } }
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

        Spacer(Modifier.height(8.dp))

        // ─── ANIMATION CHIPS ─────────────────────────────
        Text(
            currentCategory.label,
            color = Color(0xFF7C3AED),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 4.dp)
        )
        Spacer(Modifier.height(4.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            currentCategory.animations.forEach { anim ->
                val isActive = currentAnimation == anim.key
                Box(
                    modifier = Modifier
                        .height(34.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isActive) Color(0xFF7C3AED) else Color(0xFF181818))
                        .pointerInput(anim.key) {
                            detectTapGestures { onAnimationSelected(anim.key) }
                        }
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        anim.label,
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // ─── DURATION SLIDER ─────────────────────────────
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
                value = currentDuration,
                onValueChange = onDurationChanged,
                valueRange = 0.2f..3.0f,
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFF7C3AED),
                    activeTrackColor = Color(0xFF7C3AED),
                    inactiveTrackColor = Color(0xFF303030)
                )
            )
            Text(
                String.format("%.1fs", currentDuration),
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(40.dp)
            )
        }

        Spacer(Modifier.height(8.dp))

        // ─── PREVIEW BUTTON ──────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(38.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF22C55E).copy(alpha = 0.15f))
                .pointerInput(Unit) { detectTapGestures { onPreview() } },
            contentAlignment = Alignment.Center
        ) {
            Text(
                "▶ Preview Animation",
                color = Color(0xFF22C55E),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
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
        Text(text, color = Color(0xFF888888), fontSize = 10.sp)
    }
}