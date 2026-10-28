package com.moody.moodyvideoeditor.ui.features

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moody.moodyvideoeditor.data.TransitionLibrary
import com.moody.moodyvideoeditor.data.TransitionState
import com.moody.moodyvideoeditor.R
import com.moody.moodyvideoeditor.ui.components.FeaturePanel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun TransitionsPanel(
    current: TransitionState,
    hasPairAvailable: Boolean,
    hintText: String,
    onTransitionChanged: (TransitionState) -> Unit,
    onRemove: () -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    var referenceBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var secondReferenceBitmap by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(Unit) {
        referenceBitmap = withContext(Dispatchers.IO) {
            loadTransitionReferenceImage(context)
        }
        secondReferenceBitmap = withContext(Dispatchers.IO) {
            loadTransitionImage(context, R.drawable.img_2)
        }
    }

    var activeCategory by remember(current.key) {
        mutableStateOf(TransitionLibrary.find(current.key)?.category ?: "fade")
    }
    val previewAnimation = rememberInfiniteTransition(label = "transition-previews")
    val previewProgress by previewAnimation.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100),
            repeatMode = RepeatMode.Reverse
        ),
        label = "transition-preview-progress"
    )

    FeaturePanel(
        title = "⇄ Transitions (${TransitionLibrary.PRESETS.size - 1})",
        onClose = onClose
    ) {

        if (!hasPairAvailable) {
            EmptyState(
                icon = "⇄",
                title = "No transition target",
                text = hintText.ifBlank {
                    "Select a clip with a neighbor on the same track."
                }
            )
            return@FeaturePanel
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 300.dp)
                .verticalScrollWithScrollbar(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {

            // STATUS BAR
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
                    color = if (current.isActive) Color(0xFFA855F7)
                    else Color(0xFF666666),
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

            // CATEGORY CHIPS
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
                    .horizontalScrollWithTwoFinger(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                TransitionLibrary.CATEGORIES.forEach { (key, label) ->
                    val isActive = activeCategory == key
                    Box(
                        modifier = Modifier
                            .height(30.dp)
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

            // PRESET CHIPS
            val catPresets = TransitionLibrary.presetsInCategory(activeCategory)

            Text(
                "Tap to apply (${catPresets.size})",
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
                    val isActive = current.key == preset.key
                    TransitionChip(
                        preset = preset,
                        label = preset.label,
                        isActive = isActive,
                        previewProgress = previewProgress,
                        referenceBitmap = referenceBitmap,
                        secondReferenceBitmap = secondReferenceBitmap,
                        onClick = {
                            if (isActive && preset.key != "none") {
                                onRemove()
                            } else {
                                onTransitionChanged(
                                    current.copy(key = preset.key)
                                )
                            }
                        }
                    )
                }
            }

            // DURATION SLIDER
            if (current.isActive) {
                Spacer(Modifier.height(4.dp))
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
                        modifier = Modifier.width(48.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun TransitionChip(
    preset: com.moody.moodyvideoeditor.data.TransitionPreset,
    label: String,
    isActive: Boolean,
    previewProgress: Float,
    referenceBitmap: Bitmap?,
    secondReferenceBitmap: Bitmap?,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(76.dp)
            .height(68.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (isActive) Color(0xFF2A1F4D) else Color(0xFF181818))
            .then(
                if (isActive) Modifier.border(
                    1.5.dp, Color(0xFFA855F7), RoundedCornerShape(10.dp)
                ) else Modifier
            )
            .pointerInput(label) { detectTapGestures { onClick() } }
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (preset.key == "none") {
            Text(preset.icon, fontSize = 18.sp)
        } else {
            TransitionPairPreviewIcon(
                transitionKey = preset.key,
                transition = preset.ffmpegXfade,
                progress = previewProgress,
                referenceBitmap = referenceBitmap,
                secondReferenceBitmap = secondReferenceBitmap,
                modifier = Modifier.size(width = 52.dp, height = 30.dp)
            )
        }
        Spacer(Modifier.height(2.dp))
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
private fun TransitionPairPreviewIcon(
    transitionKey: String,
    transition: String,
    progress: Float,
    referenceBitmap: Bitmap?,
    secondReferenceBitmap: Bitmap?,
    modifier: Modifier = Modifier
) {
    val mode = transitionKey
        .removePrefix("style-")
        .lowercase()
    val isStyled = transitionKey.startsWith("style-")
    val effect = if (isStyled) mode else transition.lowercase()
    val p = progress.coerceIn(0f, 1f)
    val eased = p * p * (3f - 2f * p)

    Box(
        modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF151515))
    ) {
        secondReferenceBitmap?.takeUnless { it.isRecycled }?.let { image ->
            Image(
                bitmap = image.asImageBitmap(),
                contentDescription = "Incoming transition preview",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        referenceBitmap?.takeUnless { it.isRecycled }?.let { image ->
            Image(
                bitmap = image.asImageBitmap(),
                contentDescription = "Outgoing transition preview",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        cameraDistance = 32f
                        when {
                            effect.contains("cube-left") || effect.contains("flip-y") ||
                                    effect.contains("card-flip-left") ||
                                    effect.contains("orbit-left") -> {
                                rotationY = -78f * eased
                                translationX = -size.width * 0.12f * eased
                            }
                            effect.contains("cube-right") ||
                                    effect.contains("card-flip-right") ||
                                    effect.contains("orbit-right") -> {
                                rotationY = 78f * eased
                                translationX = size.width * 0.12f * eased
                            }
                            effect.contains("flip-x") || effect.contains("card-flip-up") ->
                                rotationX = 78f * eased
                            effect.contains("card-flip-down") ->
                                rotationX = -78f * eased
                            effect.contains("tilt-left") ->
                                rotationY = -32f * eased
                            effect.contains("tilt-right") ->
                                rotationY = 32f * eased
                            effect.contains("hinge-left") -> {
                                rotationZ = -48f * eased
                                translationX = -size.width * 0.2f * eased
                            }
                            effect.contains("hinge-right") -> {
                                rotationZ = 48f * eased
                                translationX = size.width * 0.2f * eased
                            }
                            effect.contains("shake") || effect.contains("jitter") ||
                                    effect.contains("vibrate") || effect.contains("whip") ||
                                    effect.contains("snap") -> {
                                val amount = if (effect.contains("heavy") || effect.contains("impact")) 7f else 3f
                                translationX = kotlin.math.sin(p * 42f) * amount
                                translationY = kotlin.math.cos(p * 34f) * amount
                                rotationZ = kotlin.math.sin(p * 30f) * amount * 0.4f
                            }
                            effect.contains("zoom") || effect.contains("focus-pull") ->
                                scaleX = 1f + eased * 0.18f
                        }

                        alpha = when {
                            effect.contains("shake") || effect.contains("jitter") ||
                                    effect.contains("vibrate") -> 1f - 0.35f * eased
                            effect.contains("cube") || effect.contains("flip") ||
                                    effect.contains("tilt") || effect.contains("hinge") ||
                                    effect.contains("orbit") -> 1f - 0.22f * eased
                            else -> 1f - eased
                        }
                    }
                    .then(
                        if (isStyled && (
                            mode.contains("blur") || mode.contains("focus") ||
                                mode == "defocus"
                            )
                        ) Modifier.blur((18f * kotlin.math.sin(p * Math.PI).toFloat()).dp)
                        else Modifier
                    )
                    .drawWithContent {
                        val width = size.width
                        val height = size.height
                        when {
                            effect.contains("circleopen") || effect.contains("circlecrop") ||
                                    effect == "radial" -> {
                                val radius = kotlin.math.sqrt(width * width + height * height) *
                                        0.5f * eased
                                val mask = Path().apply {
                                    fillType = PathFillType.EvenOdd
                                    addRect(androidx.compose.ui.geometry.Rect(0f, 0f, width, height))
                                    addOval(
                                        androidx.compose.ui.geometry.Rect(
                                            width / 2f - radius,
                                            height / 2f - radius,
                                            width / 2f + radius,
                                            height / 2f + radius
                                        )
                                    )
                                }
                                clipPath(mask) { this@drawWithContent.drawContent() }
                            }
                            effect.contains("circleclose") -> {
                                val radius = kotlin.math.sqrt(width * width + height * height) *
                                        0.5f * (1f - eased)
                                val mask = Path().apply {
                                    addOval(
                                        androidx.compose.ui.geometry.Rect(
                                            width / 2f - radius,
                                            height / 2f - radius,
                                            width / 2f + radius,
                                            height / 2f + radius
                                        )
                                    )
                                }
                                clipPath(mask) { this@drawWithContent.drawContent() }
                            }
                            effect.contains("wipeleft") || effect.contains("coverleft") ||
                                    effect.contains("revealleft") ->
                                clipRect(left = width * eased) { this@drawWithContent.drawContent() }
                            effect.contains("wiperight") || effect.contains("coverright") ||
                                    effect.contains("revealright") ->
                                clipRect(right = width * (1f - eased)) { this@drawWithContent.drawContent() }
                            effect.contains("wipeup") || effect.contains("coverup") ||
                                    effect.contains("revealup") ->
                                clipRect(top = height * eased) { this@drawWithContent.drawContent() }
                            effect.contains("wipedown") || effect.contains("coverdown") ||
                                    effect.contains("revealdown") ->
                                clipRect(bottom = height * (1f - eased)) { this@drawWithContent.drawContent() }
                            effect.contains("slideleft") || effect.contains("smoothleft") ->
                                clipRect(left = width * eased) { this@drawWithContent.drawContent() }
                            effect.contains("slideright") || effect.contains("smoothright") ->
                                clipRect(right = width * (1f - eased)) { this@drawWithContent.drawContent() }
                            effect.contains("slideup") || effect.contains("smoothup") ->
                                clipRect(top = height * eased) { this@drawWithContent.drawContent() }
                            effect.contains("slidedown") || effect.contains("smoothdown") ->
                                clipRect(bottom = height * (1f - eased)) { this@drawWithContent.drawContent() }
                            else -> drawContent()
                        }
                    }
            )
        }

        val isLightLeak = mode.startsWith("leak-") || mode.startsWith("flare-") ||
                mode.contains("film-burn") || mode.contains("light-flash") ||
                mode.contains("prism-flare") || mode.contains("sun-flare") ||
                mode.contains("golden-hour") || mode.contains("neon-leak") ||
                mode.contains("soft-leak")
        if (isStyled && isLightLeak) {
            val leakStyle = TransitionLibrary.lightLeakStyle(transitionKey)
            val leakColor = leakStyle?.colorHex
                ?.let { Color(android.graphics.Color.parseColor("#$it")) }
                ?: Color(0xFFFFE76A)
            Canvas(Modifier.fillMaxSize()) {
                val x = if (leakStyle?.movesRight == false) 1f - p else p
                val center = androidx.compose.ui.geometry.Offset(
                    size.width * x,
                    size.height * (0.25f + p * 0.5f)
                )
                val alpha = kotlin.math.sin(p * Math.PI).toFloat() * 0.68f
                drawRect(
                    brush = androidx.compose.ui.graphics.Brush.linearGradient(
                        listOf(Color.Transparent, leakColor.copy(alpha = alpha), Color.Transparent),
                        start = androidx.compose.ui.geometry.Offset(size.width * (x - 0.5f), 0f),
                        end = androidx.compose.ui.geometry.Offset(size.width * (x + 0.5f), size.height)
                    )
                )
                drawCircle(
                    brush = androidx.compose.ui.graphics.Brush.radialGradient(
                        listOf(leakColor.copy(alpha = alpha), Color.Transparent),
                        center = center,
                        radius = maxOf(size.width, size.height) * 0.72f
                    ),
                    radius = maxOf(size.width, size.height) * 0.72f,
                    center = center
                )
            }
        }
    }
}

@Composable
private fun TransitionPreviewIcon(
    transitionKey: String,
    transition: String,
    progress: Float,
    referenceBitmap: Bitmap?,
    modifier: Modifier = Modifier
) {
    Box(
        modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF151515))
    ) {
        if (referenceBitmap != null && !referenceBitmap.isRecycled) {
            Image(
                bitmap = referenceBitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val eased = progress * progress * (3f - 2f * progress)
                        when {
                            transitionKey.contains("cube-left") ||
                                    transitionKey.contains("flip-y") ||
                                    transitionKey.contains("card-flip-left") ->
                                rotationY = -65f * eased
                            transitionKey.contains("cube-right") ||
                                    transitionKey.contains("card-flip-right") ->
                                rotationY = 65f * eased
                            transitionKey.contains("flip-x") ||
                                    transitionKey.contains("card-flip-up") ->
                                rotationX = 65f * eased
                            transitionKey.contains("card-flip-down") ->
                                rotationX = -65f * eased
                            transitionKey.contains("shake") ||
                                    transitionKey.contains("jitter") ||
                                    transitionKey.contains("vibrate") ->
                                translationX = kotlin.math.sin(progress * 42f) *
                                        if (transitionKey.contains("heavy")) 5f else 2f
                            transitionKey.contains("whip") || transitionKey.contains("snap") ->
                                translationX = -size.width * eased
                        }
                        cameraDistance = 32f
                    }
                    .then(
                        if (transitionKey.startsWith("style-blur") ||
                            transitionKey.startsWith("style-soft-blur") ||
                            transitionKey.startsWith("style-strong-blur") ||
                            transitionKey.startsWith("style-radial-blur") ||
                            transitionKey.startsWith("style-zoom-blur") ||
                            transitionKey.startsWith("style-motion-blur") ||
                            transitionKey.startsWith("style-dream-blur") ||
                            transitionKey.startsWith("style-focus-pull") ||
                            transitionKey.startsWith("style-spin-blur") ||
                            transitionKey.startsWith("style-bloom-blur") ||
                            transitionKey.startsWith("style-soft-focus") ||
                            transitionKey.startsWith("style-defocus")
                        ) Modifier.blur((18f * kotlin.math.sin(progress * Math.PI).toFloat()).dp)
                        else Modifier
                    )
            )
        }

        Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val p = progress.coerceIn(0f, 1f)

        val oldColor = Color(0xFF32164A).copy(alpha = 0.72f)
        when {
            transitionKey.startsWith("style-leak-") ||
                    transitionKey.startsWith("style-flare-") ||
                    transitionKey.startsWith("style-film-burn") ||
                    transitionKey.startsWith("style-light-flash") ||
                    transitionKey.startsWith("style-prism-flare") ||
                    transitionKey.startsWith("style-sun-flare") ||
                    transitionKey.startsWith("style-golden-hour") ||
                    transitionKey.startsWith("style-neon-leak") ||
                    transitionKey.startsWith("style-soft-leak") -> {
                val color = when {
                    transitionKey.contains("blue") || transitionKey.contains("teal") -> Color.Cyan
                    transitionKey.contains("pink") || transitionKey.contains("neon") -> Color.Magenta
                    transitionKey.contains("violet") || transitionKey.contains("prism") -> Color(0xFFB36BFF)
                    transitionKey.contains("orange") || transitionKey.contains("amber") ||
                            transitionKey.contains("golden") || transitionKey.contains("sun") ||
                            transitionKey.contains("burn") -> Color(0xFFFFA23A)
                    else -> Color(0xFFFFE76A)
                }
                val alpha = kotlin.math.sin(p * Math.PI).toFloat().coerceIn(0f, 1f) * 0.7f
                val x = when {
                    transitionKey.contains("left") -> p
                    transitionKey.contains("right") -> 1f - p
                    else -> 0.2f + p * 0.6f
                }
                drawRect(
                    brush = androidx.compose.ui.graphics.Brush.linearGradient(
                        listOf(Color.Transparent, color.copy(alpha = alpha), Color.Transparent),
                        start = androidx.compose.ui.geometry.Offset(w * (x - 0.5f), 0f),
                        end = androidx.compose.ui.geometry.Offset(w * (x + 0.5f), h)
                    )
                )
                drawCircle(
                    brush = androidx.compose.ui.graphics.Brush.radialGradient(
                        listOf(color.copy(alpha = alpha), Color.Transparent),
                        center = androidx.compose.ui.geometry.Offset(w * x, h * (0.25f + p * 0.5f)),
                        radius = maxOf(w, h) * 0.72f
                    ),
                    radius = maxOf(w, h) * 0.72f,
                    center = androidx.compose.ui.geometry.Offset(w * x, h * (0.25f + p * 0.5f))
                )
            }
            transitionKey.startsWith("style-shake") ||
                    transitionKey.startsWith("style-camera-shake") ||
                    transitionKey.startsWith("style-impact-shake") ||
                    transitionKey.startsWith("style-micro-jitter") ||
                    transitionKey.startsWith("style-heavy-jitter") ||
                    transitionKey.startsWith("style-roll-shake") ||
                    transitionKey.startsWith("style-pulse-shake") ||
                    transitionKey.startsWith("style-bounce-shake") ||
                    transitionKey.startsWith("style-vibrate") -> Unit
            transitionKey.startsWith("style-blur") ||
                    transitionKey.startsWith("style-soft-blur") ||
                    transitionKey.startsWith("style-strong-blur") ||
                    transitionKey.startsWith("style-radial-blur") ||
                    transitionKey.startsWith("style-zoom-blur") ||
                    transitionKey.startsWith("style-motion-blur") ||
                    transitionKey.startsWith("style-dream-blur") ||
                    transitionKey.startsWith("style-focus-pull") ||
                    transitionKey.startsWith("style-spin-blur") ||
                    transitionKey.startsWith("style-bloom-blur") ||
                    transitionKey.startsWith("style-soft-focus") ||
                    transitionKey.startsWith("style-defocus") ->
                drawRect(Color(0xFFB8D7FF).copy(alpha = kotlin.math.sin(p * Math.PI).toFloat() * 0.2f))
            transitionKey.startsWith("style-") -> {
                val scaleInset = minOf(w, h) * 0.18f * p
                drawRect(
                    color = Color(0xFF1A1028).copy(alpha = p * 0.35f),
                    topLeft = androidx.compose.ui.geometry.Offset(scaleInset, scaleInset),
                    size = androidx.compose.ui.geometry.Size(w - scaleInset * 2f, h - scaleInset * 2f)
                )
            }
            transition.startsWith("fade") || transition == "dissolve" ||
                    transition == "distance" || transition == "hblur" ->
                drawRect(oldColor.copy(alpha = 1f - p))

            transition.startsWith("circle") || transition == "radial" -> {
                val radius = kotlin.math.sqrt(w * w + h * h) * 0.5f * p
                val path = Path().apply {
                    fillType = PathFillType.EvenOdd
                    addRect(androidx.compose.ui.geometry.Rect(0f, 0f, w, h))
                    addOval(
                        androidx.compose.ui.geometry.Rect(
                            w / 2f - radius,
                            h / 2f - radius,
                            w / 2f + radius,
                            h / 2f + radius
                        )
                    )
                }
                drawPath(path, oldColor)
            }

            transition.startsWith("vert") || transition.startsWith("horz") -> {
                val opening = if (transition.startsWith("vert")) w * p else h * p
                if (transition.endsWith("open")) {
                    if (transition.startsWith("vert")) {
                        drawRect(oldColor, size = androidx.compose.ui.geometry.Size((w - opening) / 2f, h))
                        drawRect(
                            oldColor,
                            topLeft = androidx.compose.ui.geometry.Offset((w + opening) / 2f, 0f),
                            size = androidx.compose.ui.geometry.Size((w - opening) / 2f, h)
                        )
                    } else {
                        drawRect(oldColor, size = androidx.compose.ui.geometry.Size(w, (h - opening) / 2f))
                        drawRect(
                            oldColor,
                            topLeft = androidx.compose.ui.geometry.Offset(0f, (h + opening) / 2f),
                            size = androidx.compose.ui.geometry.Size(w, (h - opening) / 2f)
                        )
                    }
                } else {
                    drawRect(oldColor.copy(alpha = 1f - p))
                }
            }

            transition.startsWith("diag") ||
                    transition in setOf("wipetl", "wipetr", "wipebl", "wipebr") -> {
                val path = Path().apply {
                    moveTo(0f, 0f)
                    lineTo(w * (1f - p), 0f)
                    lineTo(0f, h * (1f - p))
                    close()
                }
                drawPath(path, oldColor)
            }

            transition.startsWith("wipe") || transition.startsWith("slide") ||
                    transition.startsWith("smooth") || transition.startsWith("cover") ||
                    transition.startsWith("reveal") -> {
                when {
                    transition.endsWith("left") -> clipRect(right = w * (1f - p)) {
                        drawRect(oldColor)
                    }
                    transition.endsWith("right") -> clipRect(left = w * p) {
                        drawRect(oldColor)
                    }
                    transition.endsWith("up") -> clipRect(bottom = h * (1f - p)) {
                        drawRect(oldColor)
                    }
                    transition.endsWith("down") -> clipRect(top = h * p) {
                        drawRect(oldColor)
                    }
                    else -> drawRect(oldColor.copy(alpha = 1f - p))
                }
            }

            else -> {
                val inset = (minOf(w, h) * 0.45f * p)
                drawRect(oldColor, topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                    size = androidx.compose.ui.geometry.Size(w - inset * 2f, h - inset * 2f))
            }
        }

        drawRect(
            color = Color.White.copy(alpha = 0.5f),
            size = size,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx())
        )
        }
    }
}

private fun loadTransitionReferenceImage(context: android.content.Context): Bitmap? {
    return try {
        val options = BitmapFactory.Options().apply { inScaled = false }
        val source = BitmapFactory.decodeResource(
            context.resources,
            R.drawable.filter_ref_image,
            options
        ) ?: return null
        val cropSize = minOf(source.width, source.height)
        val x = (source.width - cropSize) / 2
        val y = (source.height - cropSize) / 2
        val square = Bitmap.createBitmap(source, x, y, cropSize, cropSize)
        val scaled = Bitmap.createScaledBitmap(square, 120, 120, true)
        if (square !== scaled && square !== source) square.recycle()
        if (source !== scaled && source !== square) source.recycle()
        scaled
    } catch (error: Exception) {
        Log.e("TRANSITION_PREVIEW", "Failed to load reference image", error)
        null
    }
}

private fun loadTransitionImage(
    context: android.content.Context,
    resourceId: Int
): Bitmap? {
    return try {
        val options = BitmapFactory.Options().apply { inScaled = false }
        val source = BitmapFactory.decodeResource(context.resources, resourceId, options)
            ?: return null
        val cropSize = minOf(source.width, source.height)
        val x = (source.width - cropSize) / 2
        val y = (source.height - cropSize) / 2
        val cropped = Bitmap.createBitmap(source, x, y, cropSize, cropSize)
        val scaled = Bitmap.createScaledBitmap(cropped, 120, 120, true)
        if (cropped !== scaled && cropped !== source) cropped.recycle()
        if (source !== scaled && source !== cropped) source.recycle()
        scaled
    } catch (error: Exception) {
        Log.e("TRANSITION_PREVIEW", "Failed to load transition image $resourceId", error)
        null
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