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
import com.moody.moodyvideoeditor.data.KeyframeLibrary
import com.moody.moodyvideoeditor.data.KeyframeMap
import kotlin.math.abs
import kotlin.math.min

@UnstableApi
class TransformGlEffect(
    private val targetWidth: Int,
    private val targetHeight: Int,
    private val scale: Float,
    private val scaleX: Float,
    private val scaleY: Float,
    private val rotationDeg: Float,
    private val offsetX: Float,
    private val offsetY: Float,
    private val cropL: Float,
    private val cropR: Float,
    private val cropT: Float,
    private val cropB: Float,
    private val layerOpacity: Float,
    private val keyframes: KeyframeMap = emptyMap(),
    private val clipStartSec: Double = 0.0,
    private val timestampOffsetSec: Double = 0.0,
    private val fps: Int = 30,
    private val positionHandledByOverlay: Boolean = false
) : GlEffect {

    private val hasKeyframes = keyframes.values.any { it.isNotEmpty() }
    private val offsetAppliedAfterRotation =
        hasKeyframes && resolveOffsetAppliedAfterRotation(positionHandledByOverlay)

    init {
        require(fps > 0) { "Transform frame rate must be positive" }
    }

    override fun isNoOp(inputWidth: Int, inputHeight: Int): Boolean =
        !hasKeyframes &&
                targetWidth == inputWidth &&
                targetHeight == inputHeight &&
                scale == 1f &&
                scaleX == 1f &&
                scaleY == 1f &&
                rotationDeg == 0f &&
                offsetX == 0f &&
                offsetY == 0f &&
                cropL == 0f &&
                cropR == 0f &&
                cropT == 0f &&
                cropB == 0f &&
                layerOpacity == 100f

    @Suppress("UNUSED_PARAMETER")
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram =
        TransformShaderProgram(
            targetWidth = targetWidth,
            targetHeight = targetHeight,
            scale = scale,
            scaleX = scaleX,
            scaleY = scaleY,
            rotationDeg = rotationDeg,
            offsetX = offsetX,
            offsetY = offsetY,
            cropL = cropL,
            cropR = cropR,
            cropT = cropT,
            cropB = cropB,
            layerOpacity = layerOpacity,
            keyframes = keyframes,
            hasKeyframes = hasKeyframes,
            clipStartSec = clipStartSec,
            timestampOffsetSec = timestampOffsetSec,
            offsetAppliedAfterRotation = offsetAppliedAfterRotation,
            useHdr = useHdr
        )

    private fun resolveOffsetAppliedAfterRotation(positionHandledByOverlay: Boolean): Boolean {
        // Mirrors FFmpeg's transform-path selection for the inverse offset order.
        val scaleTrack = keyframes["scale"].orEmpty()
        val scaleXTrack = keyframes["scaleX"].orEmpty()
        val scaleYTrack = keyframes["scaleY"].orEmpty()
        val rotationTrack = keyframes["rotation"].orEmpty()
        val anchorXTrack = keyframes["anchorX"].orEmpty()
        val anchorYTrack = keyframes["anchorY"].orEmpty()

        val hasAnimatedScale = scaleTrack
            .map { it.value }
            .distinctBy { (it * 1000f).toInt() }
            .size > 1
        val userScale = if (scaleTrack.isNotEmpty() && !hasAnimatedScale) {
            (scaleTrack.first().value / 100f).coerceIn(0f, MAX_SCALE)
        } else {
            scale.coerceIn(0f, MAX_SCALE)
        }
        val userScaleX = if (scaleXTrack.size == 1) {
            (scaleXTrack.first().value / 100f).coerceIn(0f, MAX_SCALE)
        } else {
            (userScale * scaleX).coerceIn(0f, MAX_SCALE)
        }
        val userScaleY = if (scaleYTrack.size == 1) {
            (scaleYTrack.first().value / 100f).coerceIn(0f, MAX_SCALE)
        } else {
            (userScale * scaleY).coerceIn(0f, MAX_SCALE)
        }
        val hasScaleTrack = hasAnimatedScale ||
                scaleXTrack.isNotEmpty() ||
                scaleYTrack.isNotEmpty() ||
                abs(userScaleX - userScaleY) > 0.01f
        val useKeyframedTransform = hasScaleTrack ||
                rotationTrack.isNotEmpty() ||
                anchorXTrack.isNotEmpty() ||
                anchorYTrack.isNotEmpty()

        fun sampledTrackValues(property: String, baseValue: Float): List<Float> {
            val track = keyframes[property].orEmpty().sortedBy { it.time }
            if (track.isEmpty()) return listOf(baseValue)
            if (track.size == 1) return listOf(track.first().value)
            return buildList {
                track.zipWithNext().forEach { (start, end) ->
                    for (step in 0..64) {
                        val time = start.time + (end.time - start.time) * step / 64f
                        add(KeyframeStore.sample(keyframes, property, time, baseValue))
                    }
                }
            }
        }

        val sampledAnchorX = sampledTrackValues("anchorX", 50f)
        val sampledAnchorY = sampledTrackValues("anchorY", 50f)
        val sampledPositionX = sampledTrackValues("x", 50f + offsetX * 100f)
        val sampledPositionY = sampledTrackValues("y", 50f + offsetY * 100f)
        val hasAnimatedRotation = abs(rotationDeg) >= 0.001f ||
                rotationTrack.any { abs(it.value) >= 0.001f }

        val canUseSimpleTransformFilter = useKeyframedTransform &&
                sampledAnchorX.all { abs(it - 50f) < 0.001f } &&
                sampledAnchorY.all { abs(it - 50f) < 0.001f } &&
                (positionHandledByOverlay ||
                        (sampledPositionX.all { it in 0f..100f } &&
                                sampledPositionY.all { it in 0f..100f })) &&
                (!hasAnimatedRotation || positionHandledByOverlay ||
                        (sampledPositionX.all { abs(it - 50f) < 0.001f } &&
                                sampledPositionY.all { abs(it - 50f) < 0.001f })) &&
                scaleX > 0f && scaleY > 0f

        return positionHandledByOverlay ||
                (useKeyframedTransform && !canUseSimpleTransformFilter)
    }

    private class TransformShaderProgram(
        private val targetWidth: Int,
        private val targetHeight: Int,
        private val scale: Float,
        private val scaleX: Float,
        private val scaleY: Float,
        private val rotationDeg: Float,
        private val offsetX: Float,
        private val offsetY: Float,
        private val cropL: Float,
        private val cropR: Float,
        private val cropT: Float,
        private val cropB: Float,
        private val layerOpacity: Float,
        private val keyframes: KeyframeMap,
        private val hasKeyframes: Boolean,
        private val clipStartSec: Double,
        private val timestampOffsetSec: Double,
        offsetAppliedAfterRotation: Boolean,
        useHdr: Boolean
    ) : BaseGlShaderProgram(useHdr, /* texturePoolCapacity= */ 1) {

        private val glProgram = createGlProgram()

        init {
            require(targetWidth > 0 && targetHeight > 0) {
                "Transform target dimensions must be positive"
            }
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
            glProgram.setFloatsUniform(
                "uTargetSize",
                floatArrayOf(targetWidth.toFloat(), targetHeight.toFloat())
            )
            glProgram.setFloatsUniform(
                "uScaleX",
                floatArrayOf((scale.coerceIn(0f, MAX_SCALE) * scaleX).coerceIn(0f, MAX_SCALE))
            )
            glProgram.setFloatsUniform(
                "uScaleY",
                floatArrayOf((scale.coerceIn(0f, MAX_SCALE) * scaleY).coerceIn(0f, MAX_SCALE))
            )
            glProgram.setFloatsUniform(
                "uRotationRad",
                floatArrayOf(Math.toRadians((rotationDeg % 360f).toDouble()).toFloat())
            )
            glProgram.setFloatsUniform("uOffsetX", floatArrayOf(offsetX))
            glProgram.setFloatsUniform("uOffsetY", floatArrayOf(offsetY))
            glProgram.setFloatsUniform("uAnchorX", floatArrayOf(0.5f))
            glProgram.setFloatsUniform("uAnchorY", floatArrayOf(0.5f))
            glProgram.setFloatsUniform(
                "uOffsetAfterRotation",
                floatArrayOf(if (offsetAppliedAfterRotation) 1f else 0f)
            )
            glProgram.setFloatsUniform("uCropL", floatArrayOf(cropL.coerceIn(0f, 1f)))
            glProgram.setFloatsUniform("uCropR", floatArrayOf(cropR.coerceIn(0f, 1f)))
            glProgram.setFloatsUniform("uCropT", floatArrayOf(cropT.coerceIn(0f, 1f)))
            glProgram.setFloatsUniform("uCropB", floatArrayOf(cropB.coerceIn(0f, 1f)))
            glProgram.setFloatsUniform(
                "uLayerOpacity",
                floatArrayOf((layerOpacity / 100f).coerceIn(0f, 1f))
            )
        }

        override fun configure(inputWidth: Int, inputHeight: Int): Size {
            check(inputWidth > 0 && inputHeight > 0) {
                "Transform source dimensions must be positive"
            }
            val fitScale = min(
                targetWidth.toFloat() / inputWidth,
                targetHeight.toFloat() / inputHeight
            )
            val padX = (targetWidth - inputWidth * fitScale) / 2f
            val padY = (targetHeight - inputHeight * fitScale) / 2f
            glProgram.setFloatsUniform(
                "uSourceSize",
                floatArrayOf(inputWidth.toFloat(), inputHeight.toFloat())
            )
            glProgram.setFloatsUniform("uFitScale", floatArrayOf(fitScale))
            glProgram.setFloatsUniform("uPad", floatArrayOf(padX, padY))
            return Size(targetWidth, targetHeight)
        }

        override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
            try {
                glProgram.use()
                if (hasKeyframes) updateKeyframedUniforms(presentationTimeUs)
                glProgram.setSamplerTexIdUniform(
                    "uTexSampler",
                    inputTexId,
                    /* texUnitIndex= */ 0
                )
                glProgram.bindAttributesAndUniforms()
                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, /* first= */ 0, /* count= */ 4)
                checkGlError("draw clip transform")
            } catch (e: GlUtil.GlException) {
                throw VideoFrameProcessingException(e, presentationTimeUs)
            }
        }

        private fun updateKeyframedUniforms(presentationTimeUs: Long) {
            val localTimeSec = (
                    presentationTimeUs / MICROS_PER_SECOND +
                            timestampOffsetSec -
                            clipStartSec
                    ).coerceAtLeast(0.0).toFloat()
            val scaleSample = KeyframeStore.sample(
                keyframes,
                "scale",
                localTimeSec,
                scale * 100f
            )
            val scaleXTrack = keyframes["scaleX"].orEmpty()
            val scaleYTrack = keyframes["scaleY"].orEmpty()
            val sampledScaleX = KeyframeStore.sample(
                keyframes,
                "scaleX",
                localTimeSec,
                scale * scaleX * 100f
            )
            val sampledScaleY = KeyframeStore.sample(
                keyframes,
                "scaleY",
                localTimeSec,
                scale * scaleY * 100f
            )
            val effectiveScaleX = if (scaleXTrack.isNotEmpty()) {
                sampledScaleX / 100f
            } else {
                (scaleSample / 100f) * scaleX
            }.coerceIn(0f, MAX_SCALE)
            val effectiveScaleY = if (scaleYTrack.isNotEmpty()) {
                sampledScaleY / 100f
            } else {
                (scaleSample / 100f) * scaleY
            }.coerceIn(0f, MAX_SCALE)
            val rotation = KeyframeStore.sample(
                keyframes,
                "rotation",
                localTimeSec,
                rotationDeg
            )
            val xPosition = KeyframeStore.sample(
                keyframes,
                "x",
                localTimeSec,
                50f + offsetX * 100f
            )
            val yPosition = KeyframeStore.sample(
                keyframes,
                "y",
                localTimeSec,
                50f + offsetY * 100f
            )
            val anchorX = KeyframeStore.sample(keyframes, "anchorX", localTimeSec, 50f) / 100f
            val anchorY = KeyframeStore.sample(keyframes, "anchorY", localTimeSec, 50f) / 100f
            val sampledCropL = KeyframeStore.sample(keyframes, "cropL", localTimeSec, cropL)
            val sampledCropR = KeyframeStore.sample(keyframes, "cropR", localTimeSec, cropR)
            val sampledCropT = KeyframeStore.sample(keyframes, "cropT", localTimeSec, cropT)
            val sampledCropB = KeyframeStore.sample(keyframes, "cropB", localTimeSec, cropB)
            val sampledOpacity = KeyframeStore.sample(
                keyframes,
                KeyframeLibrary.LAYER_OPACITY,
                localTimeSec,
                layerOpacity
            )
            val shaderScaleX = if (effectiveScaleX <= 0f) {
                0f
            } else {
                effectiveScaleX.coerceAtLeast(MIN_SCALE)
            }
            val shaderScaleY = if (effectiveScaleY <= 0f) {
                0f
            } else {
                effectiveScaleY.coerceAtLeast(MIN_SCALE)
            }

            glProgram.setFloatsUniform(
                "uScaleX",
                floatArrayOf(shaderScaleX)
            )
            glProgram.setFloatsUniform(
                "uScaleY",
                floatArrayOf(shaderScaleY)
            )
            glProgram.setFloatsUniform(
                "uRotationRad",
                floatArrayOf(Math.toRadians((rotation % 360f).toDouble()).toFloat())
            )
            glProgram.setFloatsUniform("uOffsetX", floatArrayOf((xPosition - 50f) / 100f))
            glProgram.setFloatsUniform("uOffsetY", floatArrayOf((yPosition - 50f) / 100f))
            glProgram.setFloatsUniform("uAnchorX", floatArrayOf(anchorX))
            glProgram.setFloatsUniform("uAnchorY", floatArrayOf(anchorY))
            glProgram.setFloatsUniform(
                "uCropL",
                floatArrayOf(sampledCropL.coerceIn(0f, 1f))
            )
            glProgram.setFloatsUniform(
                "uCropR",
                floatArrayOf(sampledCropR.coerceIn(0f, 1f))
            )
            glProgram.setFloatsUniform(
                "uCropT",
                floatArrayOf(sampledCropT.coerceIn(0f, 1f))
            )
            glProgram.setFloatsUniform(
                "uCropB",
                floatArrayOf(sampledCropB.coerceIn(0f, 1f))
            )
            glProgram.setFloatsUniform(
                "uLayerOpacity",
                floatArrayOf((sampledOpacity / 100f).coerceIn(0f, 1f))
            )
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
        private const val MAX_SCALE = 5f
        private const val MIN_SCALE = 0.001f
        private const val MICROS_PER_SECOND = 1_000_000.0

        private const val VERTEX_SHADER = """
            #version 100
            attribute vec4 aFramePosition;
            uniform mat4 uTransformationMatrix;
            uniform mat4 uTexTransformationMatrix;
            varying vec2 vCanvasCoord;

            void main() {
                gl_Position = uTransformationMatrix * aFramePosition;
                vec4 canvasPosition = vec4(
                    aFramePosition.x * 0.5 + 0.5,
                    aFramePosition.y * 0.5 + 0.5,
                    0.0,
                    1.0
                );
                vCanvasCoord = canvasPosition.xy;
            }
        """

        private const val FRAGMENT_SHADER = """
            #version 100
            precision highp float;

            uniform sampler2D uTexSampler;
            uniform vec2 uSourceSize;
            uniform vec2 uTargetSize;
            uniform float uFitScale;
            uniform vec2 uPad;
            uniform float uScaleX;
            uniform float uScaleY;
            uniform float uRotationRad;
            uniform float uOffsetX;
            uniform float uOffsetY;
            uniform float uAnchorX;
            uniform float uAnchorY;
            uniform float uOffsetAfterRotation;
            uniform float uCropL;
            uniform float uCropR;
            uniform float uCropT;
            uniform float uCropB;
            uniform float uLayerOpacity;
            uniform mat4 uTexTransformationMatrix;
            varying vec2 vCanvasCoord;

            void main() {
                vec2 canvasPixels = vCanvasCoord * uTargetSize;
                vec2 anchorPixels = vec2(uAnchorX, uAnchorY) * uTargetSize;
                float cosNegR = cos(-uRotationRad);
                float sinNegR = sin(-uRotationRad);
                if (uScaleX <= 0.0 || uScaleY <= 0.0) {
                    gl_FragColor = vec4(0.0);
                    return;
                }
                vec2 unscaledPixels;
                if (uOffsetAfterRotation > 0.5) {
                    // The perspective and overlay paths apply translation after rotation.
                    vec2 centered = canvasPixels - anchorPixels -
                            vec2(uOffsetX, uOffsetY) * uTargetSize;
                    vec2 rotatedBack = vec2(
                        centered.x * cosNegR - centered.y * sinNegR,
                        centered.x * sinNegR + centered.y * cosNegR
                    );
                    unscaledPixels = anchorPixels + rotatedBack / vec2(uScaleX, uScaleY);
                } else {
                    // The ordinary FFmpeg path applies translation before rotation.
                    vec2 centered = canvasPixels - uTargetSize * 0.5;
                    vec2 rotatedBack = vec2(
                        centered.x * cosNegR - centered.y * sinNegR,
                        centered.x * sinNegR + centered.y * cosNegR
                    ) + uTargetSize * 0.5;
                    vec2 unoffsetPixels =
                            rotatedBack - vec2(uOffsetX, uOffsetY) * uTargetSize;
                    vec2 scaleCentered = unoffsetPixels - uTargetSize * 0.5;
                    unscaledPixels = scaleCentered / vec2(uScaleX, uScaleY) +
                            uTargetSize * 0.5;
                }

                // Undo the aspect-preserving fit and its centered padding.
                vec2 sourcePixels = (unscaledPixels - uPad) / uFitScale;
                vec2 srcUV = sourcePixels / uSourceSize;
                if (srcUV.x < 0.0 || srcUV.x > 1.0 ||
                    srcUV.y < 0.0 || srcUV.y > 1.0) {
                    gl_FragColor = vec4(0.0);
                    return;
                }

                // Crop bounds are canvas-space alpha bounds over the fitted source rectangle.
                if (uCropL + uCropR >= 1.0 || uCropT + uCropB >= 1.0) {
                    gl_FragColor = vec4(0.0);
                    return;
                }
                vec2 fittedSize = uSourceSize * uFitScale;
                vec2 cropMin = (uPad + vec2(uCropL, uCropT) * fittedSize) / uTargetSize;
                vec2 cropMax = (
                    uPad + vec2(1.0 - uCropR, 1.0 - uCropB) * fittedSize
                ) / uTargetSize;
                if (vCanvasCoord.x < cropMin.x || vCanvasCoord.x >= cropMax.x ||
                    vCanvasCoord.y < cropMin.y || vCanvasCoord.y >= cropMax.y) {
                    gl_FragColor = vec4(0.0);
                    return;
                }

                // Keep RGB premultiplied by alpha as opacity is applied.
                vec2 srcTextureUV = (uTexTransformationMatrix * vec4(srcUV, 0.0, 1.0)).xy;
                vec4 src = texture2D(uTexSampler, srcTextureUV);
                gl_FragColor = src * uLayerOpacity;
            }
        """
    }
}
