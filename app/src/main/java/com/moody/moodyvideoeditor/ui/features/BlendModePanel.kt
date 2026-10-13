package com.moody.moodyvideoeditor.ui.features
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import com.moody.moodyvideoeditor.data.LayerBlendMode
import com.moody.moodyvideoeditor.ui.components.FeaturePanel

@Composable
fun BlendModePanel(
    currentBlendMode: String,
    currentBlendOpacity: Float,
    hasClipSelected: Boolean,
    multiCount: Int,
    onBlendModeSelected: (String) -> Unit,
    onBlendOpacityChanged: (Float) -> Unit,
    onClose: () -> Unit
) {
    FeaturePanel(title = "🎨 Blend Mode", onClose = onClose) {

        if (!hasClipSelected) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF181818))
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text("👆", fontSize = 24.sp)
                Text(
                    "No layer selected",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Select a clip to change its blend mode.",
                    color = Color(0xFF888888),
                    fontSize = 10.sp
                )
            }
            return@FeaturePanel
        }

        val activeKey = currentBlendMode.lowercase()

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 320.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {

            // ═══════ SIDE-BY-SIDE BLEND CHIPS (horizontal scroll) ═══════
            Text(
                "Tap to select · swipe for more",
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
                LayerBlendMode.values().forEach { mode ->
                    val isActive = activeKey == mode.key
                    Column(
                        modifier = Modifier
                            .width(78.dp)
                            .height(76.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (isActive) Color(0xFF2A1F4D)
                                else Color(0xFF181818)
                            )
                            .then(
                                if (isActive) Modifier.border(
                                    1.5.dp,
                                    Color(0xFF7C3AED),
                                    RoundedCornerShape(10.dp)
                                ) else Modifier
                            )
                            .pointerInput(mode.key) {
                                detectTapGestures {
                                    onBlendModeSelected(mode.key)
                                }
                            }
                            .padding(4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        // Two overlapping circles → visual hint of blend
                        Box(
                            modifier = Modifier.size(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            // Background circle (base)
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .align(Alignment.TopStart)
                                    .clip(RoundedCornerShape(11.dp))
                                    .background(Color(0xFF3B82F6))
                            )
                            // Foreground circle (varies per mode)
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .align(Alignment.BottomEnd)
                                    .clip(RoundedCornerShape(11.dp))
                                    .background(blendPreviewColor(mode))
                            )
                        }
                        Spacer(Modifier.height(3.dp))
                        Text(
                            mode.label,
                            color = if (isActive) Color(0xFFA78BFA)
                            else Color.White,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            lineHeight = 9.sp
                        )
                    }
                }
            }

            Spacer(Modifier.height(4.dp))
            // ═══════ BLEND OPACITY SLIDER ═══════
            val isNormal = activeKey == "normal"
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "Opacity",
                    color = if (isNormal) Color(0xFF555555)
                    else Color(0xFF888888),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(60.dp)
                )
                Slider(
                    value = currentBlendOpacity.coerceIn(0f, 100f),
                    onValueChange = { v ->
                        if (!isNormal) onBlendOpacityChanged(v)
                    },
                    enabled = !isNormal,
                    valueRange = 0f..100f,
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFF7C3AED),
                        activeTrackColor = Color(0xFF7C3AED),
                        inactiveTrackColor = Color(0xFF303030),
                        disabledThumbColor = Color(0xFF444444),
                        disabledActiveTrackColor = Color(0xFF333333),
                        disabledInactiveTrackColor = Color(0xFF202020)
                    )
                )
                Text(
                    "${currentBlendOpacity.toInt()}%",
                    color = if (isNormal) Color(0xFF555555)
                    else Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(44.dp)
                )
            }

            if (!isNormal) {
                Text(
                    "💡 100% = full blend, 50% = half effect, 0% = original clip",
                    color = Color(0xFF666666),
                    fontSize = 9.sp,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }

            // ═══════ CURRENT SELECTION INFO (below) ═══════
            val current = LayerBlendMode.fromKey(activeKey)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF1F1438))
                    .padding(horizontal = 10.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(current.icon, fontSize = 20.sp)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        if (multiCount > 1)
                            "Applying to $multiCount layers"
                        else current.label,
                        color = Color(0xFFA78BFA),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Blends with layers below on timeline",
                        color = Color(0xFF888888),
                        fontSize = 10.sp
                    )
                }
            }

            Text(
                "💡 Neeche koi layer na ho to ye blend mode ka koi farq nahi padega.",
                color = Color(0xFF666666),
                fontSize = 9.sp,
                modifier = Modifier.padding(start = 4.dp)
            )
        }
    }
}

// Color swatch per mode (visual hint)
private fun blendPreviewColor(mode: LayerBlendMode): Color = when (mode) {
    LayerBlendMode.NORMAL -> Color(0xFFA855F7).copy(alpha = 0.9f)
    LayerBlendMode.MULTIPLY -> Color(0xFF6366F1)
    LayerBlendMode.SCREEN -> Color(0xFFD8B4FE)
    LayerBlendMode.OVERLAY -> Color(0xFFC084FC)
    LayerBlendMode.DARKEN -> Color(0xFF4C1D95)
    LayerBlendMode.LIGHTEN -> Color(0xFFE9D5FF)
    LayerBlendMode.HARD_LIGHT -> Color(0xFFF472B6)
    LayerBlendMode.SOFT_LIGHT -> Color(0xFFFBCFE8)
    LayerBlendMode.COLOR_DODGE -> Color(0xFFFEF3C7)
    LayerBlendMode.COLOR_BURN -> Color(0xFF991B1B)
    LayerBlendMode.DIFFERENCE -> Color(0xFFF0ABFC)
    LayerBlendMode.EXCLUSION -> Color(0xFFE879F9)
    LayerBlendMode.ADD -> Color(0xFFFFC0CB)
    LayerBlendMode.HUE -> Color(0xFFFF9F43)
    LayerBlendMode.SATURATION -> Color(0xFFFF6B9D)
    LayerBlendMode.COLOR -> Color(0xFFFF4757)
    LayerBlendMode.LUMINOSITY -> Color(0xFFFEE440)
    else -> Color(0xFFA855F7).copy(alpha = 0.75f)
}