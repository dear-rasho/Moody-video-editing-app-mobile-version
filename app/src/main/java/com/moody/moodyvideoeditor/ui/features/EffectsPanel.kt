package com.moody.moodyvideoeditor.ui.features
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
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
import com.moody.moodyvideoeditor.utils.drawEdgeGlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val CATEGORIES = listOf(
    Triple(EffectKind.MOTION, "💫 Motion", EffectLibrary.MOTION_EFFECTS),
    Triple(EffectKind.ANIMATED_COLOR, "🌈 Anim Color", EffectLibrary.ANIMATED_COLOR_EFFECTS),
    Triple(EffectKind.BLUR, "🔍 Blur", EffectLibrary.BLUR_EFFECTS),
    Triple(EffectKind.OVERLAY, "🎬 Overlay", EffectLibrary.OVERLAY_EFFECTS),
    Triple(EffectKind.EDGE_GLOW, "✨ Edge Glow", EffectLibrary.EDGE_GLOW_EFFECTS),
    Triple(EffectKind.LIGHTNING, "⚡ Lightning", EffectLibrary.LIGHTNING_EFFECTS),
    Triple(EffectKind.PARTICLE, "✨ Particles", EffectLibrary.PARTICLE_EFFECTS),
    Triple(EffectKind.FIRE, "🔥 Fire & Thermal", EffectLibrary.FIRE_EFFECTS),
    Triple(EffectKind.DUST, "🌀 Dust", EffectLibrary.DUST_EFFECTS)
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
    onParticlePropertyChange: ((presetKey: String, config: com.moody.moodyvideoeditor.data.ParticleConfig) -> Unit)? = null,
    onApplyParticleLayer: ((presetKey: String, intensity: Float, config: com.moody.moodyvideoeditor.data.ParticleConfig) -> Unit)? = null,
    onApplyLightningLayer: ((presetKey: String, intensity: Float, config: com.moody.moodyvideoeditor.data.LightningConfig) -> Unit)? = null,
    onFirePropertyChange: ((presetKey: String, config: com.moody.moodyvideoeditor.data.FireConfig) -> Unit)? = null,
    onApplyFireLayer: ((presetKey: String, intensity: Float, config: com.moody.moodyvideoeditor.data.FireConfig) -> Unit)? = null,
    onDustPropertyChange: ((presetKey: String, config: com.moody.moodyvideoeditor.data.DustConfig) -> Unit)? = null,
    onApplyDustLayer: ((presetKey: String, intensity: Float, config: com.moody.moodyvideoeditor.data.DustConfig) -> Unit)? = null,
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
    var currentFireConfig by remember(selectedPreset?.key) {
        mutableStateOf(
            selectedPreset?.fire
                ?: com.moody.moodyvideoeditor.data.FireLibrary.PRESETS[0].config
        )
    }
    var currentDustConfig by remember(selectedPreset?.key) {
        mutableStateOf(
            selectedPreset?.dust
                ?: com.moody.moodyvideoeditor.data.DustLibrary.PRESETS[0].config
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
    var currentParticleConfig by remember(selectedPreset?.key) {
        mutableStateOf(
            selectedPreset?.particle
                ?: com.moody.moodyvideoeditor.data.ParticleLibrary.PRESETS[0].config
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
                initialEffectState?.particle?.let { currentParticleConfig = it }
                initialEffectState?.fire?.let { currentFireConfig = it }
                initialEffectState?.dust?.let { currentDustConfig = it }
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
                .heightIn(max = 560.dp)                 // ← 420 → 560
                .verticalScrollWithScrollbar(rememberScrollState()),
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
                    .horizontalScrollWithTwoFinger(rememberScrollState()),
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
                    .horizontalScrollWithTwoFinger(rememberScrollState()),
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
                            preset.particle?.let { currentParticleConfig = it }
                            preset.fire?.let { currentFireConfig = it }
                            preset.dust?.let { currentDustConfig = it }
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
                //  🌀 DUST PROPERTIES
                // ═══════════════════════════════════════════════════════
                if (current.kind == EffectKind.DUST) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "🌀 Dust Options",
                        color = Color(0xFF888888),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 4.dp)
                    )

                    PropertySliderRow(
                        label = "Count",
                        value = currentDustConfig.count.toFloat(),
                        range = 1f..5000f,
                        format = "%.0f",
                        suffix = "",
                        onValueChange = { v ->
                            currentDustConfig = currentDustConfig.copy(
                                count = v.toInt().coerceIn(1, 5000)
                            )
                            onDustPropertyChange?.invoke(current.key, currentDustConfig)
                        }
                    )

                    PropertySliderRow(
                        label = "Speed",
                        value = currentDustConfig.speed,
                        range = 10f..400f,
                        format = "%.0f",
                        suffix = "",
                        onValueChange = { v ->
                            currentDustConfig = currentDustConfig.copy(speed = v)
                            onDustPropertyChange?.invoke(current.key, currentDustConfig)
                        }
                    )

                    PropertySliderRow(
                        label = "Spread",
                        value = currentDustConfig.spread,
                        range = 10f..360f,
                        format = "%.0f",
                        suffix = "°",
                        onValueChange = { v ->
                            currentDustConfig = currentDustConfig.copy(spread = v)
                            onDustPropertyChange?.invoke(current.key, currentDustConfig)
                        }
                    )

                    PropertySliderRow(
                        label = "Direction",
                        value = currentDustConfig.direction,
                        range = -180f..180f,
                        format = "%.0f",
                        suffix = "°",
                        onValueChange = { v ->
                            currentDustConfig = currentDustConfig.copy(direction = v)
                            onDustPropertyChange?.invoke(current.key, currentDustConfig)
                        }
                    )

                    PropertySliderRow(
                        label = "Gravity",
                        value = currentDustConfig.gravity,
                        range = -200f..200f,
                        format = "%.0f",
                        suffix = "",
                        onValueChange = { v ->
                            currentDustConfig = currentDustConfig.copy(gravity = v)
                            onDustPropertyChange?.invoke(current.key, currentDustConfig)
                        }
                    )

                    PropertySliderRow(
                        label = "Size Min",
                        value = currentDustConfig.sizeMin,
                        range = 0.5f..50f,
                        format = "%.1f",
                        suffix = "",
                        onValueChange = { v ->
                            currentDustConfig = currentDustConfig.copy(sizeMin = v)
                            onDustPropertyChange?.invoke(current.key, currentDustConfig)
                        }
                    )

                    PropertySliderRow(
                        label = "Size Max",
                        value = currentDustConfig.sizeMax,
                        range = 0.5f..200f,
                        format = "%.1f",
                        suffix = "",
                        onValueChange = { v ->
                            currentDustConfig = currentDustConfig.copy(sizeMax = v)
                            onDustPropertyChange?.invoke(current.key, currentDustConfig)
                        }
                    )

                    PropertySliderRow(
                        label = "Softness",
                        value = currentDustConfig.softness,
                        range = 0.3f..3f,
                        format = "%.2f",
                        suffix = "x",
                        onValueChange = { v ->
                            currentDustConfig = currentDustConfig.copy(softness = v)
                            onDustPropertyChange?.invoke(current.key, currentDustConfig)
                        }
                    )

                    ColorPickerField(
                        label = "Color 1",
                        colorLong = currentDustConfig.color1,
                        onChange = { newCol ->
                            currentDustConfig = currentDustConfig.copy(color1 = newCol)
                            onDustPropertyChange?.invoke(current.key, currentDustConfig)
                        }
                    )

                    ColorPickerField(
                        label = "Color 2",
                        colorLong = currentDustConfig.color2,
                        onChange = { newCol ->
                            currentDustConfig = currentDustConfig.copy(color2 = newCol)
                            onDustPropertyChange?.invoke(current.key, currentDustConfig)
                        }
                    )

                    PropertySliderRow(
                        label = "Opacity",
                        value = currentDustConfig.opacity * 100f,
                        range = 0f..100f,
                        format = "%.0f",
                        suffix = "%",
                        onValueChange = { v ->
                            currentDustConfig = currentDustConfig.copy(
                                opacity = (v / 100f).coerceIn(0f, 1f)
                            )
                            onDustPropertyChange?.invoke(current.key, currentDustConfig)
                        }
                    )

                    PropertySliderRow(
                        label = "Lifetime",
                        value = currentDustConfig.lifetimeMs.toFloat(),
                        range = 500f..8000f,
                        format = "%.0f",
                        suffix = "ms",
                        onValueChange = { v ->
                            currentDustConfig = currentDustConfig.copy(
                                lifetimeMs = v.toLong().coerceIn(500L, 8000L)
                            )
                            onDustPropertyChange?.invoke(current.key, currentDustConfig)
                        }
                    )

                    PropertySliderRow(
                        label = "Repeat",
                        value = currentDustConfig.triggerEverySec,
                        range = 0f..15f,
                        format = "%.2f",
                        suffix = "s",
                        onValueChange = { v ->
                            currentDustConfig = currentDustConfig.copy(
                                triggerEverySec = v.coerceIn(0f, 15f)
                            )
                            onDustPropertyChange?.invoke(current.key, currentDustConfig)
                        }
                    )

                    PropertySliderRow(
                        label = "Trigger X",
                        value = currentDustConfig.triggerX * 100f,
                        range = 0f..100f,
                        format = "%.0f",
                        suffix = "%",
                        onValueChange = { v ->
                            currentDustConfig = currentDustConfig.copy(
                                triggerX = (v / 100f).coerceIn(0f, 1f)
                            )
                            onDustPropertyChange?.invoke(current.key, currentDustConfig)
                        }
                    )

                    PropertySliderRow(
                        label = "Trigger Y",
                        value = currentDustConfig.triggerY * 100f,
                        range = 0f..100f,
                        format = "%.0f",
                        suffix = "%",
                        onValueChange = { v ->
                            currentDustConfig = currentDustConfig.copy(
                                triggerY = (v / 100f).coerceIn(0f, 1f)
                            )
                            onDustPropertyChange?.invoke(current.key, currentDustConfig)
                        }
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            "Glow" to currentDustConfig.glow,
                            "Fade" to currentDustConfig.fadeOut
                        ).forEach { (label, active) ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(28.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(
                                        if (active) Color(0xFF7C3AED)
                                        else Color(0xFF181818)
                                    )
                                    .pointerInput(label, active) {
                                        detectTapGestures {
                                            currentDustConfig = when (label) {
                                                "Glow" -> currentDustConfig.copy(glow = !active)
                                                "Fade" -> currentDustConfig.copy(fadeOut = !active)
                                                else -> currentDustConfig
                                            }
                                            onDustPropertyChange?.invoke(
                                                current.key, currentDustConfig
                                            )
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "$label ${if (active) "ON" else "OFF"}",
                                    color = Color.White, fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

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
                //  🔥 FIRE PROPERTIES
                // ═══════════════════════════════════════════════════════
                if (current.kind == EffectKind.FIRE) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "🔥 Fire Options",
                        color = Color(0xFF888888),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 4.dp)
                    )

                    PropertySliderRow(
                        label = "Flame H",
                        value = currentFireConfig.flameHeight * 100f,
                        range = 5f..100f,
                        format = "%.0f",
                        suffix = "%",
                        onValueChange = { v ->
                            currentFireConfig = currentFireConfig.copy(
                                flameHeight = (v / 100f).coerceIn(0.05f, 1f)
                            )
                            onFirePropertyChange?.invoke(current.key, currentFireConfig)
                        }
                    )

                    PropertySliderRow(
                        label = "Waviness",
                        value = currentFireConfig.waviness * 100f,
                        range = 0f..100f,
                        format = "%.0f",
                        suffix = "%",
                        onValueChange = { v ->
                            currentFireConfig = currentFireConfig.copy(
                                waviness = (v / 100f).coerceIn(0f, 1f)
                            )
                            onFirePropertyChange?.invoke(current.key, currentFireConfig)
                        }
                    )

                    PropertySliderRow(
                        label = "Density",
                        value = currentFireConfig.density.toFloat(),
                        range = 4f..40f,
                        format = "%.0f",
                        suffix = "",
                        onValueChange = { v ->
                            currentFireConfig = currentFireConfig.copy(
                                density = v.toInt().coerceIn(4, 40)
                            )
                            onFirePropertyChange?.invoke(current.key, currentFireConfig)
                        }
                    )

                    PropertySliderRow(
                        label = "Sparks",
                        value = currentFireConfig.particleCount.toFloat(),
                        range = 0f..500f,
                        format = "%.0f",
                        suffix = "",
                        onValueChange = { v ->
                            currentFireConfig = currentFireConfig.copy(
                                particleCount = v.toInt().coerceIn(0, 500)
                            )
                            onFirePropertyChange?.invoke(current.key, currentFireConfig)
                        }
                    )

                    PropertySliderRow(
                        label = "Speed",
                        value = currentFireConfig.speed,
                        range = 0.2f..5f,
                        format = "%.2f",
                        suffix = "x",
                        onValueChange = { v ->
                            currentFireConfig = currentFireConfig.copy(speed = v)
                            onFirePropertyChange?.invoke(current.key, currentFireConfig)
                        }
                    )

                    ColorPickerField(
                        label = "Hot",
                        colorLong = currentFireConfig.colorHot,
                        onChange = { newCol ->
                            currentFireConfig = currentFireConfig.copy(colorHot = newCol)
                            onFirePropertyChange?.invoke(current.key, currentFireConfig)
                        }
                    )

                    ColorPickerField(
                        label = "Mid",
                        colorLong = currentFireConfig.colorMid,
                        onChange = { newCol ->
                            currentFireConfig = currentFireConfig.copy(colorMid = newCol)
                            onFirePropertyChange?.invoke(current.key, currentFireConfig)
                        }
                    )

                    ColorPickerField(
                        label = "Cool",
                        colorLong = currentFireConfig.colorCool,
                        onChange = { newCol ->
                            currentFireConfig = currentFireConfig.copy(colorCool = newCol)
                            onFirePropertyChange?.invoke(current.key, currentFireConfig)
                        }
                    )
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
                            .horizontalScrollWithTwoFinger(rememberScrollState()),
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
                // ═══════════════════════════════════════════════════════
                //  ✨💥 PARTICLE PROPERTIES
                // ═══════════════════════════════════════════════════════
                if (current.kind == EffectKind.PARTICLE) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "✨ Particle Options",
                        color = Color(0xFF888888),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 4.dp)
                    )

                    PropertySliderRow(
                        label = "Count",
                        value = currentParticleConfig.count.toFloat(),
                        range = 1f..10000f,
                        format = "%.0f",
                        suffix = "",
                        onValueChange = { v ->
                            currentParticleConfig = currentParticleConfig.copy(
                                count = v.toInt().coerceIn(1, 20000)
                            )
                            onParticlePropertyChange?.invoke(current.key, currentParticleConfig)
                        }
                    )

                    PropertySliderRow(
                        label = "Speed",
                        value = currentParticleConfig.speed,
                        range = 20f..500f,
                        format = "%.0f",
                        suffix = "",
                        onValueChange = { v ->
                            currentParticleConfig = currentParticleConfig.copy(speed = v)
                            onParticlePropertyChange?.invoke(current.key, currentParticleConfig)
                        }
                    )

                    PropertySliderRow(
                        label = "Spread",
                        value = currentParticleConfig.spread,
                        range = 10f..360f,
                        format = "%.0f",
                        suffix = "°",
                        onValueChange = { v ->
                            currentParticleConfig = currentParticleConfig.copy(spread = v)
                            onParticlePropertyChange?.invoke(current.key, currentParticleConfig)
                        }
                    )

                    PropertySliderRow(
                        label = "Direction",
                        value = currentParticleConfig.direction,
                        range = -180f..180f,
                        format = "%.0f",
                        suffix = "°",
                        onValueChange = { v ->
                            currentParticleConfig = currentParticleConfig.copy(direction = v)
                            onParticlePropertyChange?.invoke(current.key, currentParticleConfig)
                        }
                    )

                    PropertySliderRow(
                        label = "Gravity",
                        value = currentParticleConfig.gravity,
                        range = -300f..300f,
                        format = "%.0f",
                        suffix = "",
                        onValueChange = { v ->
                            currentParticleConfig = currentParticleConfig.copy(gravity = v)
                            onParticlePropertyChange?.invoke(current.key, currentParticleConfig)
                        }
                    )

                    PropertySliderRow(
                        label = "Size Min",
                        value = currentParticleConfig.sizeMin,
                        range = 0.1f..500f,
                        format = "%.1f",
                        suffix = "",
                        onValueChange = { v ->
                            currentParticleConfig = currentParticleConfig.copy(sizeMin = v)
                            onParticlePropertyChange?.invoke(current.key, currentParticleConfig)
                        }
                    )

                    PropertySliderRow(
                        label = "Size Max",
                        value = currentParticleConfig.sizeMax,
                        range = 0.1f..500f,
                        format = "%.1f",
                        suffix = "",
                        onValueChange = { v ->
                            currentParticleConfig = currentParticleConfig.copy(sizeMax = v)
                            onParticlePropertyChange?.invoke(current.key, currentParticleConfig)
                        }
                    )

                    ColorPickerField(
                        label = "Color 1",
                        colorLong = currentParticleConfig.color1,
                        onChange = { newCol ->
                            currentParticleConfig = currentParticleConfig.copy(color1 = newCol)
                            onParticlePropertyChange?.invoke(current.key, currentParticleConfig)
                        }
                    )

                    ColorPickerField(
                        label = "Color 2",
                        colorLong = currentParticleConfig.color2,
                        onChange = { newCol ->
                            currentParticleConfig = currentParticleConfig.copy(color2 = newCol)
                            onParticlePropertyChange?.invoke(current.key, currentParticleConfig)
                        }
                    )

                    PropertySliderRow(
                        label = "Lifetime",
                        value = currentParticleConfig.lifetimeMs.toFloat(),
                        range = 200f..5000f,
                        format = "%.0f",
                        suffix = "ms",
                        onValueChange = { v ->
                            currentParticleConfig = currentParticleConfig.copy(
                                lifetimeMs = v.toLong().coerceIn(200L, 5000L)
                            )
                            onParticlePropertyChange?.invoke(current.key, currentParticleConfig)
                        }
                    )

                    PropertySliderRow(
                        label = "Repeat",
                        value = currentParticleConfig.triggerEverySec,
                        range = 0f..10f,
                        format = "%.2f",
                        suffix = "s",
                        onValueChange = { v ->
                            currentParticleConfig = currentParticleConfig.copy(
                                triggerEverySec = v.coerceIn(0f, 10f)
                            )
                            onParticlePropertyChange?.invoke(current.key, currentParticleConfig)
                        }
                    )

                    PropertySliderRow(
                        label = "Trigger X",
                        value = currentParticleConfig.triggerX * 100f,
                        range = 0f..100f,
                        format = "%.0f",
                        suffix = "%",
                        onValueChange = { v ->
                            currentParticleConfig = currentParticleConfig.copy(
                                triggerX = (v / 100f).coerceIn(0f, 1f)
                            )
                            onParticlePropertyChange?.invoke(current.key, currentParticleConfig)
                        }
                    )

                    PropertySliderRow(
                        label = "Trigger Y",
                        value = currentParticleConfig.triggerY * 100f,
                        range = 0f..100f,
                        format = "%.0f",
                        suffix = "%",
                        onValueChange = { v ->
                            currentParticleConfig = currentParticleConfig.copy(
                                triggerY = (v / 100f).coerceIn(0f, 1f)
                            )
                            onParticlePropertyChange?.invoke(current.key, currentParticleConfig)
                        }
                    )

                    // Toggles
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            "Glow" to currentParticleConfig.glow,
                            "Fade" to currentParticleConfig.fadeOut
                        ).forEach { (label, active) ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(28.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(
                                        if (active) Color(0xFF7C3AED)
                                        else Color(0xFF181818)
                                    )
                                    .pointerInput(label, active) {
                                        detectTapGestures {
                                            currentParticleConfig = when (label) {
                                                "Glow" -> currentParticleConfig.copy(glow = !active)
                                                "Fade" -> currentParticleConfig.copy(fadeOut = !active)
                                                else -> currentParticleConfig
                                            }
                                            onParticlePropertyChange?.invoke(
                                                current.key, currentParticleConfig
                                            )
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "$label ${if (active) "ON" else "OFF"}",
                                    color = Color.White, fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
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
                                            current.kind == EffectKind.PARTICLE &&
                                                    onApplyParticleLayer != null -> {
                                                onApplyParticleLayer(
                                                    current.key, intensity, currentParticleConfig
                                                )
                                            }
                                            current.kind == EffectKind.LIGHTNING &&
                                                    onApplyLightningLayer != null -> {
                                                onApplyLightningLayer(
                                                    current.key, intensity, currentLightningConfig
                                                )
                                            }
                                            current.kind == EffectKind.FIRE &&
                                                    onApplyFireLayer != null -> {
                                                onApplyFireLayer(
                                                    current.key, intensity, currentFireConfig
                                                )
                                            }
                                            current.kind == EffectKind.DUST &&
                                                    onApplyDustLayer != null -> {
                                                onApplyDustLayer(
                                                    current.key, intensity, currentDustConfig
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
                    Canvas(modifier = Modifier.matchParentSize()) {
                        drawEdgeGlow(preset.edgeGlow, clockSec, radiusScale = 1f / 3f)
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
                // ⚡ Lightning thumbnail preview
                if (kind == EffectKind.LIGHTNING && preset.lightning != null) {
                    val thumbTime = clockSec % 2f
                    val flash = com.moody.moodyvideoeditor.utils.LightningEngine
                        .flashAt(preset.lightning, thumbTime)

                    if (flash > 0.01f) {
                        Canvas(modifier = Modifier.matchParentSize()) {
                            val flashColor = Color(preset.lightning.color)

                            drawRect(
                                color = flashColor.copy(
                                    alpha = (flash * 0.30f).coerceIn(0f, 1f)
                                ),
                                size = size,
                                blendMode = BlendMode.Screen
                            )

                            val cycleIdx = kotlin.math.floor(
                                thumbTime *
                                        preset.lightning.frequency.coerceIn(0.2f, 5f)
                            ).toInt()

                            val segments =
                                com.moody.moodyvideoeditor.utils.LightningEngine
                                    .generateBolt(
                                        cfg = preset.lightning,
                                        cycleIndex = cycleIdx,
                                        W = size.width,
                                        H = size.height
                                    )

                            val trunkW = (size.width / 100f) *
                                    (1f + flash * 1.5f)

                            // outer glow
                            segments.forEach { seg ->
                                drawLine(
                                    color = flashColor.copy(
                                        alpha = (seg.alpha * flash * 0.4f)
                                            .coerceIn(0f, 1f)
                                    ),
                                    start = Offset(seg.x1, seg.y1),
                                    end = Offset(seg.x2, seg.y2),
                                    strokeWidth = trunkW * seg.thickness * 3f,
                                    cap = androidx.compose.ui.graphics.StrokeCap.Round,
                                    blendMode = BlendMode.Screen
                                )
                            }
                            // mid
                            segments.forEach { seg ->
                                drawLine(
                                    color = flashColor.copy(
                                        alpha = (seg.alpha * flash * 0.8f)
                                            .coerceIn(0f, 1f)
                                    ),
                                    start = Offset(seg.x1, seg.y1),
                                    end = Offset(seg.x2, seg.y2),
                                    strokeWidth = trunkW * seg.thickness * 1.6f,
                                    cap = androidx.compose.ui.graphics.StrokeCap.Round,
                                    blendMode = BlendMode.Screen
                                )
                            }
                            // white core
                            segments.forEach { seg ->
                                drawLine(
                                    color = Color.White.copy(
                                        alpha = (flash * 0.95f).coerceIn(0f, 1f)
                                    ),
                                    start = Offset(seg.x1, seg.y1),
                                    end = Offset(seg.x2, seg.y2),
                                    strokeWidth = trunkW * seg.thickness * 0.7f,
                                    cap = androidx.compose.ui.graphics.StrokeCap.Round
                                )
                            }
                        }
                    }
                }
                // 🔥 Fire thumbnail preview
                if (kind == EffectKind.FIRE && preset.fire != null) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        try {
                            val cfg = preset.fire
                            val thumbTime = (clockSec % 3f)
                            drawIntoCanvas { canvas ->
                                com.moody.moodyvideoeditor.utils.FireEngine
                                    .drawOnCanvas(
                                        canvas = canvas.nativeCanvas,
                                        cfg = cfg,
                                        localTimeSec = thumbTime,
                                        W = size.width.toInt(),
                                        H = size.height.toInt()
                                    )
                            }
                        } catch (_: Throwable) {
                        }
                    }
                }
                // ✨💥 Particle thumbnail preview
                if (kind == EffectKind.PARTICLE && preset.particle != null) {
                    Canvas(modifier = Modifier.matchParentSize()) {
                        try {
                            val cfg = preset.particle
                            val W = size.width
                            val H = size.height
                            val localSec = clockSec % 2f

                            val cycleSec = cfg.triggerEverySec
                            val cycleIdx: Int
                            val localT: Float
                            if (cycleSec <= 0.01f) {
                                if (localSec * 1000f >
                                    cfg.burstDurationMs + cfg.lifetimeMs
                                ) return@Canvas
                                cycleIdx = 0
                                localT = localSec
                            } else {
                                cycleIdx =
                                    kotlin.math.floor(localSec / cycleSec).toInt()
                                localT = localSec - cycleIdx * cycleSec
                            }

                            val localMs = (localT * 1000f).toLong()

                            // Shockwave
                            if (cfg.shockwave) {
                                val waveFrac =
                                    (localMs.toFloat() / 800f).coerceIn(0f, 1f)
                                if (waveFrac < 1f) {
                                    val ringRadius = waveFrac * W * 0.7f
                                    val ringAlpha =
                                        (1f - waveFrac).coerceIn(0f, 1f)
                                    val c1 = Color(cfg.color1)
                                    drawCircle(
                                        color = c1.copy(alpha = ringAlpha * 0.8f),
                                        radius = ringRadius,
                                        center = Offset(
                                            cfg.triggerX * W,
                                            cfg.triggerY * H
                                        ),
                                        style =
                                            androidx.compose.ui.graphics.drawscope
                                                .Stroke(
                                                    width = (W *
                                                            cfg.shockwaveThickness)
                                                        .coerceAtLeast(1f)
                                                ),
                                        blendMode = BlendMode.Screen
                                    )
                                }
                            }

                            // Flash
                            if (cfg.flashOnTrigger) {
                                val flashFrac =
                                    (localMs / 150f).coerceIn(0f, 1f)
                                if (flashFrac < 1f) {
                                    val c1 = Color(cfg.color1)
                                    drawRect(
                                        color = c1.copy(
                                            alpha = (1f - flashFrac) * 0.55f
                                        ),
                                        size =
                                            androidx.compose.ui.geometry.Size(W, H),
                                        blendMode = BlendMode.Screen
                                    )
                                }
                            }

                            // Cap for thumb perf
                            val thumbCfg = if (cfg.count > 200)
                                cfg.copy(count = 200) else cfg

                            val particles =
                                com.moody.moodyvideoeditor.utils.ParticleEngine
                                    .generateParticles(thumbCfg, cycleIdx)
                            val intensityMul =
                                (cfg.masterIntensity / 100f).coerceIn(0f, 2f)
                            val sizeScale = (W / 250f).coerceIn(0.12f, 1f)

                            particles.forEach { p ->
                                val age = localMs - p.birthMs
                                if (age < 0L || age > cfg.lifetimeMs) {
                                    return@forEach
                                }

                                val st =
                                    com.moody.moodyvideoeditor.utils.ParticleEngine
                                        .computeState(
                                            thumbCfg, p, age, W, H
                                        ) ?: return@forEach

                                val c1 = Color(cfg.color1)
                                val c2 = Color(cfg.color2)
                                val mix = p.colorMix
                                val finalColor = Color(
                                    red = c1.red * (1f - mix) + c2.red * mix,
                                    green = c1.green * (1f - mix) + c2.green * mix,
                                    blue = c1.blue * (1f - mix) + c2.blue * mix,
                                    alpha = (st.alpha * intensityMul)
                                        .coerceIn(0f, 1f)
                                )

                                val thumbSize = (st.size * sizeScale)
                                    .coerceIn(0.4f, 12f)

                                if (cfg.glow) {
                                    drawCircle(
                                        brush = Brush.radialGradient(
                                            colors = listOf(
                                                finalColor,
                                                finalColor.copy(alpha = 0f)
                                            ),
                                            center = Offset(st.x, st.y),
                                            radius = thumbSize * 2.5f
                                        ),
                                        radius = thumbSize * 2.5f,
                                        center = Offset(st.x, st.y),
                                        blendMode = BlendMode.Screen
                                    )
                                }
                                drawCircle(
                                    color = finalColor,
                                    radius = thumbSize,
                                    center = Offset(st.x, st.y)
                                )
                            }
                        } catch (_: Throwable) {
                        }
                    }
                }
                if (kind == EffectKind.DUST && preset.dust != null) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        try {
                            val cfg = preset.dust.copy(
                                count = preset.dust.count.coerceAtMost(120)
                            )
                            val thumbTime = (clockSec % 4f)
                            drawIntoCanvas { canvas ->
                                com.moody.moodyvideoeditor.utils.DustEngine
                                    .drawOnCanvas(
                                        canvas = canvas.nativeCanvas,
                                        cfg = cfg,
                                        localTimeSec = thumbTime,
                                        W = size.width.toInt(),
                                        H = size.height.toInt()
                                    )
                            }
                        } catch (_: Throwable) {
                        }
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