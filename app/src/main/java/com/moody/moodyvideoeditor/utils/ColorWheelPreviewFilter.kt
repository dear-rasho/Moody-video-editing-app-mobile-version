package com.moody.moodyvideoeditor.utils

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.util.Log
import androidx.annotation.RequiresApi
import com.moody.moodyvideoeditor.data.ColorWheelState
import com.moody.moodyvideoeditor.data.ToneValue

object ColorWheelPreviewFilter {
    @RequiresApi(33)
    fun createRenderEffect(states: List<ColorWheelState>): RenderEffect? {
        val activeStates = states.filter { it.hasAnyChange }
        if (activeStates.isEmpty()) return null

        return try {
            val shader = RuntimeShader(buildShaderSource(activeStates.size))
            activeStates.forEachIndexed { index, state ->
                shader.setFloatUniform("hdr$index", state.hdrWhite / 100f)
                setToneUniform(shader, "shadow$index", state.shadows)
                setToneUniform(shader, "midtone$index", state.midtones)
                setToneUniform(shader, "highlight$index", state.highlights)
            }
            RenderEffect.createRuntimeShaderEffect(shader, "input")
        } catch (error: RuntimeException) {
            Log.e(TAG, "Unable to create the color-wheel preview shader", error)
            null
        }
    }

    private const val TAG = "ColorWheelPreview"

    private fun setToneUniform(
        shader: RuntimeShader,
        name: String,
        tone: ToneValue
    ) {
        shader.setFloatUniform(
            name,
            tone.hue,
            tone.saturation,
            tone.intensity,
            if (tone.isActive) 1f else 0f
        )
    }

    private fun buildShaderSource(stateCount: Int): String = buildString {
        appendLine("uniform shader input;")
        repeat(stateCount) { index ->
            appendLine("uniform float hdr$index;")
            appendLine("uniform float4 shadow$index;")
            appendLine("uniform float4 midtone$index;")
            appendLine("uniform float4 highlight$index;")
        }
        appendLine(
            """
            float shadowWeight(float lum) { return max(0.0, 1.0 - lum * 2.0); }
            float midtoneWeight(float lum) { return max(0.0, 1.0 - abs(lum - 0.5) * 2.0); }
            float highlightWeight(float lum) { return max(0.0, lum * 2.0 - 1.0); }

            float3 hslToRgb(float h, float s, float l) {
                float hh = mod(mod(h, 360.0) + 360.0, 360.0);
                float ss = clamp(s, 0.0, 100.0) / 100.0;
                float ll = clamp(l, 0.0, 100.0) / 100.0;
                float a = ss * min(ll, 1.0 - ll);
                float k0 = mod(hh / 30.0, 12.0);
                float k8 = mod(8.0 + hh / 30.0, 12.0);
                float k4 = mod(4.0 + hh / 30.0, 12.0);
                float r = ll - a * max(-1.0, min(k0 - 3.0, min(9.0 - k0, 1.0)));
                float g = ll - a * max(-1.0, min(k8 - 3.0, min(9.0 - k8, 1.0)));
                float b = ll - a * max(-1.0, min(k4 - 3.0, min(9.0 - k4, 1.0)));
                return float3(r, g, b);
            }

            float3 rgbToHsl(float3 rgb) {
                float3 c = rgb / 255.0;
                float hi = max(c.r, max(c.g, c.b));
                float lo = min(c.r, min(c.g, c.b));
                float l = (hi + lo) * 0.5;
                float h = 0.0;
                float s = 0.0;
                if (hi != lo) {
                    float d = hi - lo;
                    s = l > 0.5 ? d / (2.0 - hi - lo) : d / (hi + lo);
                    if (hi == c.r) h = mod((c.g - c.b) / d + (c.g < c.b ? 6.0 : 0.0), 6.0) * 60.0;
                    else if (hi == c.g) h = ((c.b - c.r) / d + 2.0) * 60.0;
                    else h = ((c.r - c.g) / d + 4.0) * 60.0;
                }
                return float3(h, s * 100.0, l * 100.0);
            }

            float3 applyTone(float3 color, float4 shadows, float4 midtones, float4 highlights) {
                float lum = dot(color, float3(0.299, 0.587, 0.114)) / 255.0;
                float weightSum = 0.0;
                float targetHueSum = 0.0;
                float targetSatSum = 0.0;
                if (shadows.w > 0.5) {
                    float w = shadowWeight(lum) * (shadows.z / 100.0);
                    targetHueSum += shadows.x * w;
                    targetSatSum += shadows.y * w;
                    weightSum += w;
                }
                if (midtones.w > 0.5) {
                    float w = midtoneWeight(lum) * (midtones.z / 100.0);
                    targetHueSum += midtones.x * w;
                    targetSatSum += midtones.y * w;
                    weightSum += w;
                }
                if (highlights.w > 0.5) {
                    float w = highlightWeight(lum) * (highlights.z / 100.0);
                    targetHueSum += highlights.x * w;
                    targetSatSum += highlights.y * w;
                    weightSum += w;
                }
                if (weightSum > 0.001) {
                    float hue = mod(mod(targetHueSum / weightSum, 360.0) + 360.0, 360.0);
                    float saturation = min(100.0, targetSatSum / weightSum);
                    float strength = min(1.0, weightSum);
                    float3 hsl = rgbToHsl(color);
                    float hueDiff = mod(hue - hsl.x + 540.0, 360.0) - 180.0;
                    float newHue = hsl.x + hueDiff * strength * 0.85;
                    float newSat = min(100.0, hsl.y * (1.0 + saturation / 100.0 * strength * 0.9));
                    return clamp(hslToRgb(newHue, newSat, hsl.z) * 255.0, 0.0, 255.0);
                }
                return color;
            }

            float3 applyWheel(float3 color, float hdr) {
                if (hdr != 1.0) {
                    float lum = dot(color, float3(0.299, 0.587, 0.114)) / 255.0;
                    float weight = clamp((lum - 0.55) / 0.45, 0.0, 1.0);
                    color = clamp(color + (hdr - 1.0) * 127.0 * weight, 0.0, 255.0);
                }
                return color;
            }
            """
        )
        appendLine("half4 main(float2 coord) {")
        appendLine("    half4 pixel = input.eval(coord);")
        appendLine("    float alpha = float(pixel.a);")
        appendLine("    float3 color = alpha > 0.0 ? float3(pixel.rgb) / alpha * 255.0 : float3(0.0);")
        repeat(stateCount) { index ->
            appendLine("    color = applyWheel(color, hdr$index);")
            appendLine(
                "    color = applyTone(color, shadow$index, midtone$index, highlight$index);"
            )
        }
        appendLine("    return half4(clamp(color / 255.0, 0.0, 1.0) * alpha, pixel.a);")
        appendLine("}")
    }
}
