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
import java.util.LinkedHashMap
import java.util.Locale

@UnstableApi
class MaskOverlayGlEffect(
    private val pattern: String,
    private val staticFilePath: String?,
    private val fps: Int,
    private val frameCount: Int,
    private val clipStartSec: Double,
    private val clipEndSec: Double,
    private val timestampOffsetSec: Double = 0.0
) : GlEffect {

    override fun isNoOp(inputWidth: Int, inputHeight: Int): Boolean =
        frameCount <= 0

    @Suppress("UNUSED_PARAMETER")
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram =
        MaskShaderProgram(
            pattern = pattern,
            staticFilePath = staticFilePath,
            fps = fps,
            frameCount = frameCount,
            clipStartSec = clipStartSec,
            clipEndSec = clipEndSec,
            timestampOffsetSec = timestampOffsetSec,
            useHdr = useHdr
        )

    private class MaskShaderProgram(
        private val pattern: String,
        private val staticFilePath: String?,
        private val fps: Int,
        private val frameCount: Int,
        private val clipStartSec: Double,
        private val clipEndSec: Double,
        private val timestampOffsetSec: Double,
        useHdr: Boolean
    ) : BaseGlShaderProgram(useHdr, /* texturePoolCapacity= */ 1) {

        private val glProgram = createGlProgram()
        private val textureCacheCapacity =
            if (staticFilePath != null) STATIC_TEXTURE_CACHE_CAPACITY
            else ANIMATED_TEXTURE_CACHE_CAPACITY
        private val textureCache = LinkedHashMap<Int, Int>(
            textureCacheCapacity + 1,
            CACHE_LOAD_FACTOR,
            /* accessOrder= */ true
        )
        private val transparentTextureId = createTransparentTexture()
        private var hasLoggedStaticTimebase = false

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
            glProgram.setFloatsUniform("uMaskAvailable", floatArrayOf(0f))
        }

        override fun configure(inputWidth: Int, inputHeight: Int): Size =
            Size(inputWidth, inputHeight)

        override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
            try {
                val rawTimeSec = presentationTimeUs / MICROS_PER_SECOND
                // Offset 0 means the timestamp is composition-global. If Media3 supplies
                // clip-local time, the caller adds clipStartSec through this offset.
                val effectiveTimeSec = rawTimeSec + timestampOffsetSec
                val isVisible = frameCount > 0 &&
                        effectiveTimeSec >= clipStartSec &&
                        effectiveTimeSec < clipEndSec
                val frameIndex = if (isVisible) frameIndexAt(effectiveTimeSec) else NO_FRAME
                val shouldLogTimebase = frameIndex == 1 ||
                        (frameIndex == 0 &&
                                (staticFilePath == null || !hasLoggedStaticTimebase))
                if (shouldLogTimebase) {
                    if (staticFilePath != null) hasLoggedStaticTimebase = true
                    Log.d(
                        "MASK_TIMEBASE",
                        "presentationTimeUs=$presentationTimeUs, rawTimeSec=$rawTimeSec, " +
                                "timestampOffsetSec=$timestampOffsetSec, " +
                                "effectiveTimeSec=$effectiveTimeSec, " +
                                "clipStartSec=$clipStartSec, clipEndSec=$clipEndSec, " +
                                "frameIndex=$frameIndex"
                    )
                }
                val maskTextureId = if (isVisible) {
                    textureForFrame(frameIndex, presentationTimeUs)
                } else {
                    transparentTextureId
                }

                glProgram.use()
                glProgram.setFloatsUniform(
                    "uMaskAvailable",
                    floatArrayOf(if (isVisible) 1f else 0f)
                )
                glProgram.setSamplerTexIdUniform(
                    "uTexSampler",
                    inputTexId,
                    /* texUnitIndex= */ 0
                )
                glProgram.setSamplerTexIdUniform(
                    "uMaskSampler",
                    maskTextureId,
                    /* texUnitIndex= */ 1
                )
                glProgram.bindAttributesAndUniforms()
                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, /* first= */ 0, /* count= */ 4)
                checkGlError("draw mask overlay")
            } catch (e: GlUtil.GlException) {
                throw VideoFrameProcessingException(e, presentationTimeUs)
            }
        }

        override fun release() {
            textureCache.values.toList().forEach { textureId ->
                deleteTextureBestEffort(textureId, "cached mask")
            }
            textureCache.clear()
            deleteTextureBestEffort(transparentTextureId, "transparent fallback")

            try {
                super.release()
            } catch (error: Exception) {
                Log.e(TAG, "Could not release base mask shader program", error)
            }
            try {
                glProgram.delete()
            } catch (error: Exception) {
                Log.e(TAG, "Could not delete mask GL program", error)
            }
        }

        private fun frameIndexAt(timeSec: Double): Int {
            if (staticFilePath != null || frameCount == 1) return 0
            check(fps > 0) { "Animated mask sequence must have a positive frame rate" }
            val localTimeSec = (timeSec - clipStartSec).coerceAtLeast(0.0)
            return kotlin.math.floor(localTimeSec * fps)
                .toInt()
                .coerceIn(0, frameCount - 1)
        }

        /**
         * PNG decoding and texture upload happen on Media3's GL thread here.
         * Cache only the uploaded texture; the decoded bitmap is recycled immediately.
         */
        private fun textureForFrame(frameIndex: Int, presentationTimeUs: Long): Int {
            textureCache[frameIndex]?.let { return it }

            val path = if (staticFilePath != null) {
                staticFilePath
            } else {
                try {
                    String.format(Locale.US, pattern, frameIndex + 1)
                } catch (error: Exception) {
                    Log.e(TAG, "Invalid mask frame pattern: $pattern", error)
                    throw VideoFrameProcessingException(error, presentationTimeUs)
                }
            }
            val options = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
                inPremultiplied = false
                inScaled = false
            }
            val bitmap = BitmapFactory.decodeFile(path, options)
            if (bitmap == null) {
                val error = IllegalStateException("Could not decode mask frame: $path")
                Log.e(TAG, error.message.orEmpty(), error)
                throw VideoFrameProcessingException(error, presentationTimeUs)
            }

            val textureId = try {
                uploadBitmap(bitmap, "mask frame $path", presentationTimeUs)
            } catch (error: VideoFrameProcessingException) {
                throw error
            } catch (error: Exception) {
                Log.e(TAG, "Could not upload mask frame: $path", error)
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
                deleteTextureBestEffort(textureId, "evicted mask frame")
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
        private const val TAG = "MaskOverlayGlEffect"
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
            uniform sampler2D uMaskSampler;
            uniform float uMaskAvailable;
            varying vec2 vTexSamplingCoord;

            void main() {
                vec4 base = texture2D(uTexSampler, vTexSamplingCoord);
                if (uMaskAvailable < 0.5) {
                    gl_FragColor = base;
                    return;
                }

                vec4 maskSample = texture2D(uMaskSampler, vTexSamplingCoord);
                float coverage = clamp(maskSample.a, 0.0, 1.0);
                // Keep RGB premultiplied so transparent pixels remain transparent downstream.
                gl_FragColor = vec4(base.rgb * coverage, base.a * coverage);
            }
        """
    }
}
