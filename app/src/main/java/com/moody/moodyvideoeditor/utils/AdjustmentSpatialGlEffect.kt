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
import com.moody.moodyvideoeditor.data.AdjustmentData
import java.util.Locale
import kotlin.math.PI

@UnstableApi
class AdjustmentSpatialGlEffect(private val adjustmentData: AdjustmentData) : GlEffect {

    private val noiseStrength = ffmpegNoiseStrength(adjustmentData.noise)
    private val noiseAmplitude =
        noiseStrength.toFloat() / (2f * CHANNEL_MAX_VALUE)
    private val sharpenAmount = ffmpegSharpenAmount(adjustmentData.sharpen)
    private val vignetteAngle = ffmpegVignetteAngle(adjustmentData.vignette)

    override fun isNoOp(inputWidth: Int, inputHeight: Int): Boolean =
        noiseStrength == 0 &&
                sharpenAmount == 0f &&
                vignetteAngle == 0f

    @Suppress("UNUSED_PARAMETER")
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram =
        SpatialShaderProgram(
            noiseAmplitude = noiseAmplitude,
            sharpenAmount = sharpenAmount,
            vignetteAngle = vignetteAngle,
            useHdr = useHdr
        )

    private class SpatialShaderProgram(
        private val noiseAmplitude: Float,
        private val sharpenAmount: Float,
        private val vignetteAngle: Float,
        useHdr: Boolean
    ) : BaseGlShaderProgram(useHdr, /* texturePoolCapacity= */ 1) {

        private val glProgram = createGlProgram()

        init {
            glProgram.setBufferAttribute(
                "aFramePosition",
                GlUtil.getNormalizedCoordinateBounds(),
                GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE
            )
            glProgram.setFloatsUniform(
                "uTransformationMatrix",
                GlUtil.create4x4IdentityMatrix()
            )
            glProgram.setFloatsUniform(
                "uTexTransformationMatrix",
                GlUtil.create4x4IdentityMatrix()
            )
            glProgram.setFloatsUniform("uNoiseStrength", floatArrayOf(noiseAmplitude))
            glProgram.setFloatsUniform("uSharpenAmount", floatArrayOf(sharpenAmount))
            glProgram.setFloatsUniform("uVignetteAngle", floatArrayOf(vignetteAngle))
        }

        override fun configure(inputWidth: Int, inputHeight: Int): Size {
            require(inputWidth > 0 && inputHeight > 0) {
                "Spatial adjustment requires positive frame dimensions: " +
                        "${inputWidth}x$inputHeight"
            }
            try {
                glProgram.setFloatsUniform(
                    "uTexelSize",
                    floatArrayOf(1f / inputWidth, 1f / inputHeight)
                )
                glProgram.setFloatsUniform(
                    "uFrameSize",
                    floatArrayOf(inputWidth.toFloat(), inputHeight.toFloat())
                )
            } catch (e: GlUtil.GlException) {
                throw VideoFrameProcessingException(e)
            }
            return Size(inputWidth, inputHeight)
        }

        override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
            try {
                glProgram.use()
                glProgram.setFloatsUniform(
                    "uFrameTime",
                    floatArrayOf(presentationTimeUs / MICROS_PER_SECOND)
                )
                glProgram.setSamplerTexIdUniform(
                    "uTexSampler",
                    inputTexId,
                    /* texUnitIndex= */ 0
                )
                glProgram.bindAttributesAndUniforms()
                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, /* first= */ 0, /* count= */ 4)
                checkGlError("draw spatial adjustments")
            } catch (e: GlUtil.GlException) {
                throw VideoFrameProcessingException(e, presentationTimeUs)
            }
        }

        override fun release() {
            super.release()
            try {
                glProgram.delete()
            } catch (e: GlUtil.GlException) {
                throw VideoFrameProcessingException(e)
            }
        }

        private fun checkGlError(operation: String) {
            val error = GLES20.glGetError()
            if (error != GLES20.GL_NO_ERROR) {
                throw VideoFrameProcessingException(
                    IllegalStateException(
                        "Could not $operation: GL error 0x${Integer.toHexString(error)}"
                    )
                )
            }
        }

        private fun createGlProgram(): GlProgram =
            try {
                GlProgram(VERTEX_SHADER, FRAGMENT_SHADER)
            } catch (e: GlUtil.GlException) {
                throw VideoFrameProcessingException(e)
            }
    }

    private companion object {
        private const val CHANNEL_MAX_VALUE = 255f
        private const val MICROS_PER_SECOND = 1_000_000f
        private const val VIGNETTE_MIN_ANGLE = 0.00005f

        private fun ffmpegNoiseStrength(noise: Float): Int {
            if (noise <= 0f) return 0
            val strength = (noise / 100f * 50f).coerceIn(0f, 50f)
            return strength.toInt()
        }

        private fun ffmpegSharpenAmount(sharpen: Float): Float {
            if (sharpen <= 0f) return 0f
            val amount = (sharpen / 25f).coerceIn(0f, 2f)
            return String.format(Locale.US, "%.4f", amount).toFloat()
        }

        private fun ffmpegVignetteAngle(vignette: Float): Float {
            if (vignette <= 0f) return 0f
            val angle = (vignette / 100f * PI / 4.0).toFloat().coerceIn(0f, 1.5f)
            if (angle < VIGNETTE_MIN_ANGLE) return 0f
            return String.format(Locale.US, "%.4f", angle).toFloat()
        }

        private const val VERTEX_SHADER = """
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

        private const val FRAGMENT_SHADER = """
            #version 100
            precision highp float;

            uniform sampler2D uTexSampler;
            uniform float uNoiseStrength;
            uniform float uSharpenAmount;
            uniform float uVignetteAngle;
            uniform float uFrameTime;
            uniform vec2 uTexelSize;
            uniform vec2 uFrameSize;
            varying vec2 vTexSamplingCoord;

            float randomValue(vec2 seed) {
                return fract(sin(dot(seed, vec2(12.9898, 78.233))) * 43758.5453);
            }

            vec3 addTemporalUniformNoise(vec3 color, vec2 pixelPosition) {
                if (uNoiseStrength <= 0.0) {
                    return color;
                }

                // FFmpeg uses a seeded LFG noise table and changes row shifts per frame.
                // This per-pixel time-seeded hash is a visual approximation, not the same
                // random sequence. Amplitude maps alls=N's roughly +/-N/2 8-bit range to RGB.
                vec2 temporalSeed = vec2(uFrameTime * 17.0, uFrameTime * 31.0);
                float noiseR = randomValue(pixelPosition + temporalSeed + vec2(0.0, 0.0));
                float noiseG = randomValue(pixelPosition + temporalSeed + vec2(19.19, 73.73));
                float noiseB = randomValue(pixelPosition + temporalSeed + vec2(47.47, 11.11));
                vec3 centeredNoise = (vec3(noiseR, noiseG, noiseB) * 2.0) - 1.0;
                return clamp(color + centeredNoise * uNoiseStrength, 0.0, 1.0);
            }

            vec3 sampleNoisyInput(vec2 textureCoordinate) {
                vec2 uv = clamp(textureCoordinate, vec2(0.0), vec2(1.0));
                vec3 color = texture2D(uTexSampler, uv).rgb;
                vec2 pixelPosition = min(
                    floor(uv * uFrameSize),
                    uFrameSize - vec2(1.0)
                );
                return addTemporalUniformNoise(color, pixelPosition);
            }

            vec3 sharpenWithFfmpeg3x3(vec2 textureCoordinate) {
                vec3 center = sampleNoisyInput(textureCoordinate);
                if (uSharpenAmount <= 0.0) {
                    return center;
                }

                // FFmpeg unsharp's 3x3 finite-state blur is a uniform box average,
                // not a [1,2,1] Gaussian kernel; clamp-to-edge matches its edge extension.
                vec3 blurred =
                    sampleNoisyInput(textureCoordinate + vec2(-uTexelSize.x, -uTexelSize.y)) +
                    sampleNoisyInput(textureCoordinate + vec2(0.0, -uTexelSize.y)) +
                    sampleNoisyInput(textureCoordinate + vec2(uTexelSize.x, -uTexelSize.y)) +
                    sampleNoisyInput(textureCoordinate + vec2(-uTexelSize.x, 0.0)) +
                    center +
                    sampleNoisyInput(textureCoordinate + vec2(uTexelSize.x, 0.0)) +
                    sampleNoisyInput(textureCoordinate + vec2(-uTexelSize.x, uTexelSize.y)) +
                    sampleNoisyInput(textureCoordinate + vec2(0.0, uTexelSize.y)) +
                    sampleNoisyInput(textureCoordinate + vec2(uTexelSize.x, uTexelSize.y));
                blurred /= 9.0;

                // FFmpeg applies original + amount * (original - rounded box blur).
                // This float RGB pass approximates its per-plane fixed-point YUV arithmetic.
                return clamp(center + uSharpenAmount * (center - blurred), 0.0, 1.0);
            }

            float ffmpegForwardVignetteFactor(vec2 textureCoordinate) {
                if (uVignetteAngle <= 0.0) {
                    return 1.0;
                }

                // FFmpeg vf_vignette.c: dnorm=hypot((x-x0)*xscale,(y-y0)*yscale)/dmax;
                // factor is zero when dnorm>1, otherwise cos(angle*dnorm)^4.
                // Normalized UVs are converted back to frame pixels for aspect-correct distance.
                vec2 deltaInPixels = (textureCoordinate - vec2(0.5)) * uFrameSize;
                float maxDistance = length(uFrameSize * 0.5);
                float normalizedDistance = length(deltaInPixels) / max(maxDistance, 1.0);
                if (normalizedDistance > 1.0) {
                    return 0.0;
                }
                float cosine = cos(uVignetteAngle * normalizedDistance);
                return cosine * cosine * cosine * cosine;
            }

            void main() {
                vec3 color = sharpenWithFfmpeg3x3(vTexSamplingCoord);
                float vignette = ffmpegForwardVignetteFactor(vTexSamplingCoord);
                float alpha = texture2D(uTexSampler, vTexSamplingCoord).a;
                gl_FragColor = vec4(clamp(color * vignette, 0.0, 1.0), alpha);
            }
        """
    }
}
