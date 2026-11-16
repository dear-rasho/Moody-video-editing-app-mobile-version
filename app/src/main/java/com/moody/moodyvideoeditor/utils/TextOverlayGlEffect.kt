package com.moody.moodyvideoeditor.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.opengl.GLES20
import android.opengl.GLUtils
import android.util.Log
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram
import com.moody.moodyvideoeditor.data.KeyframeLibrary
import java.util.LinkedHashMap
import java.util.Locale

@UnstableApi
class TextOverlayGlEffect(private val sequence: TextOverlaySequence) : GlEffect {

    override fun isNoOp(inputWidth: Int, inputHeight: Int): Boolean =
        sequence.frameCount <= 0

    @Suppress("UNUSED_PARAMETER")
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram =
        TextOverlayShaderProgram(sequence, useHdr)

    private class TextOverlayShaderProgram(
        private val sequence: TextOverlaySequence,
        useHdr: Boolean
    ) : BaseGlShaderProgram(useHdr, /* texturePoolCapacity= */ 1) {

        private val glProgram = createGlProgram()
        private val textureCacheCapacity =
            if (sequence.isStaticImage) STATIC_TEXTURE_CACHE_CAPACITY
            else ANIMATED_TEXTURE_CACHE_CAPACITY
        private val textureCache = LinkedHashMap<Int, Int>(
            textureCacheCapacity + 1,
            CACHE_LOAD_FACTOR,
            /* accessOrder= */ true
        )
        private val transparentTextureId = createTransparentTexture()

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
            glProgram.setFloatsUniform("uTextAvailable", floatArrayOf(0f))
            glProgram.setFloatsUniform("uTextOpacity", floatArrayOf(0f))
            glProgram.setFloatsUniform(
                "uBlendMode",
                floatArrayOf(if (sequence.blendMode.equals(ADDITION_BLEND, true)) 1f else 0f)
            )
        }

        override fun configure(inputWidth: Int, inputHeight: Int): Size =
            Size(inputWidth, inputHeight)

        override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
            try {
                val timeSec = presentationTimeUs / MICROS_PER_SECOND
                val isVisible = timeSec >= sequence.startSec && timeSec < sequence.endSec
                val frameIndex = if (isVisible) frameIndexAt(timeSec) else NO_FRAME
                val textTextureId = if (isVisible) {
                    textureForFrame(frameIndex, presentationTimeUs)
                } else {
                    transparentTextureId
                }
                val opacity = if (isVisible) opacityAt(timeSec) else 0f

                glProgram.use()
                glProgram.setFloatsUniform(
                    "uTextAvailable",
                    floatArrayOf(if (isVisible) 1f else 0f)
                )
                glProgram.setFloatsUniform("uTextOpacity", floatArrayOf(opacity))
                glProgram.setFloatsUniform(
                    "uBlendMode",
                    floatArrayOf(if (sequence.blendMode.equals(ADDITION_BLEND, true)) 1f else 0f)
                )
                glProgram.setSamplerTexIdUniform(
                    "uTexSampler",
                    inputTexId,
                    /* texUnitIndex= */ 0
                )
                glProgram.setSamplerTexIdUniform(
                    "uTextSampler",
                    textTextureId,
                    /* texUnitIndex= */ 1
                )
                glProgram.bindAttributesAndUniforms()
                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, /* first= */ 0, /* count= */ 4)
                checkGlError("draw text overlay")
            } catch (e: GlUtil.GlException) {
                throw VideoFrameProcessingException(e, presentationTimeUs)
            }
        }

        override fun release() {
            textureCache.values.toList().forEach { textureId ->
                deleteTextureBestEffort(textureId, "cached overlay")
            }
            textureCache.clear()
            deleteTextureBestEffort(transparentTextureId, "transparent fallback")

            try {
                super.release()
            } catch (error: Exception) {
                Log.e(TAG, "Could not release base text-overlay shader program", error)
            }
            try {
                glProgram.delete()
            } catch (error: Exception) {
                Log.e(TAG, "Could not delete text-overlay GL program", error)
            }
        }

        private fun frameIndexAt(timeSec: Double): Int {
            if (sequence.isStaticImage) return 0
            val localTimeSec = (timeSec - sequence.startSec).coerceAtLeast(0.0)
            return kotlin.math.floor(localTimeSec * sequence.fps)
                .toInt()
                .coerceIn(0, sequence.frameCount - 1)
        }

        private fun opacityAt(timeSec: Double): Float {
            val localTimeSec = (timeSec - sequence.startSec).toFloat()
            val opacityTrack =
                sequence.layerOpacityKeyframes[KeyframeLibrary.LAYER_OPACITY].orEmpty()
            val opacityPercent = if (opacityTrack.isNotEmpty()) {
                KeyframeStore.sample(
                    sequence.layerOpacityKeyframes,
                    KeyframeLibrary.LAYER_OPACITY,
                    localTimeSec,
                    sequence.layerOpacity
                )
            } else {
                sequence.layerOpacity
            }
            check(opacityPercent.isFinite()) {
                "Text overlay opacity is not finite at ${localTimeSec}s"
            }
            return (opacityPercent / 100f).coerceIn(0f, 1f)
        }

        /**
         * PNG decoding and texture upload happen on Media3's GL thread here.
         * Cache only the uploaded texture; the decoded bitmap is recycled immediately.
         */
        private fun textureForFrame(frameIndex: Int, presentationTimeUs: Long): Int {
            textureCache[frameIndex]?.let { return it }

            val fileIndex = frameIndex + sequence.startNumber
            val path = try {
                String.format(Locale.US, sequence.pattern, fileIndex)
            } catch (error: Exception) {
                Log.e(TAG, "Invalid text-overlay frame pattern: ${sequence.pattern}", error)
                throw VideoFrameProcessingException(error, presentationTimeUs)
            }
            val options = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
                inPremultiplied = false
                inScaled = false
            }
            val bitmap = BitmapFactory.decodeFile(path, options)
            if (bitmap == null) {
                val error = IllegalStateException("Could not decode text-overlay frame: $path")
                Log.e(TAG, error.message.orEmpty(), error)
                throw VideoFrameProcessingException(error, presentationTimeUs)
            }

            val textureId = try {
                uploadBitmap(bitmap, "text-overlay frame $path", presentationTimeUs)
            } catch (error: VideoFrameProcessingException) {
                throw error
            } catch (error: Exception) {
                Log.e(TAG, "Could not upload text-overlay frame: $path", error)
                throw VideoFrameProcessingException(error, presentationTimeUs)
            } finally {
                bitmap.recycle()
            }

            textureCache[frameIndex] = textureId
            evictEldestTextures()
            return textureId
        }

        private fun evictEldestTextures() {
            while (textureCache.size > textureCacheCapacity) {
                val iterator = textureCache.entries.iterator()
                if (!iterator.hasNext()) return
                val eldest = iterator.next()
                val textureId = eldest.value
                iterator.remove()
                deleteTextureBestEffort(textureId, "evicted overlay frame")
            }
        }

        private fun createTransparentTexture(): Int {
            val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.TRANSPARENT)
            return try {
                uploadBitmap(bitmap, "transparent fallback", NO_PRESENTATION_TIME)
            } finally {
                bitmap.recycle()
            }
        }

        private fun uploadBitmap(
            bitmap: Bitmap,
            description: String,
            presentationTimeUs: Long
        ): Int {
            val texture = IntArray(1)
            GLES20.glGenTextures(1, texture, 0)
            val textureId = texture[0]
            if (textureId == 0) {
                val error = IllegalStateException("Could not allocate $description texture")
                if (presentationTimeUs == NO_PRESENTATION_TIME) throw error
                throw VideoFrameProcessingException(error, presentationTimeUs)
            }

            var uploaded = false
            try {
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
                GLES20.glTexParameteri(
                    GLES20.GL_TEXTURE_2D,
                    GLES20.GL_TEXTURE_MIN_FILTER,
                    GLES20.GL_LINEAR
                )
                GLES20.glTexParameteri(
                    GLES20.GL_TEXTURE_2D,
                    GLES20.GL_TEXTURE_MAG_FILTER,
                    GLES20.GL_LINEAR
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
                val errorCode = GLES20.glGetError()
                if (errorCode != GLES20.GL_NO_ERROR) {
                    throw IllegalStateException(
                        "Could not upload $description: GL error 0x" +
                                Integer.toHexString(errorCode)
                    )
                }
                uploaded = true
                return textureId
            } catch (error: Exception) {
                if (presentationTimeUs == NO_PRESENTATION_TIME) throw error
                throw VideoFrameProcessingException(error, presentationTimeUs)
            } finally {
                if (!uploaded) {
                    GLES20.glDeleteTextures(1, texture, 0)
                }
            }
        }

        private fun deleteTextureBestEffort(textureId: Int, description: String) {
            try {
                GLES20.glDeleteTextures(1, intArrayOf(textureId), 0)
                val errorCode = GLES20.glGetError()
                if (errorCode != GLES20.GL_NO_ERROR) {
                    Log.e(
                        TAG,
                        "Could not delete $description texture: GL error 0x" +
                                Integer.toHexString(errorCode)
                    )
                }
            } catch (error: Exception) {
                Log.e(TAG, "Could not delete $description texture", error)
            }
        }

        private fun checkGlError(operation: String) {
            val errorCode = GLES20.glGetError()
            if (errorCode != GLES20.GL_NO_ERROR) {
                throw VideoFrameProcessingException(
                    IllegalStateException(
                        "Could not $operation: GL error 0x${Integer.toHexString(errorCode)}"
                    )
                )
            }
        }

        private fun createGlProgram(): GlProgram =
            try {
                GlProgram(VERTEX_SHADER, FRAGMENT_SHADER)
            } catch (error: GlUtil.GlException) {
                throw VideoFrameProcessingException(error)
            }
    }

    private companion object {
        private const val TAG = "TextOverlayGlEffect"
        private const val ADDITION_BLEND = "addition"
        private const val ANIMATED_TEXTURE_CACHE_CAPACITY = 30
        private const val STATIC_TEXTURE_CACHE_CAPACITY = 1
        private const val CACHE_LOAD_FACTOR = 0.75f
        private const val MICROS_PER_SECOND = 1_000_000.0
        private const val NO_FRAME = -1
        private const val NO_PRESENTATION_TIME = Long.MIN_VALUE

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
            uniform sampler2D uTextSampler;
            uniform float uTextAvailable;
            uniform float uTextOpacity;
            uniform float uBlendMode;
            varying vec2 vTexSamplingCoord;

            void main() {
                vec4 base = texture2D(uTexSampler, vTexSamplingCoord);
                if (uTextAvailable < 0.5) {
                    gl_FragColor = base;
                    return;
                }

                vec4 text = texture2D(uTextSampler, vTexSamplingCoord);
                float coverage = clamp(text.a * uTextOpacity, 0.0, 1.0);
                vec3 blended;
                if (uBlendMode < 0.5) {
                    blended = mix(base.rgb, text.rgb, coverage);
                } else {
                    blended = base.rgb + text.rgb * coverage;
                }

                // FFmpeg overlay preserves the main input alpha by default.
                gl_FragColor = vec4(clamp(blended, 0.0, 1.0), base.a);
            }
        """
    }
}
