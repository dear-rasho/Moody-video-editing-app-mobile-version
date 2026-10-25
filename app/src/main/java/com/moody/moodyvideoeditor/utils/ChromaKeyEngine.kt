package com.moody.moodyvideoeditor.utils

import android.graphics.ColorMatrix
import android.os.Build
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix as ComposeColorMatrix
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import com.moody.moodyvideoeditor.data.ChromaKeyState
import java.util.Locale

object ChromaKeyEngine {

    // ═══════════════════════════════════════════════════════════
    //  RuntimeShader — API 33+ (Live Preview)
    // ═══════════════════════════════════════════════════════════

    private val CHROMA_SHADER: String = """
        uniform shader input;
        uniform float3 uTarget;
        uniform float uSimilarity;
        uniform float uSmoothness;
        uniform float uSpill;
        uniform float uOpacity;

        float3 rgb2yuv(float3 c) {
            float y = dot(c, float3(0.299, 0.587, 0.114));
            float u = dot(c, float3(-0.169, -0.331, 0.500)) + 0.5;
            float v = dot(c, float3(0.500, -0.419, -0.081)) + 0.5;
            return float3(y, u, v);
        }

        half4 main(float2 fragCoord) {
            half4 pixel = input.eval(fragCoord);
            if (pixel.a < 0.01) return pixel;

            float3 rgb = float3(pixel.rgb);
            float3 targetYuv = rgb2yuv(uTarget);
            float3 pixelYuv = rgb2yuv(rgb);

            // YUV space mein difference nikal rahe hain (Brightness ignore hoti hai)
            float2 chromaDiff = float2(
                pixelYuv.y - targetYuv.y,
                pixelYuv.z - targetYuv.z
            );
            float dist = length(chromaDiff);

            float sim = clamp(uSimilarity, 0.0, 1.0);
            float smooth = clamp(uSmoothness, 0.001, 1.0);
            
            float low = sim * (1.0 - smooth * 0.5);
            float high = sim * (1.0 + smooth * 0.5);

            float alpha;
            if (dist < low) {
                alpha = 0.0;
            } else if (dist > high) {
                alpha = 1.0;
            } else {
                alpha = (dist - low) / max(high - low, 0.0001);
            }

            // Spill suppression (Green cast remove karta hai edges se)
            if (uSpill > 0.01 && alpha < 0.9) {
                float spillAmt = (1.0 - alpha) * uSpill;
                float luma = dot(rgb, float3(0.299, 0.587, 0.114));
                float3 desaturated = float3(luma, luma, luma);
                rgb = mix(rgb, desaturated, spillAmt);
            }

            float finalAlpha = pixel.a * alpha * uOpacity;
            return half4(rgb * (alpha * uOpacity), finalAlpha);
        }
    """.trimIndent()

    fun createRenderEffect(state: ChromaKeyState): android.graphics.RenderEffect? {
        if (!state.isActive) return null
        if (Build.VERSION.SDK_INT < 33) return null

        return try {
            val shader = android.graphics.RuntimeShader(CHROMA_SHADER)

            // Target color to normalized RGB
            val r = ((state.targetColor shr 16) and 0xFF) / 255f
            val g = ((state.targetColor shr 8) and 0xFF) / 255f
            val b = (state.targetColor and 0xFF) / 255f
            shader.setFloatUniform("uTarget", r, g, b)

            // UI values (0-100) ko 0.0-1.0 mein map kar rahe hain
            shader.setFloatUniform(
                "uSimilarity",
                (state.similarity / 100f * 0.5f).coerceIn(0.01f, 0.5f)
            )
            shader.setFloatUniform("uSmoothness", (state.smoothness / 100f).coerceIn(0.001f, 1f))
            shader.setFloatUniform("uSpill", (state.spillSuppression / 100f).coerceIn(0f, 1f))
            shader.setFloatUniform("uOpacity", (state.opacity / 100f).coerceIn(0f, 1f))

            android.graphics.RenderEffect.createRuntimeShaderEffect(shader, "input")
        } catch (t: Throwable) {
            android.util.Log.e("ChromaKey", "RenderEffect failed", t)
            null
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  FALLBACK (API < 33) — Approximate Desaturation
    // ═══════════════════════════════════════════════════════════

    fun buildFallbackColorMatrix(state: ChromaKeyState): ColorMatrix? {
        if (!state.isActive) return null

        val r = (state.targetColor shr 16) and 0xFF
        val g = (state.targetColor shr 8) and 0xFF
        val b = state.targetColor and 0xFF

        val dominant = when {
            g >= r && g >= b -> "g"
            b >= r && b >= g -> "b"
            else -> "r"
        }

        val strength = (state.similarity / 100f).coerceIn(0f, 1f)
        val matrix = ColorMatrix()
        when (dominant) {
            "g" -> matrix.setSaturation(1f - strength * 0.7f)
            "b" -> matrix.setSaturation(1f - strength * 0.5f)
            else -> matrix.setSaturation(1f - strength * 0.5f)
        }
        return matrix
    }

    // ═══════════════════════════════════════════════════════════
    //  FFmpeg filter (Export)
    //  NOTE: Video gaib hone ka issue yahan fix hua hai
    // ═══════════════════════════════════════════════════════════

    fun buildFfmpegFilter(state: ChromaKeyState): String {
        if (!state.isActive) return ""

        // FFmpeg ko 0xRRGGBB format chahiye hota hai
        val hex = String.format(
            Locale.US, "0x%06X",
            state.targetColor and 0xFFFFFF
        )

        // FFmpeg Chromakey Similarity (0.01 to 0.5) aur Blend (0.0 to 0.3)
        val sim = (state.similarity / 100f * 0.5f).coerceIn(0.01f, 0.5f)
        val blend = (state.smoothness / 100f * 0.3f).coerceIn(0f, 0.3f)

        // ZAROORI: "format=rgba" lagana zaroori hai warna FFmpeg alpha channel
        // ko render nahi karta aur background black ya transparent ho jata hai.
        return "format=rgba,chromakey=color=$hex:similarity=${fmt(sim)}:blend=${fmt(blend)}"
    }

    private fun fmt(v: Float): String =
        String.format(Locale.US, "%.4f", v)
}

// ═══════════════════════════════════════════════════════════════
//  TOP-LEVEL EXTENSION
// ═══════════════════════════════════════════════════════════════

fun Modifier.applyChromaKey(state: ChromaKeyState): Modifier {
    if (!state.isActive) return this

    val effect = ChromaKeyEngine.createRenderEffect(state)
    if (effect != null) {
        return this.graphicsLayer {
            renderEffect = effect.asComposeRenderEffect()
        }
    }

    // Fallback for API < 33
    val cm = ChromaKeyEngine.buildFallbackColorMatrix(state) ?: return this
    val composeMatrix = ComposeColorMatrix(cm.array)
    val colorFilter = ColorFilter.colorMatrix(composeMatrix)
    return this.graphicsLayer {
        this.colorFilter = colorFilter
    }
}