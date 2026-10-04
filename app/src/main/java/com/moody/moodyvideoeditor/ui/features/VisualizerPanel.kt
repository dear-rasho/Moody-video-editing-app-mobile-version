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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moody.moodyvideoeditor.R
import com.moody.moodyvideoeditor.data.VisualizerPreset
import com.moody.moodyvideoeditor.data.VisualizerState
import com.moody.moodyvideoeditor.ui.components.ColorPickerField
import com.moody.moodyvideoeditor.ui.components.FeaturePanel
import com.moody.moodyvideoeditor.utils.VisualizerEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


@Composable
fun VisualizerPanel(
    current: VisualizerState,
    hasAudio: Boolean,
    onStateChanged: (VisualizerState) -> Unit,
    onPickImage: () -> Unit,
    onClearImage: () -> Unit,
    onRemove: () -> Unit,
    onClose: () -> Unit
) {
    var subView by remember { mutableStateOf("main") }


    //  TEXT EDITOR SUB-VIEW

    if (subView == "text") {
        TextPanel(
            currentText = current.textState,
            hasTextClipSelected = true,
            onTextChanged = { newText ->
                onStateChanged(
                    current.copy(
                        textState = newText,
                        textContent = newText.content
                    )
                )
            },
            onCreateNew = {},
            onRemove = {
                onStateChanged(
                    current.copy(
                        showText = false,
                        textContent = "",
                        textState = current.textState.copy(content = "")
                    )
                )
                subView = "main"
            },
            onApplyTemplate = {},
            onClose = { subView = "main" }
        )
        return
    }


    //  MAIN VIEW

    val context = LocalContext.current

    var refBitmap by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(Unit) {
        refBitmap = withContext(Dispatchers.IO) {
            loadRefImage(context)
        }
    }

<<<<<<< HEAD
=======
    // Shared animation clock (0..10 sec loop)
>>>>>>> 5681a8706659a5f06557a27780f8683a58525bbd
    val infiniteTransition = rememberInfiniteTransition(label = "vizClock")
    val clockSec by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 10f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 10000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "clockSec"
    )

    FeaturePanel(title = "🎵 Visualizer", onClose = onClose) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 600.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {

            if (!hasAudio) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF2A0F0F))
                        .padding(10.dp)
                ) {
                    Text(
                        "⚠️ Select an audio clip first",
                        color = Color(0xFFFF6B6B),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

<<<<<<< HEAD
=======
            // BEAT STATUS
>>>>>>> 5681a8706659a5f06557a27780f8683a58525bbd
            if (current.hasBeats) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF0F2A1A))
                        .padding(10.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            "✅ ${current.beatTimesMs.size} beats detected",
                            color = Color(0xFF22C55E),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Visualizer pulses on every beat",
                            color = Color(0xFF888888),
                            fontSize = 9.sp
                        )
                    }
                }
            }

<<<<<<< HEAD
            // ═══════════════════════════════════════════════════════
            //  PRESET PICKER
            // ═══════════════════════════════════════════════════════
=======

            //  PRESET PICKER

>>>>>>> 5681a8706659a5f06557a27780f8683a58525bbd
            PresetPicker(
                current = current,
                refBitmap = refBitmap,
                clockSec = clockSec,
                onPick = { preset ->
                    onStateChanged(current.copy(preset = preset))
                }
            )

<<<<<<< HEAD
            // ═══════════════════════════════════════════════════════
            //  BEAT REACTION
            // ═══════════════════════════════════════════════════════
=======
            // BEAT REACTION
>>>>>>> 5681a8706659a5f06557a27780f8683a58525bbd
            ValueSliderRow(
                label = "Beat Reaction",
                value = current.beatReaction,
                range = 0f..2f,
                format = "%.2f"
            ) {
                onStateChanged(current.copy(beatReaction = it))
            }

<<<<<<< HEAD
            // ═══════════════════════════════════════════════════════
            //  CIRCLE CONTENT
            // ═══════════════════════════════════════════════════════
=======

            //  CIRCLE CONTENT

>>>>>>> 5681a8706659a5f06557a27780f8683a58525bbd
            Spacer(Modifier.height(4.dp))
            SectionLabel("Circle Content")

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ToggleChip(
                    label = "🖼️ Image",
                    active = current.showImage,
                    modifier = Modifier.weight(1f)
                ) {
                    onStateChanged(current.copy(showImage = !current.showImage))
                }
                ToggleChip(
                    label = "📝 Text",
                    active = current.showText,
                    modifier = Modifier.weight(1f)
                ) {
                    onStateChanged(current.copy(showText = !current.showText))
                }
            }

            if (current.showImage && current.showText) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Order",
                        color = Color(0xFF888888),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(60.dp)
                    )
                    ToggleChip(
                        label = "Image → Text",
                        active = current.textOnTopOfImage,
                        modifier = Modifier.weight(1f)
                    ) {
                        onStateChanged(current.copy(textOnTopOfImage = true))
                    }
                    ToggleChip(
                        label = "Text → Image",
                        active = !current.textOnTopOfImage,
                        modifier = Modifier.weight(1f)
                    ) {
                        onStateChanged(current.copy(textOnTopOfImage = false))
                    }
                }
            }

            if (current.showText) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF0F0F0F))
                        .padding(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Text:",
                                color = Color(0xFF888888),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                current.textState.content.ifBlank { "(empty)" },
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2
                            )
                        }
                        Box(
                            modifier = Modifier
                                .height(32.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF7C3AED))
                                .pointerInput(Unit) {
                                    detectTapGestures { subView = "text" }
                                }
                                .padding(horizontal = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "✏️ Edit",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                ValueSliderRow(
                    label = "Text Size",
                    value = current.textState.fontSize.toFloat(),
                    range = 8f..200f,
                    format = "%.0f"
                ) { v ->
                    val newTextState = current.textState.copy(fontSize = v.toInt())
                    onStateChanged(
                        current.copy(
                            textState = newTextState,
                            textContent = newTextState.content
                        )
                    )
                }
            }

            if (current.showImage) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF7C3AED))
                            .pointerInput(Unit) {
                                detectTapGestures { onPickImage() }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (current.imageUri == null) "📷 Choose Image"
                            else "📷 Change Image",
                            color = Color.White, fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (current.imageUri != null) {
                        Box(
                            modifier = Modifier
                                .height(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFFF6B6B).copy(alpha = 0.2f))
                                .pointerInput(Unit) {
                                    detectTapGestures { onClearImage() }
                                }
                                .padding(horizontal = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "✕ Remove", color = Color(0xFFFF6B6B),
                                fontSize = 10.sp, fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                if (current.imageUri != null) {
                    ToggleRow("Bass-only Pulse", current.imageBassOnly) {
                        onStateChanged(current.copy(imageBassOnly = it))
                    }
                    ToggleRow("Idle Rotation", current.imageIdleRotation) {
                        onStateChanged(current.copy(imageIdleRotation = it))
                    }
<<<<<<< HEAD
                    ValueSliderRow(
                        "Img Size", current.imageScale, 0.2f..1f, "%.2f"
                    ) {
                        onStateChanged(current.copy(imageScale = it))
                    }
                    ValueSliderRow(
                        "Img Pulse", current.imagePulseAmount, 0f..0.5f, "%.2f"
                    ) {
                        onStateChanged(current.copy(imagePulseAmount = it))
                    }
                    ValueSliderRow(
                        "Img Opacity", current.imageOpacity, 0f..1f, "%.2f"
                    ) {
=======
                    ValueSliderRow("Img Size", current.imageScale, 0.2f..1f, "%.2f") {
                        onStateChanged(current.copy(imageScale = it))
                    }
                    ValueSliderRow("Img Pulse", current.imagePulseAmount, 0f..0.5f, "%.2f") {
                        onStateChanged(current.copy(imagePulseAmount = it))
                    }
                    ValueSliderRow("Img Opacity", current.imageOpacity, 0f..1f, "%.2f") {
>>>>>>> 5681a8706659a5f06557a27780f8683a58525bbd
                        onStateChanged(current.copy(imageOpacity = it))
                    }
                }
            }

<<<<<<< HEAD
            // ═══════════════════════════════════════════════════════
            //  AUDIO ANALYSIS
            // ═══════════════════════════════════════════════════════
            Spacer(Modifier.height(6.dp))
            SectionLabel("Audio Analysis")

            ValueSliderRow("Start Hz", current.startFrequencyHz, 20f..2000f, "%.0f") { v ->
=======

            //  ADVANCED AUDIO SETTINGS

            Spacer(Modifier.height(6.dp))
            SectionLabel("Audio Analysis")

            ValueSliderRow(
                "Start Hz",
                current.startFrequencyHz,
                20f..2000f,
                "%.0f"
            ) { v ->
>>>>>>> 5681a8706659a5f06557a27780f8683a58525bbd
                onStateChanged(
                    current.copy(
                        startFrequencyHz = v.coerceAtMost(current.endFrequencyHz - 20f)
                    )
                )
            }

<<<<<<< HEAD
            ValueSliderRow("End Hz", current.endFrequencyHz, 20f..2000f, "%.0f") { v ->
=======
            ValueSliderRow(
                "End Hz",
                current.endFrequencyHz,
                20f..2000f,
                "%.0f"
            ) { v ->
>>>>>>> 5681a8706659a5f06557a27780f8683a58525bbd
                onStateChanged(
                    current.copy(
                        endFrequencyHz = v.coerceAtLeast(current.startFrequencyHz + 20f)
                    )
                )
            }

<<<<<<< HEAD
            ValueSliderRow("Bands", current.bands.toFloat(), 1f..6400f, "%.0f") { v ->
                onStateChanged(current.copy(bands = v.toInt().coerceIn(1, 6400)))
            }

            ValueSliderRow("Max Height", current.maxHeight, 0.1f..3.0f, "%.2f") { v ->
=======
            ValueSliderRow(
                "Bands",
                current.bands.toFloat(),
                1f..6400f,
                "%.0f"
            ) { v ->
                onStateChanged(current.copy(bands = v.toInt().coerceIn(1, 6400)))
            }

            ValueSliderRow(
                "Max Height",
                current.maxHeight,
                0.1f..3.0f,
                "%.2f"
            ) { v ->
>>>>>>> 5681a8706659a5f06557a27780f8683a58525bbd
                onStateChanged(current.copy(maxHeight = v))
            }

            ValueSliderRow(
                "Audio Window",
                current.audioWindowMs.toFloat(),
                20f..2000f,
                "%.0f"
            ) { v ->
                onStateChanged(
                    current.copy(audioWindowMs = v.toInt().coerceIn(20, 2000))
                )
            }

            ValueSliderRow(
                "Audio Offset",
                current.audioOffsetMs.toFloat(),
                -500f..500f,
                "%.0f"
            ) { v ->
                onStateChanged(
                    current.copy(audioOffsetMs = v.toInt().coerceIn(-500, 500))
                )
            }

<<<<<<< HEAD
            // ═══════════════════════════════════════════════════════
            //  BAND INFLUENCES
            // ═══════════════════════════════════════════════════════
            Spacer(Modifier.height(6.dp))
            SectionLabel("Band Influence")

            ValueSliderRow("Bass", current.bassInfluence, 0f..2f, "%.2f") { v ->
                onStateChanged(current.copy(bassInfluence = v))
            }
            ValueSliderRow("Mid", current.midInfluence, 0f..2f, "%.2f") { v ->
                onStateChanged(current.copy(midInfluence = v))
            }
            ValueSliderRow("Treble", current.trebleInfluence, 0f..2f, "%.2f") { v ->
                onStateChanged(current.copy(trebleInfluence = v))
            }
            ValueSliderRow("Overall", current.overallSensitivity, 0f..3f, "%.2f") { v ->
                onStateChanged(current.copy(overallSensitivity = v))
            }

            // ═══════════════════════════════════════════════════════
            //  SMOOTHING
            // ═══════════════════════════════════════════════════════
            Spacer(Modifier.height(6.dp))
            SectionLabel("Smoothing")

            ValueSliderRow("Sensitivity", current.sensitivity, 0.5f..3f, "%.2f") { v ->
                onStateChanged(current.copy(sensitivity = v))
            }
            ValueSliderRow("Attack", current.attack, 0f..1f, "%.2f") { v ->
                onStateChanged(current.copy(attack = v))
            }
            ValueSliderRow("Release", current.release, 0f..1f, "%.2f") { v ->
                onStateChanged(current.copy(release = v))
            }
            ValueSliderRow("Lerp", current.lerpFactor, 0.05f..0.95f, "%.2f") { v ->
                onStateChanged(current.copy(lerpFactor = v))
            }

            // ═══════════════════════════════════════════════════════
            //  BEAT RESPONSE
            // ═══════════════════════════════════════════════════════
            Spacer(Modifier.height(6.dp))
            SectionLabel("Beat Response")

            ValueSliderRow("Beat Pulse", current.beatPulse, 0f..2f, "%.2f") { v ->
                onStateChanged(current.copy(beatPulse = v))
            }
            ValueSliderRow("Beat Glow", current.beatGlow, 0f..2f, "%.2f") { v ->
                onStateChanged(current.copy(beatGlow = v))
            }
            ValueSliderRow(
                "Pulse Duration",
                current.beatPulseDurationMs.toFloat(),
                50f..1000f,
                "%.0f"
            ) { v ->
                onStateChanged(
                    current.copy(
                        beatPulseDurationMs = v.toLong().coerceIn(50L, 1000L)
                    )
                )
            }

            // ═══════════════════════════════════════════════════════
            //  SHAPE / STYLE
            // ═══════════════════════════════════════════════════════
            Spacer(Modifier.height(6.dp))
            SectionLabel("Shape & Style")

            ValueSliderRow("Line Width", current.lineWidth, 0.5f..20f, "%.1f") { v ->
=======

            //  SHAPE / STYLE

            Spacer(Modifier.height(6.dp))
            SectionLabel("Shape & Style")

            ValueSliderRow(
                "Line Width",
                current.lineWidth,
                0.5f..20f,
                "%.1f"
            ) { v ->
>>>>>>> 5681a8706659a5f06557a27780f8683a58525bbd
                onStateChanged(current.copy(lineWidth = v))
            }

            ValueSliderRow(
                "Particle Size",
                current.particleSize,
                0.5f..5.0f,
                "%.2f"
            ) { v ->
                onStateChanged(current.copy(particleSize = v))
            }

<<<<<<< HEAD
            // ═══════════════════════════════════════════════════════
            //  DISPLAY STYLE
            // ═══════════════════════════════════════════════════════
=======

            //  DISPLAY STYLE

>>>>>>> 5681a8706659a5f06557a27780f8683a58525bbd
            Spacer(Modifier.height(6.dp))
            SectionLabel("Display Style")

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(
                    "digital" to "📊 Digital",
                    "analog_lines" to "〰️ Lines",
                    "analog_dots" to "• Dots"
                ).forEach { (key, label) ->
                    val active = current.displayStyle == key
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(34.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (active) Color(0xFFFFD166)
                                else Color(0xFF181818)
                            )
                            .pointerInput(key) {
                                detectTapGestures {
<<<<<<< HEAD
                                    onStateChanged(
                                        current.copy(displayStyle = key)
                                    )
=======
                                    onStateChanged(current.copy(displayStyle = key))
>>>>>>> 5681a8706659a5f06557a27780f8683a58525bbd
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            label,
                            color = if (active) Color.Black else Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

<<<<<<< HEAD
            // ═══════════════════════════════════════════════════════
            //  SIDE MODE
            // ═══════════════════════════════════════════════════════
=======

            //  SIDE MODE

>>>>>>> 5681a8706659a5f06557a27780f8683a58525bbd
            Spacer(Modifier.height(6.dp))
            SectionLabel("Side Mode")

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(
                    "a" to "A (up)",
                    "b" to "B (down)",
                    "both" to "Both"
                ).forEach { (key, label) ->
                    val active = current.sideMode == key
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(34.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (active) Color(0xFFFFD166)
                                else Color(0xFF181818)
                            )
                            .pointerInput(key) {
                                detectTapGestures {
                                    onStateChanged(current.copy(sideMode = key))
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            label,
                            color = if (active) Color.Black else Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

<<<<<<< HEAD
            // ═══════════════════════════════════════════════════════
            //  GLOW
            // ═══════════════════════════════════════════════════════
            Spacer(Modifier.height(6.dp))
            SectionLabel("Glow")

            ToggleRow("Glow", current.glow) {
                onStateChanged(current.copy(glow = it))
            }
            ValueSliderRow(
                "Intensity", current.glowIntensity, 0f..1f, "%.2f"
            ) { v ->
                onStateChanged(current.copy(glowIntensity = v))
            }
            ToggleRow("Peak Hold", current.peakHold) {
                onStateChanged(current.copy(peakHold = it))
            }

            // ═══════════════════════════════════════════════════════
            //  RING STYLE
            // ═══════════════════════════════════════════════════════
=======

            //  RING STYLE

>>>>>>> 5681a8706659a5f06557a27780f8683a58525bbd
            Spacer(Modifier.height(6.dp))
            SectionLabel("Ring Style")

            ColorPickerField(
                label = "Color A",
                colorLong = current.color1,
                onChange = { onStateChanged(current.copy(color1 = it)) }
            )
            ColorPickerField(
                label = "Color B",
                colorLong = current.color2,
                onChange = { onStateChanged(current.copy(color2 = it)) }
            )

            ValueSliderRow("Size", current.size, 0.15f..0.6f, "%.2f") {
                onStateChanged(current.copy(size = it))
            }
            ValueSliderRow("Pos X", current.positionX, 0f..1f, "%.2f") {
                onStateChanged(current.copy(positionX = it))
            }
            ValueSliderRow("Pos Y", current.positionY, 0f..1f, "%.2f") {
                onStateChanged(current.copy(positionY = it))
            }
            ValueSliderRow("Opacity", current.opacity, 0f..1f, "%.2f") {
                onStateChanged(current.copy(opacity = it))
            }

<<<<<<< HEAD
=======
            ToggleRow("Glow", current.glow) {
                onStateChanged(current.copy(glow = it))
            }

>>>>>>> 5681a8706659a5f06557a27780f8683a58525bbd
            Spacer(Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFFF6B6B).copy(alpha = 0.15f))
                    .pointerInput(Unit) { detectTapGestures { onRemove() } },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "🗑 Remove Visualizer",
                    color = Color(0xFFFF6B6B), fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}


<<<<<<< HEAD
// ═══════════════════════════════════════════════════════════════
//  PRESET PICKER
// ═══════════════════════════════════════════════════════════════
=======
//  PRESET PICKER

>>>>>>> 5681a8706659a5f06557a27780f8683a58525bbd
@Composable
private fun PresetPicker(
    current: VisualizerState,
    refBitmap: Bitmap?,
    clockSec: Float,
    onPick: (VisualizerPreset) -> Unit
) {
    var activeCategory by remember { mutableStateOf("spectrum") }
    val categoryPresets = VisualizerPreset.byCategory(activeCategory)

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {

<<<<<<< HEAD
=======
        // CATEGORY TABS
>>>>>>> 5681a8706659a5f06557a27780f8683a58525bbd
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            VisualizerPreset.CATEGORIES.forEach { (key, label) ->
                val isActive = activeCategory == key
                Box(
                    modifier = Modifier
                        .height(28.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (isActive) Color(0xFF7C3AED) else Color(0xFF181818)
                        )
                        .pointerInput(key) {
                            detectTapGestures { activeCategory = key }
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

<<<<<<< HEAD
=======
        // PRESET CARDS ROW
>>>>>>> 5681a8706659a5f06557a27780f8683a58525bbd
        Text(
            "Tap to preview · ${categoryPresets.size} presets",
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
                VisualizerPreviewCard(
                    preset = preset,
                    isSelected = current.preset == preset,
                    refBitmap = refBitmap,
                    clockSec = clockSec,
                    onTap = { onPick(preset) }
                )
            }
        }
    }
}


<<<<<<< HEAD
// ═══════════════════════════════════════════════════════════════
//  SINGLE PRESET CARD
// ═══════════════════════════════════════════════════════════════
=======
//  SINGLE PRESET CARD

>>>>>>> 5681a8706659a5f06557a27780f8683a58525bbd
@Composable
private fun VisualizerPreviewCard(
    preset: VisualizerPreset,
    isSelected: Boolean,
    refBitmap: Bitmap?,
    clockSec: Float,
    onTap: () -> Unit
) {
    val hasImage = refBitmap != null && !refBitmap.isRecycled

<<<<<<< HEAD
=======
    // Reference image ko center mein dikhane ke liye — "showImage = true"
    // aur preview state mein imageScale ko bada rakho taaki circle bhare
>>>>>>> 5681a8706659a5f06557a27780f8683a58525bbd
    val previewState = remember(preset) {
        VisualizerState(
            preset = preset,
            color1 = 0xFFFFD166,
            color2 = 0xFF00E5FF,
<<<<<<< HEAD
            size = 0.42f,
=======
            size = 0.42f,           // visualizer size
>>>>>>> 5681a8706659a5f06557a27780f8683a58525bbd
            positionX = 0.5f,
            positionY = 0.5f,
            opacity = 1f,
            glow = true,
<<<<<<< HEAD
=======
            // 🆕 Image center mein dikhao
>>>>>>> 5681a8706659a5f06557a27780f8683a58525bbd
            showImage = true,
            imageScale = 0.55f,
            imageOpacity = 1f,
            showText = false
        )
    }

<<<<<<< HEAD
    LaunchedEffect(refBitmap, preset.key) {
        if (refBitmap != null && !refBitmap.isRecycled) {
=======
    // 🆕 Reference image ko VisualizerEngine ke center content ke roop mein set karo
    LaunchedEffect(refBitmap, preset.key) {
        if (refBitmap != null && !refBitmap.isRecycled) {
            // Image ko VisualizerEngine ke cache mein set karo
>>>>>>> 5681a8706659a5f06557a27780f8683a58525bbd
            VisualizerEngine.setCenterImage(
                "preview_${preset.key}",
                refBitmap.asImageBitmap()
            )
        }
    }

    Column(
        modifier = Modifier
            .width(76.dp)
            .height(102.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (isSelected) Color(0xFF2A1F4D) else Color(0xFF181818)
            )
            .then(
                if (isSelected) Modifier.border(
                    1.5.dp, Color(0xFF7C3AED),
                    RoundedCornerShape(10.dp)
                ) else Modifier
            )
            .pointerInput(preset.key) {
                detectTapGestures { onTap() }
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
<<<<<<< HEAD
=======
            // 🆕 VisualizerEngine pe hi image draw hoga (center content ke roop mein)
            // Isliye Box mein image alag se NAHI dikha rahe
>>>>>>> 5681a8706659a5f06557a27780f8683a58525bbd
            Canvas(modifier = Modifier.fillMaxSize()) {
                try {
                    drawVisualizerPreview(
                        preset = preset,
                        state = previewState,
                        clockSec = clockSec,
                        refBitmap = refBitmap
                    )
                } catch (e: Throwable) {
                    Log.e("VIZ_PREVIEW", "Preview failed: ${preset.key}", e)
                }
            }

            if (isSelected) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF7C3AED).copy(alpha = 0.15f))
                )
            }
        }

        Text(preset.icon, fontSize = 12.sp)

        Text(
            preset.label,
            color = if (isSelected) Color(0xFFA78BFA) else Color.White,
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            lineHeight = 9.sp
        )
    }
}


<<<<<<< HEAD
// ═══════════════════════════════════════════════════════════════
//  PREVIEW DRAW
// ═══════════════════════════════════════════════════════════════
=======
//  PREVIEW DRAW

>>>>>>> 5681a8706659a5f06557a27780f8683a58525bbd
private fun DrawScope.drawVisualizerPreview(
    preset: VisualizerPreset,
    state: VisualizerState,
    clockSec: Float,
<<<<<<< HEAD
    refBitmap: Bitmap?
=======
    refBitmap: Bitmap?      // 🆕
>>>>>>> 5681a8706659a5f06557a27780f8683a58525bbd
) {
    val previewState = state.copy(
        preset = preset,
        opacity = 1f
    )

<<<<<<< HEAD
=======
    // 🆕 Reference image ko VisualizerEngine ke center content ke liye set karo
>>>>>>> 5681a8706659a5f06557a27780f8683a58525bbd
    if (refBitmap != null && !refBitmap.isRecycled) {
        try {
            VisualizerEngine.setCenterImage(
                "preview_${preset.key}",
                refBitmap.asImageBitmap()
            )
        } catch (_: Throwable) {
        }
    }

    VisualizerEngine.drawPreview(
        scope = this,
        state = previewState,
        elapsedSec = clockSec,
        instanceKey = "preview_${preset.key}"
    )
}


<<<<<<< HEAD
// ═══════════════════════════════════════════════════════════════
//  REUSABLE COMPONENTS
// ═══════════════════════════════════════════════════════════════
=======
//  REUSABLE COMPONENTS

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        color = Color(0xFFFFD166),
        fontSize = 9.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 4.dp, top = 2.dp, bottom = 2.dp)
    )
}

>>>>>>> 5681a8706659a5f06557a27780f8683a58525bbd
@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        color = Color(0xFFFFD166),
        fontSize = 9.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 4.dp, top = 2.dp, bottom = 2.dp)
    )
}

@Composable
private fun ToggleChip(
    label: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(36.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (active) Color(0xFF7C3AED) else Color(0xFF181818))
            .pointerInput(label) { detectTapGestures { onClick() } },
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label, color = Color(0xFF888888), fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
        Box(
            modifier = Modifier
                .height(28.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(
                    if (checked) Color(0xFFFFD166) else Color(0xFF181818)
                )
                .pointerInput(checked) {
                    detectTapGestures { onChange(!checked) }
                }
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                if (checked) "ON" else "OFF",
                color = if (checked) Color.Black else Color.White,
                fontSize = 10.sp, fontWeight = FontWeight.Bold
            )
        }
    }
}

<<<<<<< HEAD
=======
/**
 * Slider + value box — user can drag OR type exact value.
 */
>>>>>>> 5681a8706659a5f06557a27780f8683a58525bbd
@Composable
private fun ValueSliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    format: String,
    onChange: (Float) -> Unit
) {
    var textValue by remember(value) {
        mutableStateOf(String.format(format, value))
    }
    var isFocused by remember { mutableStateOf(false) }

    if (!isFocused) {
        val newStr = String.format(format, value)
        if (textValue != newStr) textValue = newStr
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            label,
            color = Color(0xFF888888),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(80.dp)
        )

        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onChange,
            valueRange = range,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFFFFD166),
                activeTrackColor = Color(0xFFFFD166),
                inactiveTrackColor = Color(0xFF303030)
            )
        )

<<<<<<< HEAD
=======
        // 🆕 Value box — type exact value
>>>>>>> 5681a8706659a5f06557a27780f8683a58525bbd
        Box(
            modifier = Modifier
                .width(64.dp)
                .height(28.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF0F0F0F))
                .padding(horizontal = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            BasicTextField(
                value = textValue,
                onValueChange = { newText ->
                    val filtered = newText.filter {
                        it.isDigit() || it == '-' || it == '.' || it == '+'
                    }
                    textValue = filtered
                    filtered.toFloatOrNull()?.let { v ->
                        onChange(v.coerceIn(range.start, range.endInclusive))
                    }
                },
                singleLine = true,
                textStyle = TextStyle(
                    color = Color(0xFFFFD166),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                ),
                cursorBrush = SolidColor(Color(0xFFFFD166)),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { focusState ->
                        isFocused = focusState.isFocused
                        if (!focusState.isFocused) {
                            textValue.toFloatOrNull()?.let { v ->
<<<<<<< HEAD
                                onChange(
                                    v.coerceIn(
                                        range.start,
                                        range.endInclusive
                                    )
                                )
=======
                                onChange(v.coerceIn(range.start, range.endInclusive))
>>>>>>> 5681a8706659a5f06557a27780f8683a58525bbd
                            }
                        }
                    }
            )
        }
    }
}


<<<<<<< HEAD
// ═══════════════════════════════════════════════════════════════
//  REFERENCE IMAGE LOADER
// ═══════════════════════════════════════════════════════════════
=======
//  REFERENCE IMAGE LOADER

>>>>>>> 5681a8706659a5f06557a27780f8683a58525bbd
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
        Log.e("VIZ_REF", "Failed to load reference image", e)
        null
    }
}