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
fun TextAnimationsPanel(
    current: String,
    onSelected: (String) -> Unit,
    onClose: () -> Unit
) {
    val animations = listOf(
        "none", "typewriter", "decoder", "fadeIn", "fadeUp", "fadeDown",
        "slideLeft", "slideRight", "slideUp", "slideDown", "popIn", "bounceIn",
        "flicker", "cinematicBlur", "wordReveal", "characterRise", "glitch",
        "rgbSplit", "vcrDistort", "matrixRain", "wave", "bounceWave",
        "liquidMelt", "flagWave", "waterRipple", "elasticWave", "turbulent",
        "overshootPop", "elasticDrop", "jellyBounce", "stompBounce", "float",
        "diagonalJump", "gravityFall", "doubleBounce", "bouncySpin", "snapBack",
        "flyDiagonalTL", "flyDiagonalBR", "crossSlide", "accelSlide", "zigzagSlide",
        "smoothGlide", "flip3DX", "flip3DY", "rotate3D", "vortexSpin",
        "spiralIn", "tornado", "skewSpin", "pendulum", "propeller", "barrelRoll",
        "zoomIn", "zoomOut", "cinematicZoom", "hyperZoomOut", "pulseScale",
        "elasticZoom", "shrinkReveal", "popScale", "depthZoom", "snapZoom",
        "scribble", "neonGlow", "ghostTrail", "silhouette", "explosion", "implosion"
    )

    FeaturePanel(title = "🎞️ Text Animations (${animations.size})", onClose = onClose) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            animations.forEach { anim ->
                val isActive = current == anim
                Box(
                    modifier = Modifier
                        .height(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isActive) Color(0xFF7C3AED) else Color(0xFF181818))
                        .pointerInput(anim) { detectTapGestures { onSelected(anim) } }
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(anim, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}