package com.moody.moodyvideoeditor.ui.features

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moody.moodyvideoeditor.R
import com.moody.moodyvideoeditor.data.EffectKind
import com.moody.moodyvideoeditor.data.EffectLibrary
import com.moody.moodyvideoeditor.data.EffectPreset
import com.moody.moodyvideoeditor.data.EffectState
import com.moody.moodyvideoeditor.data.OverlayConfig
import com.moody.moodyvideoeditor.data.OverlayState
import com.moody.moodyvideoeditor.ui.components.ColorPickerField
import com.moody.moodyvideoeditor.ui.components.FeaturePanel
import com.moody.moodyvideoeditor.utils.BlurConfig
import com.moody.moodyvideoeditor.utils.EffectsEngine
import com.moody.moodyvideoeditor.utils.LightLeakEngine
import com.moody.moodyvideoeditor.utils.OverlayEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val CATEGORIES = listOf(
    Triple(EffectKind.MOTION, "💫 Motion", EffectLibrary.MOTION_EFFECTS),
    Triple(EffectKind.ANIMATED_COLOR, "🌈 Anim Color", EffectLibrary.ANIMATED_COLOR_EFFECTS),
    Triple(EffectKind.BLUR, "🔍 Blur", EffectLibrary.BLUR_EFFECTS),
    Triple(EffectKind.OVERLAY, "🎬 Overlay", EffectLibrary.OVERLAY_EFFECTS),
    Triple(EffectKind.EDGE_GLOW, "✨ Edge Glow", EffectLibrary.EDGE_GLOW_EFFECTS),
    Triple(EffectKind.LIGHT_LEAK, "🌅 Light Leaks", EffectLibrary.LIGHT_LEAK_EFFECTS),
    Triple(EffectKind.LIGHTNING, "⚡ Lightning", EffectLibrary.LIGHTNING_EFFECTS)
)
@Composable
fun EffectsPanel(
    editLayerId: String? = null,
    initialEffectState: EffectState? = null,
    initialPresetKey: String? = null,
    onPreviewEffect: (presetKey: String?, intensity: Float) -> Unit,
    onApplyAsLayer: (presetKey: String, intensity: Float) -> Unit,
    onApplyOverlayLayer: ((presetKey: String, intensity: Float, config: OverlayConfig) -> Unit)? = null,
    onApplyBlurLayer: ((presetKey: String, intensity: Float, config: BlurConfig) -> Unit)? = null,
    onUpdateIntensity: (intensity: Float) -> Unit = {},
    onOverlayPropertyChange: ((presetKey: String, config: OverlayConfig) -> Unit)? = null,
    onBlurPropertyChange: ((presetKey: String, config: BlurConfig) -> Unit)? = null,
    onLightningPropertyChange: ((presetKey: String, config: com.moody.moodyvideoeditor.data.LightningConfig) -> Unit)? = null,
    onDeleteLayer: () -> Unit = {},
    onClose: () -> Unit
) {
    val isEditMode = editLayerId != null
    val context = LocalContext.current

    var selectedCategoryIdx by remember { mutableStateOf(0) }
    var selectedPreset by remember { mutableStateOf<EffectPreset?>(null) }
    var intensity by remember { mutableStateOf(100f) }

    // Local overlay config — survives shape/size changes
    var currentOverlayConfig by remember(selectedPreset?.key) {
        mutableStateOf(
            selectedPreset?.overlay ?: OverlayConfig(
                type = selectedPreset?.key ?: "rain",
                color = 0xFFFFFFFF,
                color2 = 0xFFFFFFFF,
                shape = "circle"
            )
        )
    }

    // Local blur config
    var currentBlurConfig by remember(selectedPreset?.key) {
        mutableStateOf(
            selectedPreset?.blur ?: BlurConfig(
                type = selectedPreset?.key ?: "blurIn",
                intensity = 50f,
                animDuration = 2f
            )
        )
    }
    // Local lightning config
    var currentLightningConfig by remember(selectedPreset?.key) {
        mutableStateOf(
            selectedPreset?.lightning ?: com.moody.moodyvideoeditor.data.LightningConfig()
        )
    }
    var refBitmap by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(Unit) {
        refBitmap = withContext(Dispatchers.IO) { loadRefImage(context) }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "fxClock")
    val clockSec by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 10f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 10000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "clockSec"
    )

    LaunchedEffect(Unit) {
        if (isEditMode && initialPresetKey != null) {
            val preset = EffectLibrary.findByKey(initialPresetKey)
            if (preset != null) {
                selectedPreset = preset
                intensity = initialEffectState?.masterIntensity ?: 100f
                initialEffectState?.overlay?.let { currentOverlayConfig = it }
                initialEffectState?.blur?.let { currentBlurConfig = it }
                initialEffectState?.lightning?.let { currentLightningConfig = it }
                val catIdx = CATEGORIES.indexOfFirst { (_, _, list) ->
                    list.any { it.key == initialPresetKey }
                }
                if (catIdx >= 0) selectedCategoryIdx = catIdx
                onPreviewEffect(initialPresetKey, intensity)
            }
        } else {
            onPreviewEffect(null, 100f)
        }
    }

    FeaturePanel(
        title = if (isEditMode) "✨ Edit Effect" else "✨ Effects",
        onClose = {
            onPreviewEffect(null, 100f)
            onClose()
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 420.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {

            // ─── CATEGORY ───
            Text(
                "Category (${EffectLibrary.ALL.size} effects)",
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
                CATEGORIES.forEachIndexed { idx, (_, label, _) ->
                    val isActive = selectedCategoryIdx == idx
                    Box(
                        modifier = Modifier
                            .height(30.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isActive) Color(0xFF7C3AED) else Color(0xFF181818)
                            )
                            .pointerInput(idx) {
                                detectTapGestures { selectedCategoryIdx = idx }
                            }
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            label,
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(Modifier.height(2.dp))

            // ─── PRESETS ───
            val (currentKind, catLabel, catPresets) = CATEGORIES[selectedCategoryIdx]

            Text(
                "$catLabel (${catPresets.size})",
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
                catPresets.forEach { preset ->
                    val isSelected = selectedPreset?.key == preset.key
                    EffectThumbChip(
                        preset = preset,
                        kind = currentKind,
                        isSelected = isSelected,
                        refBitmap = refBitmap,
                        clockSec = clockSec,
                        onClick = {
                            selectedPreset = preset
                            intensity = 100f
                            preset.overlay?.let { currentOverlayConfig = it }
                            preset.blur?.let { currentBlurConfig = it }
                            preset.lightning?.let { currentLightningConfig = it }
                            onPreviewEffect(preset.key, intensity)
                            if (isEditMode) onUpdateIntensity(intensity)
                        }
                    )
                }
            }

            // ─── PROPERTIES ───
            val current = selectedPreset
            if (current != null) {
                Spacer(Modifier.height(2.dp))

                // Info bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF1F1438))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(current.icon, fontSize = 14.sp,
                        modifier = Modifier.padding(end = 8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Previewing: ${current.label}",
                            color = Color(0xFFA78BFA),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            if (isEditMode) "Live updates on layer"
                            else "Not yet applied — press Apply",
                            color = Color(0xFF666666),
                            fontSize = 9.sp
                        )
                    }
                }

                // Intensity (all effects have this)
                PropertySliderRow(
                    label = "Intensity",
                    value = intensity,
                    range = 0f..200f,
                    format = "%.0f",
                    suffix = "%",
                    onValueChange = { v ->
                        intensity = v
                        onPreviewEffect(current.key, v)
                        if (isEditMode) onUpdateIntensity(v)
                    }
                )

                // ═══════════════════════════════════════════════════════
                //  🆕 BLUR PROPERTIES
                // ═══════════════════════════════════════════════════════
                if (current.kind == EffectKind.BLUR) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "🔍 Blur Options",
                        color = Color(0xFF888888),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 4.dp)
                    )

                    // Blur Intensity (peak)
                    PropertySliderRow(
                        label = "Blur Peak",
                        value = currentBlurConfig.intensity,
                        range = 0f..100f,
                        format = "%.0f",
                        suffix = "%",
                        onValueChange = { v ->
                            currentBlurConfig = currentBlurConfig.copy(intensity = v)
                            onBlurPropertyChange?.invoke(current.key, currentBlurConfig)
                        }
                    )

                    // Animation Duration
                    PropertySliderRow(
                        label = "Anim Dur",
                        value = currentBlurConfig.animDuration,
                        range = 0.2f..10f,
                        format = "%.1f",
                        suffix = "s",
                        onValueChange = { v ->
                            currentBlurConfig = currentBlurConfig.copy(animDuration = v)
                            onBlurPropertyChange?.invoke(current.key, currentBlurConfig)
                        }
                    )

                    // Speed
                    PropertySliderRow(
                        label = "Speed",
                        value = currentBlurConfig.speed,
                        range = 0.2f..3f,
                        format = "%.2f",
                        suffix = "x",
                        onValueChange = { v ->
                            currentBlurConfig = currentBlurConfig.copy(speed = v)
                            onBlurPropertyChange?.invoke(current.key, currentBlurConfig)
                        }
                    )

                    // Type-specific options
                    when (currentBlurConfig.type) {
                        "motion", "oblique" -> {
                            PropertySliderRow(
                                label = "Angle",
                                value = currentBlurConfig.angle,
                                range = 0f..360f,
                                format = "%.0f",
                                suffix = "°",
                                onValueChange = { v ->
                                    currentBlurConfig = currentBlurConfig.copy(angle = v)
                                    onBlurPropertyChange?.invoke(current.key, currentBlurConfig)
                                }
                            )
                        }
                        "radial", "spin" -> {
                            PropertySliderRow(
                                label = "Pivot X",
                                value = currentBlurConfig.centerX * 100f,
                                range = 0f..100f,
                                format = "%.0f",
                                suffix = "%",
                                onValueChange = { v ->
                                    currentBlurConfig = currentBlurConfig.copy(centerX = v / 100f)
                                    onBlurPropertyChange?.invoke(current.key, currentBlurConfig)
                                }
                            )
                            PropertySliderRow(
                                label = "Pivot Y",
                                value = currentBlurConfig.centerY * 100f,
                                range = 0f..100f,
                                format = "%.0f",
                                suffix = "%",
                                onValueChange = { v ->
                                    currentBlurConfig = currentBlurConfig.copy(centerY = v / 100f)
                                    onBlurPropertyChange?.invoke(current.key, currentBlurConfig)
                                }
                            )
                        }
                        "mosaic" -> {
                            PropertySliderRow(
                                label = "Block",
                                value = currentBlurConfig.blockSize,
                                range = 4f..100f,
                                format = "%.0f",
                                suffix = "px",
                                onValueChange = { v ->
                                    currentBlurConfig = currentBlurConfig.copy(blockSize = v)
                                    onBlurPropertyChange?.invoke(current.key, currentBlurConfig)
                                }
                            )
                        }
                        "softFocus" -> {
                            PropertySliderRow(
                                label = "Glow",
                                value = currentBlurConfig.secondary,
                                range = 0f..100f,
                                format = "%.0f",
                                suffix = "%",
                                onValueChange = { v ->
                                    currentBlurConfig = currentBlurConfig.copy(secondary = v)
                                    onBlurPropertyChange?.invoke(current.key, currentBlurConfig)
                                }
                            )
                        }
                        "edgeBlur" -> {
                            PropertySliderRow(
                                label = "Safe Zone",
                                value = currentBlurConfig.secondary,
                                range = 0f..100f,
                                format = "%.0f",
                                suffix = "%",
                                onValueChange = { v ->
                                    currentBlurConfig = currentBlurConfig.copy(secondary = v)
                                    onBlurPropertyChange?.invoke(current.key, currentBlurConfig)
                                }
                            )
                        }
                        "chromaBlur" -> {
                            PropertySliderRow(
                                label = "Split",
                                value = currentBlurConfig.secondary,
                                range = 0f..50f,
                                format = "%.1f",
                                suffix = "px",
                                onValueChange = { v ->
                                    currentBlurConfig = currentBlurConfig.copy(secondary = v)
                                    onBlurPropertyChange?.invoke(current.key, currentBlurConfig)
                                }
                            )
                        }
                    }
                }

                // ═══════════════════════════════════════════════════════
                //  🎬 OVERLAY PROPERTIES
                // ═══════════════════════════════════════════════════════
                if (current.kind == EffectKind.OVERLAY) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "🎬 Overlay Properties",
                        color = Color(0xFF888888),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 4.dp)
                    )

                    // Color 1
                    ColorPickerField(
                        label = "Color 1",
                        colorLong = currentOverlayConfig.color,
                        onChange = { newCol ->
                            currentOverlayConfig = currentOverlayConfig.copy(color = newCol)
                            onOverlayPropertyChange?.invoke(current.key, currentOverlayConfig)
                        }
                    )

                    // Color 2
                    ColorPickerField(
                        label = "Color 2",
                        colorLong = currentOverlayConfig.color2,
                        onChange = { newCol ->
                            currentOverlayConfig = currentOverlayConfig.copy(color2 = newCol)
                            onOverlayPropertyChange?.invoke(current.key, currentOverlayConfig)
                        }
                    )

                    // Size
                    PropertySliderRow(
                        label = "Size",
                        value = currentOverlayConfig.size,
                        range = 0.2f..20f,
                        format = "%.2f",
                        suffix = "x",
                        onValueChange = { v ->
                            currentOverlayConfig = currentOverlayConfig.copy(size = v)
                            onOverlayPropertyChange?.invoke(current.key, currentOverlayConfig)
                        }
                    )

                    // Opacity
                    PropertySliderRow(
                        label = "Opacity",
                        value = currentOverlayConfig.opacity * 100f,
                        range = 0f..100f,
                        format = "%.0f",
                        suffix = "%",
                        onValueChange = { v ->
                            currentOverlayConfig = currentOverlayConfig.copy(
                                opacity = (v / 100f).coerceIn(0f, 1f)
                            )
                            onOverlayPropertyChange?.invoke(current.key, currentOverlayConfig)
                        }
                    )

                    // Speed
                    PropertySliderRow(
                        label = "Speed",
                        value = currentOverlayConfig.speed,
                        range = 0.2f..20f,
                        format = "%.2f",
                        suffix = "x",
                        onValueChange = { v ->
                            currentOverlayConfig = currentOverlayConfig.copy(speed = v)
                            onOverlayPropertyChange?.invoke(current.key, currentOverlayConfig)
                        }
                    )

                    // Shape
                    Text(
                        "Shape",
                        color = Color(0xFF888888), fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            "circle" to "● Circle",
                            "square" to "■ Square",
                            "star" to "★ Star",
                            "line" to "▮ Line"
                        ).forEach { (key, label) ->
                            val active = currentOverlayConfig.shape == key
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(30.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(
                                        if (active) Color(0xFF7C3AED)
                                        else Color(0xFF181818)
                                    )
                                    .pointerInput(key) {
                                        detectTapGestures {
                                            currentOverlayConfig = currentOverlayConfig.copy(shape = key)
                                            onOverlayPropertyChange?.invoke(current.key, currentOverlayConfig)
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(label, color = Color.White, fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // Glow toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Glow", color = Color(0xFF888888), fontSize = 10.sp,
                            fontWeight = FontWeight.Bold, modifier = Modifier.width(56.dp))
                        Box(
                            modifier = Modifier
                                .height(28.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    if (currentOverlayConfig.glowEnabled) Color(0xFF7C3AED)
                                    else Color(0xFF181818)
                                )
                                .pointerInput(currentOverlayConfig.glowEnabled) {
                                    detectTapGestures {
                                        currentOverlayConfig = currentOverlayConfig.copy(
                                            glowEnabled = !currentOverlayConfig.glowEnabled
                                        )
                                        onOverlayPropertyChange?.invoke(current.key, currentOverlayConfig)
                                    }
                                }
                                .padding(horizontal = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                if (currentOverlayConfig.glowEnabled) "ON" else "OFF",
                                color = Color.White, fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    if (currentOverlayConfig.glowEnabled) {
                        PropertySliderRow(
                            label = "Glow R",
                            value = currentOverlayConfig.glowRadius,
                            range = 1.2f..15f,
                            format = "%.2f",
                            suffix = "x",
                            onValueChange = { v ->
                                currentOverlayConfig = currentOverlayConfig.copy(glowRadius = v)
                                onOverlayPropertyChange?.invoke(current.key, currentOverlayConfig)
                            }
                        )

                        ColorPickerField(
                            label = "Glow Col",
                            colorLong = currentOverlayConfig.glowColor,
                            onChange = { newCol ->
                                currentOverlayConfig = currentOverlayConfig.copy(glowColor = newCol)
                                onOverlayPropertyChange?.invoke(current.key, currentOverlayConfig)
                            }
                        )
                    }
                }
                // ═══════════════════════════════════════════════════════
                //  ⚡ LIGHTNING PROPERTIES
                // ═══════════════════════════════════════════════════════
                if (current.kind == EffectKind.LIGHTNING) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "⚡ Lightning Options",
                        color = Color(0xFF888888),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 4.dp)
                    )

                    PropertySliderRow(
                        label = "Intensity",
                        value = currentLightningConfig.intensity,
                        range = 0f..200f,
                        format = "%.0f",
                        suffix = "%",
                        onValueChange = { v ->
                            currentLightningConfig =
                                currentLightningConfig.copy(intensity = v)
                            onLightningPropertyChange?.invoke(
                                current.key, currentLightningConfig
                            )
                        }
                    )

                    PropertySliderRow(
                        label = "Frequency",
                        value = currentLightningConfig.frequency,
                        range = 0.2f..5f,
                        format = "%.2f",
                        suffix = "/s",
                        onValueChange = { v ->
                            currentLightningConfig =
                                currentLightningConfig.copy(frequency = v)
                            onLightningPropertyChange?.invoke(
                                current.key, currentLightningConfig
                            )
                        }
                    )

                    PropertySliderRow(
                        label = "Flash Dur",
                        value = currentLightningConfig.durationMs.toFloat(),
                        range = 20f..500f,
                        format = "%.0f",
                        suffix = "ms",
                        onValueChange = { v ->
                            currentLightningConfig = currentLightningConfig.copy(
                                durationMs = v.toLong().coerceIn(20L, 500L)
                            )
                            onLightningPropertyChange?.invoke(
                                current.key, currentLightningConfig
                            )
                        }
                    )

                    ColorPickerField(
                        label = "Flash Color",
                        colorLong = currentLightningConfig.color,
                        onChange = { newCol ->
                            currentLightningConfig =
                                currentLightningConfig.copy(color = newCol)
                            onLightningPropertyChange?.invoke(
                                current.key, currentLightningConfig
                            )
                        }
                    )

                    // Quick color presets
                    Text(
                        "Quick Colors",
                        color = Color(0xFF888888),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            0xFFFFFFFF to "⚪ White",
                            0xFF66CCFF to "🔵 Blue",
                            0xFFB366FF to "💜 Purple",
                            0xFFFFEECC to "🌤️ Warm",
                            0xFFFF6666 to "🔴 Red"
                        ).forEach { (colorLong, label) ->
                            val active = currentLightningConfig.color == colorLong
                            Box(
                                modifier = Modifier
                                    .height(30.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(
                                        if (active) Color(0xFF7C3AED)
                                        else Color(0xFF181818)
                                    )
                                    .pointerInput(colorLong) {
                                        detectTapGestures {
                                            currentLightningConfig =
                                                currentLightningConfig.copy(color = colorLong)
                                            onLightningPropertyChange?.invoke(
                                                current.key, currentLightningConfig
                                            )
                                        }
                                    }
                                    .padding(horizontal = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    label,
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Double strike toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "Double Strike",
                            color = Color(0xFF888888), fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(100.dp)
                        )
                        Box(
                            modifier = Modifier
                                .height(28.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    if (currentLightningConfig.doubleStrike)
                                        Color(0xFF7C3AED)
                                    else Color(0xFF181818)
                                )
                                .pointerInput(currentLightningConfig.doubleStrike) {
                                    detectTapGestures {
                                        currentLightningConfig =
                                            currentLightningConfig.copy(
                                                doubleStrike =
                                                    !currentLightningConfig.doubleStrike
                                            )
                                        onLightningPropertyChange?.invoke(
                                            current.key, currentLightningConfig
                                        )
                                    }
                                }
                                .padding(horizontal = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                if (currentLightningConfig.doubleStrike) "ON" else "OFF",
                                color = Color.White, fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                // ─── ACTIONS ───
                if (isEditMode) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFFF6B6B).copy(alpha = 0.15f))
                                .pointerInput(Unit) {
                                    detectTapGestures {
                                        onDeleteLayer()
                                        onPreviewEffect(null, 100f)
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("🗑 Delete", color = Color(0xFFFF6B6B),
                                fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Box(
                            modifier = Modifier
                                .weight(2f)
                                .height(44.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF7C3AED))
                                .pointerInput(Unit) {
                                    detectTapGestures {
                                        onPreviewEffect(null, 100f)
                                        onClose()
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("✓ Done", color = Color.White,
                                fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF181818))
                                .pointerInput(Unit) {
                                    detectTapGestures {
                                        selectedPreset = null
                                        intensity = 100f
                                        onPreviewEffect(null, 100f)
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("✕ Cancel", color = Color(0xFFAAAAAA),
                                fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        Box(
                            modifier = Modifier
                                .weight(2f)
                                .height(44.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF7C3AED))
                                .pointerInput(Unit) {
                                    detectTapGestures {
                                        when {
                                            current.kind == EffectKind.BLUR &&
                                                    onApplyBlurLayer != null -> {
                                                onApplyBlurLayer(
                                                    current.key, intensity, currentBlurConfig
                                                )
                                            }
                                            current.kind == EffectKind.OVERLAY &&
                                                    onApplyOverlayLayer != null -> {
                                                onApplyOverlayLayer(
                                                    current.key, intensity, currentOverlayConfig
                                                )
                                            }
                                            else -> {
                                                onApplyAsLayer(current.key, intensity)
                                            }
                                        }
                                        selectedPreset = null
                                        intensity = 100f
                                        onPreviewEffect(null, 100f)
                                        onClose()
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("✨ Apply as Layer", color = Color.White,
                                fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Text(
                    if (isEditMode) "💡 Layer live update ho raha hai"
                    else "💡 Custom settings apply hongi",
                    color = Color(0xFF666666),
                    fontSize = 9.sp,
                    modifier = Modifier.padding(start = 4.dp)
                )
            } else {
                Text("👆 Tap any effect chip above to preview",
                    color = Color(0xFF666666), fontSize = 10.sp,
                    modifier = Modifier.padding(start = 4.dp, top = 8.dp))
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  SLIDER WITH TEXT INPUT
// ═══════════════════════════════════════════════════════════════

@Composable
private fun PropertySliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    format: String,
    suffix: String = "",
    onValueChange: (Float) -> Unit
) {
    var textValue by remember { mutableStateOf(String.format(format, value)) }
    var isFocused by remember { mutableStateOf(false) }

    LaunchedEffect(value, isFocused) {
        if (!isFocused) textValue = String.format(format, value)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(label, color = Color(0xFF888888), fontSize = 10.sp,
            fontWeight = FontWeight.Bold, modifier = Modifier.width(60.dp))

        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = {
                onValueChange(it.coerceIn(range.start, range.endInclusive))
                textValue = String.format(format, it)
            },
            valueRange = range,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFF7C3AED),
                activeTrackColor = Color(0xFF7C3AED),
                inactiveTrackColor = Color(0xFF303030)
            )
        )

        Box(
            modifier = Modifier
                .width(72.dp)
                .height(28.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF0F0F0F))
                .padding(horizontal = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicTextField(
                    value = textValue,
                    onValueChange = { input ->
                        val filtered = input.filter {
                            it.isDigit() || it == '.' || it == '-' || it == '+'
                        }
                        textValue = filtered
                        filtered.toFloatOrNull()?.takeIf { it.isFinite() }?.let {
                            onValueChange(it.coerceIn(0f, 1000f))
                        }
                    },
                    singleLine = true,
                    textStyle = TextStyle(
                        color = Color(0xFF4F9DFF),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.End
                    ),
                    cursorBrush = SolidColor(Color(0xFF4F9DFF)),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .onFocusChanged { state ->
                            isFocused = state.isFocused
                            if (!state.isFocused) {
                                textValue.toFloatOrNull()
                                    ?.takeIf { it.isFinite() }
                                    ?.let { onValueChange(it.coerceIn(0f, 1000f)) }
                            }
                        }
                )
                if (suffix.isNotBlank()) {
                    Text(suffix, color = Color(0xFF888888),
                        fontSize = 9.sp,
                        modifier = Modifier.padding(start = 2.dp))
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  EFFECT THUMB CHIP
// ═══════════════════════════════════════════════════════════════

@Composable
private fun EffectThumbChip(
    preset: EffectPreset,
    kind: EffectKind,
    isSelected: Boolean,
    refBitmap: Bitmap?,
    clockSec: Float,
    onClick: () -> Unit
) {
    val colorFilter = remember(preset.key, kind, clockSec) {
        if (kind != EffectKind.ANIMATED_COLOR || preset.animatedColor == null) null
        else try {
            val dynFilters = com.moody.moodyvideoeditor.utils.AnimatedColorEngine
                .compute(preset.animatedColor, clockSec)
            val cm = EffectsEngine.buildColorMatrix(dynFilters)
            ColorFilter.colorMatrix(ColorMatrix(cm.array))
        } catch (_: Throwable) { null }
    }

    val motionFrame = remember(preset.key, kind, clockSec) {
        if (kind != EffectKind.MOTION || preset.motion == null)
            EffectsEngine.MotionFrame()
        else EffectsEngine.computeMotion(preset.motion, clockSec)
    }

    val overlayState: OverlayState? = remember(preset.key, kind) {
        if (kind == EffectKind.OVERLAY && preset.overlay != null) {
            try {
                OverlayState(
                    type = preset.overlay.type,
                    intensity = preset.overlay.intensity,
                    color = preset.overlay.color
                )
            } catch (_: Throwable) { null }
        } else null
    }

    val edgeGlowFrame: EffectsEngine.EdgeGlowFrame? =
        remember(preset.key, kind, clockSec) {
            if (kind == EffectKind.EDGE_GLOW && preset.edgeGlow != null) {
                try { EffectsEngine.computeEdgeGlow(preset.edgeGlow, clockSec) }
                catch (_: Throwable) { null }
            } else null
        }

    val lightLeakFrame: LightLeakEngine.Frame? =
        remember(preset.key, kind, clockSec) {
            if (kind == EffectKind.LIGHT_LEAK && preset.lightLeak != null) {
                try { LightLeakEngine.computeFrame(preset.lightLeak, clockSec) }
                catch (_: Throwable) { null }
            } else null
        }

    // Blur radius preview
    val blurRadiusDp = remember(preset.key, kind, clockSec) {
        if (kind == EffectKind.BLUR && preset.blur != null) {
            try {
                BlurEngineForThumb(preset.blur, clockSec)
            } catch (_: Throwable) { 0f }
        } else 0f
    }

    val hasImage = refBitmap != null && !refBitmap.isRecycled

    Column(
        modifier = Modifier
            .width(76.dp)
            .height(102.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (isSelected) Color(0xFF2A1F4D) else Color(0xFF181818))
            .pointerInput(preset.key) { detectTapGestures { onClick() } }
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF0F0F0F))
        ) {
            if (hasImage) {
                // ✅ FIX: Use Modifier.blur() instead of RenderEffect
                //    Works on all API levels, no asComposeRenderEffect import needed
                Image(
                    bitmap = refBitmap!!.asImageBitmap(),
                    contentDescription = preset.label,
                    contentScale = ContentScale.Crop,
                    colorFilter = colorFilter,
                    modifier = Modifier
                        .matchParentSize()
                        .then(
                            if (kind == EffectKind.BLUR && blurRadiusDp > 0.5f) {
                                Modifier.blur(
                                    radius = blurRadiusDp
                                        .coerceAtMost(15f)
                                        .dp,
                                    edgeTreatment = BlurredEdgeTreatment.Unbounded
                                )
                            } else Modifier
                        )
                        .graphicsLayer {
                            translationX = motionFrame.tx
                            translationY = motionFrame.ty
                            scaleX = motionFrame.scale
                            scaleY = motionFrame.scale
                            rotationZ = motionFrame.rotation
                            transformOrigin = TransformOrigin.Center
                        }
                )

                if (overlayState != null) {
                    Canvas(modifier = Modifier.matchParentSize()) {
                        try {
                            OverlayEngine.draw(this, clockSec, overlayState)
                        } catch (_: Throwable) {}
                    }
                }

                if (edgeGlowFrame != null && preset.edgeGlow != null) {
                    val r = (preset.edgeGlow.radius * edgeGlowFrame.radiusMul / 3f)
                        .coerceIn(1f, 12f)
                    val a = (edgeGlowFrame.intensityMul *
                            (preset.edgeGlow.intensity / 100f) *
                            edgeGlowFrame.alpha).coerceIn(0f, 1f)
                    Canvas(modifier = Modifier.matchParentSize()) {
                        val cx = size.width / 2f + edgeGlowFrame.offsetX
                        val cy = size.height / 2f + edgeGlowFrame.offsetY
                        drawCircle(
                            color = edgeGlowFrame.color.copy(alpha = a * 0.7f),
                            radius = r,
                            center = Offset(cx, cy)
                        )
                    }
                }

                if (lightLeakFrame != null) {
                    Canvas(modifier = Modifier.matchParentSize()) {
                        try {
                            val cx = lightLeakFrame.posX * size.width
                            val cy = lightLeakFrame.posY * size.height
                            val maxDim = maxOf(size.width, size.height)
                            val r = lightLeakFrame.radius * maxDim
                            val alpha = (lightLeakFrame.alpha *
                                    lightLeakFrame.intensityMul * 0.75f)
                                .coerceIn(0f, 1f)
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        lightLeakFrame.color1.copy(alpha = alpha),
                                        lightLeakFrame.color2.copy(alpha = alpha * 0.5f),
                                        Color.Transparent
                                    ),
                                    center = Offset(cx, cy),
                                    radius = r
                                ),
                                radius = r,
                                center = Offset(cx, cy),
                                blendMode = BlendMode.Screen
                            )
                        } catch (_: Throwable) {}
                    }
                }
            } else {
                Box(modifier = Modifier.matchParentSize(),
                    contentAlignment = Alignment.Center) {
                    Text(preset.icon, fontSize = 24.sp)
                }
            }

            if (isSelected) {
                Box(modifier = Modifier
                    .matchParentSize()
                    .background(Color(0xFF7C3AED).copy(alpha = 0.15f)))
            }
        }

        Text(preset.label,
            color = if (isSelected) Color(0xFFA78BFA) else Color.White,
            fontSize = 9.sp, fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center, maxLines = 2, lineHeight = 10.sp)
    }
}

// Helper for thumb preview — different time base
private fun BlurEngineForThumb(
    cfg: BlurConfig,
    clockSec: Float
): Float {
    // Thumbnail animates from 0..2 sec in a loop
    val thumbTime = (clockSec % 2f)
    return com.moody.moodyvideoeditor.utils.BlurEngine
        .computeRadiusDp(cfg, thumbTime, 2f)
}

// ═══════════════════════════════════════════════════════════════
//  REFERENCE IMAGE LOADER
// ═══════════════════════════════════════════════════════════════

private fun loadRefImage(context: android.content.Context): Bitmap? {
    return try {
        val opts = BitmapFactory.Options().apply { inScaled = false }
        val bmp = BitmapFactory.decodeResource(
            context.resources, R.drawable.filter_ref_image, opts
        ) ?: return null
        val minDim = minOf(bmp.width, bmp.height)
        val x = (bmp.width - minDim) / 2
        val y = (bmp.height - minDim) / 2
        val cropped = Bitmap.createBitmap(bmp, x, y, minDim, minDim)
        val scaled = Bitmap.createScaledBitmap(cropped, 120, 120, true)
        if (cropped != scaled && cropped != bmp) cropped.recycle()
        if (bmp != scaled && bmp != cropped) bmp.recycle()
        scaled
    } catch (e: Throwable) {
        Log.e("EFFECT_REF", "Failed to load reference image", e)
        return null
    }
}