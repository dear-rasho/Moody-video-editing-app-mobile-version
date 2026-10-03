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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moody.moodyvideoeditor.R
import com.moody.moodyvideoeditor.data.EffectKind
import com.moody.moodyvideoeditor.data.EffectLibrary
import com.moody.moodyvideoeditor.data.EffectPreset
import com.moody.moodyvideoeditor.data.EffectState
import com.moody.moodyvideoeditor.data.OverlayState
import com.moody.moodyvideoeditor.ui.components.FeaturePanel
import com.moody.moodyvideoeditor.utils.EffectsEngine
import com.moody.moodyvideoeditor.utils.OverlayEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val CATEGORIES = listOf(
    Triple(EffectKind.MOTION, "💫 Motion", EffectLibrary.MOTION_EFFECTS),
    Triple(EffectKind.COLOR, "🎨 Color", EffectLibrary.COLOR_EFFECTS),
    Triple(EffectKind.OVERLAY, "🎬 Overlay", EffectLibrary.OVERLAY_EFFECTS)
)

@Composable
fun EffectsPanel(
    editLayerId: String? = null,
    initialEffectState: EffectState? = null,
    initialPresetKey: String? = null,
    onPreviewEffect: (presetKey: String?, intensity: Float) -> Unit,
    onApplyAsLayer: (presetKey: String, intensity: Float) -> Unit,
    onUpdateIntensity: (intensity: Float) -> Unit = {},
    onDeleteLayer: () -> Unit = {},
    onClose: () -> Unit
) {
    val isEditMode = editLayerId != null
    val context = LocalContext.current

    var selectedCategoryIdx by remember { mutableStateOf(0) }
    var selectedPreset by remember { mutableStateOf<EffectPreset?>(null) }
    var intensity by remember { mutableStateOf(100f) }

    // Reference image
    var refBitmap by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(Unit) {
        refBitmap = withContext(Dispatchers.IO) {
            loadRefImage(context)
        }
    }

    // 🆕 Shared animation clock (0-10 sec loop)
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
                .heightIn(max = 320.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {


            //  CATEGORY CHIPS

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


            //  PRESET CHIPS — ANIMATED PREVIEWS

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
                            onPreviewEffect(preset.key, intensity)
                            if (isEditMode) onUpdateIntensity(intensity)
                        }
                    )
                }
            }


            //  INTENSITY + ACTIONS

            val current = selectedPreset
            if (current != null) {
                Spacer(Modifier.height(2.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF1F1438))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        current.icon,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Previewing: ${current.label}",
                            color = Color(0xFFA78BFA),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            if (isEditMode) "Changes live update ho rahi hain"
                            else "Layer nahi banegi jab tak Apply na karo",
                            color = Color(0xFF666666),
                            fontSize = 9.sp
                        )
                    }
                }

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
                            onPreviewEffect(current.key, v)
                            if (isEditMode) onUpdateIntensity(v)
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
                                        onPreviewEffect(null, 100f)
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
                                        onPreviewEffect(null, 100f)
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "✕ Cancel",
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
                                        onApplyAsLayer(current.key, intensity)
                                        selectedPreset = null
                                        intensity = 100f
                                        onPreviewEffect(null, 100f)
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
                        "💡 Layer pe live update ho raha hai"
                    else
                        "💡 Layer timeline pe banegi — drag, trim, stretch kar sakte ho",
                    color = Color(0xFF666666),
                    fontSize = 9.sp,
                    modifier = Modifier.padding(start = 4.dp)
                )
            } else {
                Text(
                    "👆 Tap any effect chip above to preview",
                    color = Color(0xFF666666),
                    fontSize = 10.sp,
                    modifier = Modifier.padding(start = 4.dp, top = 8.dp)
                )
            }
        }
    }
}


//  EFFECT THUMB CHIP — ANIMATED previews
//  - Color  → image + ColorMatrix (static)
//  - Motion → image + LIVE animated transform
//  - Overlay → image + LIVE animated overlay

@Composable
private fun EffectThumbChip(
    preset: EffectPreset,
    kind: EffectKind,
    isSelected: Boolean,
    refBitmap: Bitmap?,
    clockSec: Float,
    onClick: () -> Unit
) {
    // Color matrix (COLOR only)
    val colorFilter = remember(preset.key, kind) {
        if (kind != EffectKind.COLOR || preset.filters == null) {
            null
        } else {
            try {
                val cm = EffectsEngine.buildColorMatrix(preset.filters)
                ColorFilter.colorMatrix(ColorMatrix(cm.array))
            } catch (_: Throwable) {
                null
            }
        }
    }

    // LIVE motion frame (MOTION only)
    val motionFrame = remember(preset.key, kind, clockSec) {
        if (kind != EffectKind.MOTION || preset.motion == null) {
            EffectsEngine.MotionFrame()
        } else {
            EffectsEngine.computeMotion(preset.motion, clockSec)
        }
    }

    // Overlay state (OVERLAY only)
    val overlayState = remember(preset.key, kind) {
        if (kind != EffectKind.OVERLAY || preset.overlay == null) null
        else OverlayState(
            type = preset.overlay.type,
            intensity = (preset.overlay.intensity * 1.3f).coerceAtMost(200f),
            color = preset.overlay.color
        )
    }

    val hasImage = refBitmap != null && !refBitmap.isRecycled

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

        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF0F0F0F))
        ) {
            if (hasImage) {
                // Image with motion transform
                Image(
                    bitmap = refBitmap!!.asImageBitmap(),
                    contentDescription = preset.label,
                    contentScale = ContentScale.Crop,
                    colorFilter = colorFilter,
                    modifier = Modifier
                        .matchParentSize()
                        .graphicsLayer {
                            translationX = motionFrame.tx
                            translationY = motionFrame.ty
                            scaleX = motionFrame.scale
                            scaleY = motionFrame.scale
                            rotationZ = motionFrame.rotation
                            transformOrigin = TransformOrigin.Center
                        }
                )

                // Overlay animation on top
                if (overlayState != null) {
                    Canvas(modifier = Modifier.matchParentSize()) {
                        try {
                            OverlayEngine.draw(
                                scope = this,
                                time = clockSec,
                                overlay = overlayState
                            )
                        } catch (e: Throwable) {
                            Log.e(
                                "EFFECT_OVERLAY",
                                "Overlay failed: ${overlayState.type}", e
                            )
                        }
                    }
                }
            } else {
                // Fallback icon while image loads
                Box(
                    modifier = Modifier.matchParentSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(preset.icon, fontSize = 24.sp)
                }
            }

            // Selection overlay (top-most)
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(Color(0xFF7C3AED).copy(alpha = 0.15f))
                )
            }
        }

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


//  REFERENCE IMAGE LOADER

private fun loadRefImage(context: android.content.Context): Bitmap? {
    return try {
        val opts = BitmapFactory.Options().apply { inScaled = false }
        val bmp = BitmapFactory.decodeResource(
            context.resources,
            R.drawable.filter_ref_image,
            opts
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
        null
    }
}