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
import com.moody.moodyvideoeditor.data.FilterState

@UnstableApi
class FilterLayerGlEffect(private val filterState: FilterState) : GlEffect {

    override fun isNoOp(inputWidth: Int, inputHeight: Int): Boolean =
        !FiltersEngine.hasRealTimeFilters(filterState)

    @Suppress("UNUSED_PARAMETER")
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram =
        FilterLayerShaderProgram(filterState, useHdr)

    private class FilterLayerShaderProgram(
        filterState: FilterState,
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

            val matrix = FiltersEngine.buildColorMatrix(filterState).array
            // Each vec4 contains one row from Android's 4x5 ColorMatrix, preserving its order.
            glProgram.setFloatsUniform(
                "uColorMatrixR",
                floatArrayOf(matrix[0], matrix[1], matrix[2], matrix[3])
            )
            glProgram.setFloatsUniform(
                "uColorMatrixG",
                floatArrayOf(matrix[5], matrix[6], matrix[7], matrix[8])
            )
            glProgram.setFloatsUniform(
                "uColorMatrixB",
                floatArrayOf(matrix[10], matrix[11], matrix[12], matrix[13])
            )
            glProgram.setFloatsUniform(
                "uColorMatrixA",
                floatArrayOf(matrix[15], matrix[16], matrix[17], matrix[18])
            )
            // Android ColorMatrix offsets are 0..255; shader colors are normalized to 0..1.
            glProgram.setFloatsUniform(
                "uColorOffset",
                floatArrayOf(
                    matrix[4] / 255f,
                    matrix[9] / 255f,
                    matrix[14] / 255f,
                    matrix[19] / 255f
                )
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
                glProgram.bindAttributesAndUniforms()
                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, /* first= */ 0, /* count= */ 4)
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

        private fun createGlProgram(): GlProgram =
            try {
                GlProgram(VERTEX_SHADER, FRAGMENT_SHADER)
            } catch (e: GlUtil.GlException) {
                throw VideoFrameProcessingException(e)
            }
    }

    private companion object {
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
            uniform vec4 uColorMatrixR;
            uniform vec4 uColorMatrixG;
            uniform vec4 uColorMatrixB;
            uniform vec4 uColorMatrixA;
            uniform vec4 uColorOffset;
            varying vec2 vTexSamplingCoord;

            void main() {
                vec4 sourceColor = texture2D(uTexSampler, vTexSamplingCoord);
                // These dot products apply the exact four rows from FiltersEngine's ColorMatrix.
                vec4 filteredColor = vec4(
                    dot(uColorMatrixR, sourceColor) + uColorOffset.r,
                    dot(uColorMatrixG, sourceColor) + uColorOffset.g,
                    dot(uColorMatrixB, sourceColor) + uColorOffset.b,
                    dot(uColorMatrixA, sourceColor) + uColorOffset.a
                );
                gl_FragColor = clamp(filteredColor, 0.0, 1.0);
            }
        """
    }
}
