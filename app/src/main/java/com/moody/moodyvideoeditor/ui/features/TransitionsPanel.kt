package com.moody.moodyvideoeditor.ui.features

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moody.moodyvideoeditor.ui.components.FeaturePanel

@Composable
fun TransitionsPanel(current: String, onSelected: (String) -> Unit, onClose: () -> Unit) {
    val transitions = listOf(
        "none",
        "fade",
        "dissolve",
        "fadeBlack",
        "fadeWhite",
        "blur",
        "pushLeft",
        "pushRight",
        "pushUp",
        "pushDown",
        "slideLeft",
        "slideRight",
        "slideUp",
        "slideDown",
        "wipeLeft",
        "wipeRight",
        "wipeDiagonalTL",
        "wipeDiagonalBR",
        "circleIn",
        "irisBox",
        "irisCross",
        "checkerboardWipe",
        "venetianBlinds",
        "clockWipe",
        "zoomIn",
        "zoomOut",
        "smoothZoomIn",
        "smoothZoomOut",
        "crossZoom",
        "zoomBlur",
        "spinCW",
        "spinCCW",
        "radialBlurSpin",
        "swirlDistort",
        "cubeFlipLeft",
        "cubeFlipRight",
        "pageFlip",
        "doorSwing",
        "cardFlip",
        "flyBy",
        "rgbSplit",
        "vcrStatic",
        "dataMosh",
        "glitchDissolve",
        "sliceDistort",
        "signalLoss",
        "lensFlareFlash",
        "lightLeakOrange",
        "neonGlowBurn",
        "filmBurn",
        "exposureFlash",
        "waterRipple",
        "acidMelt",
        "turbulentSwirl",
        "liquidFluidWipe",
        "glassShatter",
        "heartExpand",
        "starWipe",
        "diamondMask",
        "hexagonTiles",
        "triangleFan",
        "paintBrush",
        "inkSplash"
    )
    FeaturePanel(title = "⇄ Transitions (${transitions.size - 1})", onClose = onClose) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            transitions.forEach { t ->
                val isActive = current == t
                Box(
                    modifier = Modifier
                        .height(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isActive) Color(0xFF7C3AED) else Color(0xFF181818))
                        .pointerInput(t) { detectTapGestures { onSelected(t) } }
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(t, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}