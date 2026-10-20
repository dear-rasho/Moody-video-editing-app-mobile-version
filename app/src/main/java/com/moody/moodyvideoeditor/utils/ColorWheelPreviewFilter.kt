package com.moody.moodyvideoeditor.utils

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.util.Log
import androidx.annotation.RequiresApi
import com.moody.moodyvideoeditor.data.ColorWheelState

object ColorWheelPreviewFilter {

    private const val TAG = "ColorWheelPreview"

    @RequiresApi(33)
    fun createRenderEffect(states: List<ColorWheelState>): RenderEffect? {
        val activeStates = states.filter { it.hasAnyChange }
        if (activeStates.isEmpty()) return null

        return try {
            val shader = RuntimeShader(buildShaderSource(activeStates.size))
            activeStates.forEachIndexed { index, state ->
                shader.setFloatUniform("hdr$index", state.hdrWhite / 100f)

                // ✅ FIXED: ColorWheelEngine (not ColorWheelFfmpegHelper)
                val (sR, sG, sB) = ColorWheelEngine.getRGBShift(state.shadows)
                val (mR, mG, mB) = ColorWheelEngine.getRGBShift(state.midtones)
                val (hR, hG, hB) = ColorWheelEngine.getRGBShift(state.highlights)

                shader.setFloatUniform("shR$index", sR)
                shader.setFloatUniform("shG$index", sG)
                shader.setFloatUniform("shB$index", sB)

                shader.setFloatUniform("mdR$index", mR)
                shader.setFloatUniform("mdG$index", mG)
                shader.setFloatUniform("mdB$index", mB)

                shader.setFloatUniform("hlR$index", hR)
                shader.setFloatUniform("hlG$index", hG)
                shader.setFloatUniform("hlB$index", hB)
            }
            RenderEffect.createRuntimeShaderEffect(shader, "input")
        } catch (error: Throwable) {
            Log.e(TAG, "Unable to create the color-wheel preview shader", error)
            null
        }
    }

    private fun buildShaderSource(stateCount: Int): String = buildString {
        appendLine("uniform shader input;")
        repeat(stateCount) { index ->
            appendLine("uniform float hdr$index;")
            appendLine("uniform float shR$index;")
            appendLine("uniform float shG$index;")
            appendLine("uniform float shB$index;")
            appendLine("uniform float mdR$index;")
            appendLine("uniform float mdG$index;")
            appendLine("uniform float mdB$index;")
            appendLine("uniform float hlR$index;")
            appendLine("uniform float hlG$index;")
            appendLine("uniform float hlB$index;")
        }
        appendLine(
            """
            float shadowWeight(float lum) { return max(0.0, 1.0 - lum * 2.0); }
            float midtoneWeight(float lum) { return max(0.0, 1.0 - abs(lum - 0.5) * 2.0); }
            float highlightWeight(float lum) { return max(0.0, lum * 2.0 - 1.0); }

            float3 applyWheel(float3 color, float sR, float sG, float sB, float mR, float mG, float mB, float hR, float hG, float hB, float hdr) {
                if (hdr != 1.0) {
                    float lum = dot(color, float3(0.299, 0.587, 0.114)) / 255.0;
                    float weight = clamp((lum - 0.55) / 0.45, 0.0, 1.0);
                    color = clamp(color + (hdr - 1.0) * 127.0 * weight, 0.0, 255.0);
                }
                
                float lum = dot(color, float3(0.299, 0.587, 0.114)) / 255.0;
                float sW = shadowWeight(lum);
                float mW = midtoneWeight(lum);
                float hW = highlightWeight(lum);
                
                color.r = clamp(color.r + (sR * sW + mR * mW + hR * hW) * 255.0, 0.0, 255.0);
                color.g = clamp(color.g + (sG * sW + mG * mW + hG * hW) * 255.0, 0.0, 255.0);
                color.b = clamp(color.b + (sB * sW + mB * mW + hB * hW) * 255.0, 0.0, 255.0);
                
                return color;
            }
            """
        )
        appendLine("half4 main(float2 coord) {")
        appendLine("    half4 pixel = input.eval(coord);")
        appendLine("    float alpha = float(pixel.a);")
        appendLine("    float3 color = alpha > 0.0 ? float3(pixel.rgb) / alpha * 255.0 : float3(0.0);")
        repeat(stateCount) { index ->
            appendLine("    color = applyWheel(color, shR$index, shG$index, shB$index, mdR$index, mdG$index, mdB$index, hlR$index, hlG$index, hlB$index, hdr$index);")
        }
        appendLine("    return half4(clamp(color / 255.0, 0.0, 1.0) * alpha, pixel.a);")
        appendLine("}")
    }
}