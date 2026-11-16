package com.moody.moodyvideoeditor.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.opengl.GLES20
import android.opengl.GLUtils
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram
import com.moody.moodyvideoeditor.data.AdjustmentData
import kotlin.math.roundToInt

@UnstableApi
class AdjustmentGlEffect(private val adjustmentData: AdjustmentData) : GlEffect {

    override fun isNoOp(inputWidth: Int, inputHeight: Int): Boolean =
        adjustmentData.isDefault

    @Suppress("UNUSED_PARAMETER")
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram =
        AdjustmentShaderProgram(
            AdjustmentNative.buildCubeLut(adjustmentData, LUT_SIZE),
            useHdr
        )

    private class AdjustmentShaderProgram(
        lut: FloatArray,
        useHdr: Boolean
    ) : BaseGlShaderProgram(useHdr, /* texturePoolCapacity= */ 1) {

        private val glProgram = createGlProgram()
        private val lutTextureId = createLutTexture(lut)

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
            glProgram.setFloatsUniform("uLutDimension", floatArrayOf(LUT_SIZE.toFloat()))
            glProgram.setFloatsUniform(
                "uLutTilesPerRow",
                floatArrayOf(LUT_TILES_PER_ROW.toFloat())
            )
            glProgram.setFloatsUniform(
                "uLutAtlasSize",
                floatArrayOf(LUT_ATLAS_WIDTH.toFloat(), LUT_ATLAS_HEIGHT.toFloat())
            )
        }

        override fun configure(inputWidth: Int, inputHeight: Int): Size =
            Size(inputWidth, inputHeight)

        override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
            try {
                glProgram.use()
                glProgram.setSamplerTexIdUniform(
                    "uTexSampler",
                    inputTexId,
                    /* texUnitIndex= */ 0
                )
                glProgram.setSamplerTexIdUniform(
                    "uLutSampler",
                    lutTextureId,
                    /* texUnitIndex= */ 1
                )
                glProgram.bindAttributesAndUniforms()
                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, /* first= */ 0, /* count= */ 4)
                checkGlError("draw adjustment LUT")
            } catch (e: GlUtil.GlException) {
                throw VideoFrameProcessingException(e, presentationTimeUs)
            }
        }

        override fun release() {
            super.release()
            var programError: GlUtil.GlException? = null
            try {
                glProgram.delete()
            } catch (e: GlUtil.GlException) {
                programError = e
            }
            GLES20.glDeleteTextures(1, intArrayOf(lutTextureId), 0)
            val textureError = GLES20.glGetError()
            if (programError != null) {
                throw VideoFrameProcessingException(programError)
            }
            if (textureError != GLES20.GL_NO_ERROR) {
                throw VideoFrameProcessingException(
                    IllegalStateException(
                        "Could not delete adjustment LUT texture: GL error 0x" +
                                Integer.toHexString(textureError)
                    )
                )
            }
        }

        private fun createLutTexture(lut: FloatArray): Int {
            val expectedValues = LUT_SIZE * LUT_SIZE * LUT_SIZE * LUT_CHANNELS
            check(lut.size == expectedValues) {
                "Adjustment LUT has ${lut.size} values; expected $expectedValues"
            }

            val pixels = IntArray(LUT_ATLAS_WIDTH * LUT_ATLAS_HEIGHT)
            for (blue in 0 until LUT_SIZE) {
                val tileColumn = blue % LUT_TILES_PER_ROW
                val tileRow = blue / LUT_TILES_PER_ROW
                for (green in 0 until LUT_SIZE) {
                    for (red in 0 until LUT_SIZE) {
                        val lutIndex =
                            ((blue * LUT_SIZE + green) * LUT_SIZE + red) * LUT_CHANNELS
                        val pixel = Color.rgb(
                            lut[lutIndex].roundToInt().coerceIn(0, 255),
                            lut[lutIndex + 1].roundToInt().coerceIn(0, 255),
                            lut[lutIndex + 2].roundToInt().coerceIn(0, 255)
                        )
                        val x = tileColumn * LUT_SIZE + red
                        val y = tileRow * LUT_SIZE + green
                        pixels[y * LUT_ATLAS_WIDTH + x] = pixel
                    }
                }
            }

            val bitmap = Bitmap.createBitmap(
                LUT_ATLAS_WIDTH,
                LUT_ATLAS_HEIGHT,
                Bitmap.Config.ARGB_8888
            )
            try {
                bitmap.setPixels(
                    pixels,
                    /* offset= */ 0,
                    /* stride= */ LUT_ATLAS_WIDTH,
                    /* x= */ 0,
                    /* y= */ 0,
                    LUT_ATLAS_WIDTH,
                    LUT_ATLAS_HEIGHT
                )

                val texture = IntArray(1)
                GLES20.glGenTextures(1, texture, 0)
                val textureId = texture[0]
                if (textureId == 0) {
                    throw VideoFrameProcessingException(
                        IllegalStateException("Could not allocate adjustment LUT texture")
                    )
                }

                var uploaded = false
                try {
                    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
                    GLES20.glTexParameteri(
                        GLES20.GL_TEXTURE_2D,
                        GLES20.GL_TEXTURE_MIN_FILTER,
                        GLES20.GL_NEAREST
                    )
                    GLES20.glTexParameteri(
                        GLES20.GL_TEXTURE_2D,
                        GLES20.GL_TEXTURE_MAG_FILTER,
                        GLES20.GL_NEAREST
                    )
                    GLES20.glTexParameteri(
                        GLES20.GL_TEXTURE_2D,
                        GLES20.GL_TEXTURE_WRAP_S,
                        GLES20.GL_CLAMP_TO_EDGE
                    )
                    GLES20.glTexParameteri(
                        GLES20.GL_TEXTURE_2D,
                        GLES20.GL_TEXTURE_WRAP_T,
                        GLES20.GL_CLAMP_TO_EDGE
                    )
                    GLUtils.texImage2D(
                        GLES20.GL_TEXTURE_2D,
                        /* level= */ 0,
                        bitmap,
                        /* border= */ 0
                    )
                    checkGlError("upload adjustment LUT")
                    uploaded = true
                    return textureId
                } finally {
                    if (!uploaded) {
                        GLES20.glDeleteTextures(1, texture, 0)
                    }
                }
            } finally {
                bitmap.recycle()
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
        private const val LUT_SIZE = 65
        private const val LUT_CHANNELS = 3
        private const val LUT_TILES_PER_ROW = 9
        private const val LUT_TILE_ROWS = (LUT_SIZE + LUT_TILES_PER_ROW - 1) /
                LUT_TILES_PER_ROW
        private const val LUT_ATLAS_WIDTH = LUT_SIZE * LUT_TILES_PER_ROW
        private const val LUT_ATLAS_HEIGHT = LUT_SIZE * LUT_TILE_ROWS

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
            uniform sampler2D uLutSampler;
            uniform float uLutDimension;
            uniform float uLutTilesPerRow;
            uniform vec2 uLutAtlasSize;
            varying vec2 vTexSamplingCoord;

            vec3 sampleLut(float redIndex, float greenIndex, float blueIndex) {
                float tileColumn = mod(blueIndex, uLutTilesPerRow);
                float tileRow = floor(blueIndex / uLutTilesPerRow);
                vec2 atlasPixel = vec2(tileColumn, tileRow) * uLutDimension +
                    vec2(redIndex, greenIndex) + vec2(0.5);
                return texture2D(uLutSampler, atlasPixel / uLutAtlasSize).rgb;
            }

            vec3 tetrahedralLut(vec3 inputColor) {
                float maxIndex = uLutDimension - 1.0;
                vec3 position = clamp(inputColor, 0.0, 1.0) * maxIndex;
                vec3 baseIndex = floor(position);
                vec3 fraction = position - baseIndex;
                vec3 nextIndex = min(baseIndex + 1.0, vec3(maxIndex));

                float r0 = baseIndex.r;
                float g0 = baseIndex.g;
                float b0 = baseIndex.b;
                float r1 = nextIndex.r;
                float g1 = nextIndex.g;
                float b1 = nextIndex.b;
                float fr = fraction.r;
                float fg = fraction.g;
                float fb = fraction.b;

                if (fr >= fg) {
                    if (fg >= fb) {
                        vec3 c000 = sampleLut(r0, g0, b0);
                        vec3 c100 = sampleLut(r1, g0, b0);
                        vec3 c110 = sampleLut(r1, g1, b0);
                        vec3 c111 = sampleLut(r1, g1, b1);
                        return c000 + fr * (c100 - c000) +
                            fg * (c110 - c100) + fb * (c111 - c110);
                    }
                    if (fr >= fb) {
                        vec3 c000 = sampleLut(r0, g0, b0);
                        vec3 c100 = sampleLut(r1, g0, b0);
                        vec3 c101 = sampleLut(r1, g0, b1);
                        vec3 c111 = sampleLut(r1, g1, b1);
                        return c000 + fr * (c100 - c000) +
                            fb * (c101 - c100) + fg * (c111 - c101);
                    }
                    vec3 c000 = sampleLut(r0, g0, b0);
                    vec3 c001 = sampleLut(r0, g0, b1);
                    vec3 c101 = sampleLut(r1, g0, b1);
                    vec3 c111 = sampleLut(r1, g1, b1);
                    return c000 + fb * (c001 - c000) +
                        fr * (c101 - c001) + fg * (c111 - c101);
                }

                if (fr >= fb) {
                    vec3 c000 = sampleLut(r0, g0, b0);
                    vec3 c010 = sampleLut(r0, g1, b0);
                    vec3 c110 = sampleLut(r1, g1, b0);
                    vec3 c111 = sampleLut(r1, g1, b1);
                    return c000 + fg * (c010 - c000) +
                        fr * (c110 - c010) + fb * (c111 - c110);
                }
                if (fg >= fb) {
                    vec3 c000 = sampleLut(r0, g0, b0);
                    vec3 c010 = sampleLut(r0, g1, b0);
                    vec3 c011 = sampleLut(r0, g1, b1);
                    vec3 c111 = sampleLut(r1, g1, b1);
                    return c000 + fg * (c010 - c000) +
                        fb * (c011 - c010) + fr * (c111 - c011);
                }
                vec3 c000 = sampleLut(r0, g0, b0);
                vec3 c001 = sampleLut(r0, g0, b1);
                vec3 c011 = sampleLut(r0, g1, b1);
                vec3 c111 = sampleLut(r1, g1, b1);
                return c000 + fb * (c001 - c000) +
                    fg * (c011 - c001) + fr * (c111 - c011);
            }

            void main() {
                vec4 sourceColor = texture2D(uTexSampler, vTexSamplingCoord);
                vec3 adjustedColor = tetrahedralLut(sourceColor.rgb);
                gl_FragColor = vec4(clamp(adjustedColor, 0.0, 1.0), sourceColor.a);
            }
        """
    }
}
