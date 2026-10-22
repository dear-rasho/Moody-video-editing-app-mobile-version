package com.moody.moodyvideoeditor.utils

import android.content.Context
import android.opengl.GLES20
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram
import com.moody.moodyvideoeditor.data.ChromaState

@UnstableApi
data class ChromaKeyGlEffect(val state: ChromaState) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram =
        ChromaKeyGlShaderProgram(context, state, useHdr)
}

@UnstableApi
private class ChromaKeyGlShaderProgram(
    context: Context,
    state: ChromaState,
    useHdr: Boolean
) : BaseGlShaderProgram(useHdr, 1) {
    private val program = GlProgram(VERTEX_SHADER, FRAGMENT_SHADER)

    init {
        program.setBufferAttribute(
            "aFramePosition",
            GlUtil.getNormalizedCoordinateBounds(),
            GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE
        )
        val identity = GlUtil.create4x4IdentityMatrix()
        program.setFloatsUniform("uTransformationMatrix", identity)
        program.setFloatsUniform("uTexTransformationMatrix", identity)

        val red = srgbToLinear(((state.keyColor shr 16) and 0xFF).toFloat() / 255f)
        val green = srgbToLinear(((state.keyColor shr 8) and 0xFF).toFloat() / 255f)
        val blue = srgbToLinear((state.keyColor and 0xFF).toFloat() / 255f)
        val keyU = -0.14713f * red - 0.28886f * green + 0.436f * blue
        val keyV = 0.615f * red - 0.51499f * green - 0.10001f * blue
        val maxComponent = maxOf(red, green, blue)
        val minComponent = minOf(red, green, blue)
        val keyHue = when {
            maxComponent == minComponent -> 0f
            maxComponent == red -> ((green - blue) / (maxComponent - minComponent)) / 6f
            maxComponent == green -> ((blue - red) / (maxComponent - minComponent) + 2f) / 6f
            else -> ((red - green) / (maxComponent - minComponent) + 4f) / 6f
        }.let { if (it < 0f) it + 1f else it }

        program.setFloatUniform("uKeyU", keyU)
        program.setFloatUniform("uKeyV", keyV)
        program.setFloatUniform("uKeyH", keyHue)
        program.setFloatUniform("uSimilarity", state.similarity.coerceIn(0f, 100f) / 100f)
        program.setFloatUniform("uSmoothness", state.smoothness.coerceIn(0f, 100f) / 100f)
        program.setFloatUniform("uSpill", state.spill.coerceIn(0f, 100f) / 100f)
        program.setFloatUniform("uIntensity", state.intensity.coerceIn(0f, 100f) / 100f)
    }

    private fun srgbToLinear(value: Float): Float =
        if (value <= 0.04045f) value / 12.92f
        else Math.pow(((value + 0.055f) / 1.055f).toDouble(), 2.4).toFloat()

    override fun configure(inputWidth: Int, inputHeight: Int) = Size(inputWidth, inputHeight)

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        try {
            program.use()
            program.setSamplerTexIdUniform("uTexSampler", inputTexId, 0)
            program.bindAttributesAndUniforms()
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        } catch (error: GlUtil.GlException) {
            throw VideoFrameProcessingException(error, presentationTimeUs)
        }
    }

    override fun release() {
        super.release()
        program.delete()
    }

    private companion object {
        const val VERTEX_SHADER = """
            #version 100
            attribute vec4 aFramePosition;
            uniform mat4 uTransformationMatrix;
            uniform mat4 uTexTransformationMatrix;
            varying vec2 vTexSamplingCoord;
            void main() {
                gl_Position = uTransformationMatrix * aFramePosition;
                vec4 texturePosition = vec4(
                    aFramePosition.x * 0.5 + 0.5,
                    aFramePosition.y * 0.5 + 0.5,
                    0.0,
                    1.0
                );
                vTexSamplingCoord = (uTexTransformationMatrix * texturePosition).xy;
            }
        """

        const val FRAGMENT_SHADER = """
            #version 100
            precision highp float;
            uniform sampler2D uTexSampler;
            uniform float uKeyU;
            uniform float uKeyV;
            uniform float uKeyH;
            uniform float uSimilarity;
            uniform float uSmoothness;
            uniform float uSpill;
            uniform float uIntensity;
            varying vec2 vTexSamplingCoord;

            vec3 rgbToYuv(vec3 c) {
                float y = 0.299 * c.r + 0.587 * c.g + 0.114 * c.b;
                float u = -0.14713 * c.r - 0.28886 * c.g + 0.436 * c.b;
                float v = 0.615 * c.r - 0.51499 * c.g - 0.10001 * c.b;
                return vec3(y, u, v);
            }

            vec3 rgbToHsv(vec3 c) {
                vec4 k = vec4(0.0, -1.0 / 3.0, 2.0 / 3.0, -1.0);
                vec4 p = mix(vec4(c.bg, k.wz), vec4(c.gb, k.xy), step(c.b, c.g));
                vec4 q = mix(vec4(p.xyw, c.r), vec4(c.r, p.yzx), step(p.x, c.r));
                float d = q.x - min(q.w, q.y);
                float e = 1.0e-10;
                return vec3(abs(q.z + (q.w - q.y) / (6.0 * d + e)), d / (q.x + e), q.x);
            }

            void main() {
                vec4 pixel = texture2D(uTexSampler, vTexSamplingCoord);
                if (pixel.a <= 0.001) {
                    gl_FragColor = pixel;
                    return;
                }

                vec3 rgb = pixel.rgb / max(pixel.a, 0.001);
                vec3 yuv = rgbToYuv(rgb);
                float du = yuv.y - uKeyU;
                float dv = yuv.z - uKeyV;
                float chromaDistance = sqrt(du * du + dv * dv);

                vec3 hsv = rgbToHsv(rgb);
                float hueDistance = abs(hsv.x - uKeyH);
                hueDistance = min(hueDistance, 1.0 - hueDistance);
                float distance = chromaDistance * 1.6 + hueDistance * 0.4;

                float threshold = mix(0.01, 0.28, uSimilarity);
                float softness = mix(0.002, 0.08, uSmoothness);
                float keyAlpha = smoothstep(threshold - softness, threshold + softness, distance);
                float alpha = mix(1.0, keyAlpha, uIntensity);

                if (uSpill > 0.001 && alpha < 0.98) {
                    float spillAmount = (1.0 - alpha) * uSpill;
                    float other = max(rgb.r, rgb.b);
                    rgb.g = mix(rgb.g, min(rgb.g, other), spillAmount);
                }

                gl_FragColor = vec4(rgb * alpha, pixel.a * alpha);
            }
        """
    }
}