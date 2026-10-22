package com.moody.moodyvideoeditor.utils

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.util.Log
import androidx.annotation.RequiresApi
import com.moody.moodyvideoeditor.data.ChromaState

/**
 * Chroma Key Preview using RuntimeShader (API 33+).
 *
 * Algorithm (closer to FFmpeg chromakey):
 *  1. Work primarily in YUV chroma space (U/V) — lighting-invariant
 *  2. Secondary HSV hue check for pure saturated keys
 *  3. Similarity + smoothness → soft alpha matte
 *  4. Spill suppression (desaturate residual key colour on edges)
 *  5. Intensity controls overall strength of the key
 */
object ChromaKeyPreviewFilter {

    private const val TAG = "ChromaPreview"

    @RequiresApi(33)
    fun createRenderEffect(chroma: ChromaState?): RenderEffect? {
        if (chroma == null) {
            Log.e(TAG, "❌ chroma=null → no effect")
            return null
        }
        if (!chroma.isActive) {
            Log.e(
                TAG,
                "❌ chroma NOT active: keyColor=0x${chroma.keyColor.toString(16)} " +
                        "intensity=${chroma.intensity}"
            )
            return null
        }

        // Extract RGB 0-1
        val r = ((chroma.keyColor shr 16) and 0xFF) / 255f
        val g = ((chroma.keyColor shr 8) and 0xFF) / 255f
        val b = (chroma.keyColor and 0xFF) / 255f

        // Key colour in YUV (BT.601)
        val keyU = -0.14713f * r - 0.28886f * g + 0.436f * b
        val keyV = 0.615f * r - 0.51499f * g - 0.10001f * b

        // HSV for hue helper
        val maxC = maxOf(r, g, b)
        val minC = minOf(r, g, b)
        val sat = if (maxC == 0f) 0f else (maxC - minC) / maxC
        val hue = when {
            maxC == minC -> 0f
            maxC == r -> ((g - b) / (maxC - minC)) / 6f
            maxC == g -> ((b - r) / (maxC - minC) + 2f) / 6f
            else -> ((r - g) / (maxC - minC) + 4f) / 6f
        }.let { if (it < 0f) it + 1f else it }

        Log.e(
            TAG,
            "✅ Creating chroma effect: key=0x${chroma.keyColor.toString(16)} " +
                    "U=$keyU V=$keyV H=$hue S=$sat " +
                    "sim=${chroma.similarity} smooth=${chroma.smoothness} " +
                    "spill=${chroma.spill} int=${chroma.intensity}"
        )

        return try {
            val shader = RuntimeShader(SHADER)
            shader.setFloatUniform("uKeyR", r)
            shader.setFloatUniform("uKeyG", g)
            shader.setFloatUniform("uKeyB", b)
            shader.setFloatUniform("uKeyU", keyU)
            shader.setFloatUniform("uKeyV", keyV)
            shader.setFloatUniform("uKeyH", hue)
            shader.setFloatUniform("uKeyS", sat)
            shader.setFloatUniform(
                "uSimilarity", (chroma.similarity / 100f).coerceIn(0.01f, 1f)
            )
            shader.setFloatUniform(
                "uSmoothness", (chroma.smoothness / 100f).coerceIn(0f, 0.99f)
            )
            shader.setFloatUniform("uSpill", (chroma.spill / 100f).coerceIn(0f, 1f))
            shader.setFloatUniform("uIntensity", (chroma.intensity / 100f).coerceIn(0f, 1f))
            RenderEffect.createRuntimeShaderEffect(shader, "input")
        } catch (e: Throwable) {
            Log.e(TAG, "❌ Failed to create chroma shader", e)
            null
        }
    }

    private const val SHADER = """
        uniform shader input;
        uniform float uKeyR, uKeyG, uKeyB;
        uniform float uKeyU, uKeyV;
        uniform float uKeyH, uKeyS;
        uniform float uSimilarity;
        uniform float uSmoothness;
        uniform float uSpill;
        uniform float uIntensity;

        float3 rgb2yuv(float3 c) {
            float y = 0.299 * c.r + 0.587 * c.g + 0.114 * c.b;
            float u = -0.14713 * c.r - 0.28886 * c.g + 0.436 * c.b;
            float v = 0.615 * c.r - 0.51499 * c.g - 0.10001 * c.b;
            return float3(y, u, v);
        }

        float3 rgb2hsv(float3 c) {
            float4 K = float4(0.0, -1.0/3.0, 2.0/3.0, -1.0);
            float4 p = mix(float4(c.bg, K.wz), float4(c.gb, K.xy), step(c.b, c.g));
            float4 q = mix(float4(p.xyw, c.r), float4(c.r, p.yzx), step(p.x, c.r));
            float d = q.x - min(q.w, q.y);
            float e = 1.0e-10;
            return float3(abs(q.z + (q.w - q.y) / (6.0 * d + e)), d / (q.x + e), q.x);
        }

        half4 main(float2 fragCoord) {
            half4 pixel = input.eval(fragCoord);
            if (pixel.a < 0.01) return pixel;

            // Un-premultiply (Compose layers are premultiplied)
            float3 rgb = float3(pixel.rgb) / max(float(pixel.a), 0.001);

            // Primary: YUV chroma distance (lighting-invariant)
            float3 yuv = rgb2yuv(rgb);
            float du = yuv.y - uKeyU;
            float dv = yuv.z - uKeyV;
            float chromaDist = sqrt(du * du + dv * dv);

            // Secondary: HSV hue distance
            float3 hsv = rgb2hsv(rgb);
            float hueDist = abs(hsv.x - uKeyH);
            hueDist = min(hueDist, 1.0 - hueDist);

            // Combined distance
            float dist = chromaDist * 1.6 + hueDist * 0.4;

            float threshold = mix(0.04, 0.55, uSimilarity);
            float soft = mix(0.008, 0.22, uSmoothness);

            float alpha = smoothstep(threshold - soft, threshold + soft, dist);
            alpha = mix(1.0, alpha, uIntensity);

            // Spill suppression
            if (uSpill > 0.001 && alpha < 0.98) {
                float spillAmt = (1.0 - alpha) * uSpill;
                if (uKeyG > uKeyR && uKeyG > uKeyB) {
                    float other = max(rgb.r, rgb.b);
                    if (rgb.g > other) rgb.g = mix(rgb.g, other, spillAmt);
                } else if (uKeyB > uKeyR && uKeyB > uKeyG) {
                    float other = max(rgb.r, rgb.g);
                    if (rgb.b > other) rgb.b = mix(rgb.b, other, spillAmt);
                } else {
                    float other = max(rgb.g, rgb.b);
                    if (rgb.r > other) rgb.r = mix(rgb.r, other, spillAmt);
                }
            }

            return half4(half3(rgb) * alpha, pixel.a * alpha);
        }
    """
}