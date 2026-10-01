package com.moody.moodyvideoeditor.ui.features

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.foundation.Image
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moody.moodyvideoeditor.R
import com.moody.moodyvideoeditor.data.EditorClip
import com.moody.moodyvideoeditor.data.FilterPreset
import com.moody.moodyvideoeditor.data.FilterState
import com.moody.moodyvideoeditor.ui.components.FeaturePanel
import com.moody.moodyvideoeditor.utils.FiltersEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun FiltersPanel(
    editLayerId: String? = null,
    initialFilters: FilterState? = null,
    previewClip: EditorClip? = null,
    previewTimeMs: Long = 0L,
    onPreviewFilters: (FilterState?) -> Unit,
    onApplyAsLayer: (FilterState) -> Unit,
    onUpdateLayer: (FilterState) -> Unit = {},
    onDeleteLayer: () -> Unit = {},
    onClose: () -> Unit
) {
    val isEditMode = editLayerId != null
    val context = LocalContext.current

    var selectedCategory by remember {
        mutableStateOf(FilterState.PRESET_CATEGORIES.first().first)
    }
    var selectedPreset by remember { mutableStateOf<FilterPreset?>(null) }
    var intensity by remember { mutableStateOf(100f) }
    var currentFilters by remember {
        mutableStateOf(initialFilters ?: FilterState())
    }

    // 🆕 Load reference image ONCE
    var refBitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(Unit) {
        refBitmap = withContext(Dispatchers.IO) {
            loadRefImage(context)
        }
    }

    LaunchedEffect(Unit) {
        if (isEditMode && initialFilters != null) {
            onPreviewFilters(initialFilters)
        } else {
            onPreviewFilters(null)
        }
    }

    FeaturePanel(
        title = if (isEditMode) "🎨 Edit Filter Layer" else "🎨 Filters",
        onClose = {
            onPreviewFilters(null)
            onClose()
        }
    ) {

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 320.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {

            // ═══════════════════════════════════════════════════════
            //  CATEGORY CHIPS
            // ═══════════════════════════════════════════════════════
            Text(
                "Category (${FilterState.PRESETS.size} filters)",
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
                FilterState.PRESET_CATEGORIES.forEach { (key, label) ->
                    val isActive = selectedCategory == key
                    Box(
                        modifier = Modifier
                            .height(30.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isActive) Color(0xFF7C3AED) else Color(0xFF181818)
                            )
                            .pointerInput(key) {
                                detectTapGestures { selectedCategory = key }
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

            // ═══════════════════════════════════════════════════════
            //  PRESET CHIPS — Reference image with filter applied
            // ═══════════════════════════════════════════════════════
            val categoryPresets = FilterState.presetsInCategory(selectedCategory)

            Text(
                "Tap to preview (${categoryPresets.size})",
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
                categoryPresets.forEach { preset ->
                    val isSelected = selectedPreset?.key == preset.key
                    FilterThumbChip(
                        preset = preset,
                        isSelected = isSelected,
                        refBitmap = refBitmap,
                        onClick = {
                            selectedPreset = preset
                            intensity = 100f
                            currentFilters = scalePresetIntensity(preset, intensity)
                            onPreviewFilters(currentFilters)
                            if (isEditMode) onUpdateLayer(currentFilters)
                        }
                    )
                }
            }

            // ═══════════════════════════════════════════════════════
            //  INTENSITY SLIDER
            // ═══════════════════════════════════════════════════════
            selectedPreset?.let { preset ->
                Spacer(Modifier.height(2.dp))
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
                        modifier = Modifier.width(60.dp)
                    )
                    Slider(
                        value = intensity.coerceIn(0f, 200f),
                        onValueChange = { v ->
                            intensity = v
                            currentFilters = scalePresetIntensity(preset, v)
                            onPreviewFilters(currentFilters)
                            if (isEditMode) onUpdateLayer(currentFilters)
                        },
                        valueRange = 0f..200f,
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF7C3AED),
                            activeTrackColor = Color(0xFF7C3AED),
                            inactiveTrackColor = Color(0xFF303030)
                        )
                    )
                    Text(
                        "${intensity.toInt()}%",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(44.dp)
                    )
                }
            }

            Spacer(Modifier.height(6.dp))

            // ═══════════════════════════════════════════════════════
            //  ACTION BUTTONS
            // ═══════════════════════════════════════════════════════
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
                                    onPreviewFilters(null)
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "🗑 Delete",
                            color = Color(0xFFFF6B6B),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(2f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF7C3AED))
                            .pointerInput(Unit) {
                                detectTapGestures {
                                    onPreviewFilters(null)
                                    onClose()
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "✓ Done",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
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
                                    currentFilters = FilterState()
                                    onPreviewFilters(null)
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "↺ Reset",
                            color = Color(0xFFAAAAAA),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(2f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF7C3AED))
                            .pointerInput(Unit) {
                                detectTapGestures {
                                    onApplyAsLayer(currentFilters)
                                    selectedPreset = null
                                    intensity = 100f
                                    currentFilters = FilterState()
                                    onPreviewFilters(null)
                                    onClose()
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "✨ Apply as Layer",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Text(
                if (isEditMode)
                    "💡 Changes live update ho rahi hain layer pe"
                else
                    "💡 Layer timeline pe banegi — drag, trim, stretch kar sakte ho",
                color = Color(0xFF666666),
                fontSize = 9.sp,
                modifier = Modifier.padding(start = 4.dp)
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  🆕 REFERENCE IMAGE LOADER
// ═══════════════════════════════════════════════════════════════
private fun loadRefImage(context: android.content.Context): Bitmap? {
    return try {
        // Decode from drawable resource
        val opts = BitmapFactory.Options().apply {
            inScaled = false
        }
        val bmp = BitmapFactory.decodeResource(
            context.resources,
            R.drawable.filter_ref_image,
            opts
        ) ?: return null

        // Square crop (center) for consistent look
        val minDim = minOf(bmp.width, bmp.height)
        val x = (bmp.width - minDim) / 2
        val y = (bmp.height - minDim) / 2
        val cropped = Bitmap.createBitmap(bmp, x, y, minDim, minDim)

        // Scale to 120×120
        val scaled = Bitmap.createScaledBitmap(cropped, 120, 120, true)

        if (cropped != scaled && cropped != bmp) cropped.recycle()
        if (bmp != scaled && bmp != cropped) bmp.recycle()

        scaled
    } catch (e: Throwable) {
        Log.e("FILTER_REF", "Failed to load reference image", e)
        null
    }
}

// ═══════════════════════════════════════════════════════════════
//  FILTER THUMB CHIP — reference image + filter applied
// ═══════════════════════════════════════════════════════════════
@Composable
private fun FilterThumbChip(
    preset: FilterPreset,
    isSelected: Boolean,
    refBitmap: Bitmap?,
    onClick: () -> Unit
) {
    val filterState = remember(preset.key) {
        preset.toFilterState()
    }

    val colorFilter = remember(preset.key, refBitmap) {
        try {
            val cm = FiltersEngine.buildColorMatrix(filterState)
            ColorFilter.colorMatrix(ColorMatrix(cm.array))
        } catch (_: Throwable) {
            null
        }
    }

    Column(
        modifier = Modifier
            .width(76.dp)
            .height(102.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (isSelected) Color(0xFF2A1F4D) else Color(0xFF181818))
            .pointerInput(preset.key) {
                detectTapGestures { onClick() }
            }
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {

        // Thumbnail — ref image with filter applied
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF0F0F0F)),
            contentAlignment = Alignment.Center
        ) {
            if (refBitmap != null && !refBitmap.isRecycled) {
                Image(
                    bitmap = refBitmap.asImageBitmap(),
                    contentDescription = preset.label,
                    contentScale = ContentScale.Crop,
                    colorFilter = colorFilter,
                    modifier = Modifier
                        .size(60.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
            } else {
                // Fallback if ref image not loaded yet
                Text(preset.icon, fontSize = 22.sp)
            }

            // Selection overlay
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF7C3AED).copy(alpha = 0.15f))
                )
            }
        }

        // Label
        Text(
            preset.label,
            color = if (isSelected) Color(0xFFA78BFA) else Color.White,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            lineHeight = 10.sp
        )
    }
}

/**
 * Scale preset values by intensity (0-200%).
 */
private fun scalePresetIntensity(
    preset: FilterPreset,
    intensityPct: Float
): FilterState {
    val t = (intensityPct / 100f).coerceIn(0f, 2f)

    fun scale100(presetVal: Float): Float =
        (100f + (presetVal - 100f) * t).coerceIn(0f, 300f)

    fun scale0(presetVal: Float): Float =
        (presetVal * t).coerceIn(0f, 300f)

    return FilterState(
        brightness = scale100(preset.brightness),
        contrast = scale100(preset.contrast),
        saturation = scale100(preset.saturation),
        hue = scale0(preset.hue).coerceIn(0f, 360f),
        grayscale = scale0(preset.grayscale).coerceIn(0f, 100f),
        sepia = scale0(preset.sepia).coerceIn(0f, 100f),
        invert = scale0(preset.invert).coerceIn(0f, 100f),
        blur = scale0(preset.blur).coerceIn(0f, 30f),
        opacity = scale100(preset.opacity).coerceIn(0f, 100f)
    )
}