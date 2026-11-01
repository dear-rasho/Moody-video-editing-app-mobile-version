package com.moody.moodyvideoeditor.ui.components

import android.content.Context
import android.graphics.PixelFormat
import android.opengl.GLSurfaceView
import android.view.MotionEvent
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import com.moody.moodyvideoeditor.data.VisualizerState
import com.moody.moodyvideoeditor.utils.VisualizerNative
import java.util.concurrent.atomic.AtomicBoolean
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class VisualizerGLSurfaceView(context: Context) : GLSurfaceView(context) {
    private val renderer = VisualizerRenderer()
    @Volatile
    private var currentState = VisualizerState()
    private var gestureState = currentState
    private var downX = 0f
    private var downY = 0f
    private var initialDistance = 0f
    var onVisualizerChanged: ((VisualizerState) -> Unit)? = null
    var onSelected: (() -> Unit)? = null

    init {
        setEGLContextClientVersion(3)
        setEGLConfigChooser(8, 8, 8, 8, 0, 0)
        holder.setFormat(PixelFormat.TRANSLUCENT)
        setZOrderMediaOverlay(true)
        preserveEGLContextOnPause = false
        setRenderer(renderer)
        renderMode = RENDERMODE_WHEN_DIRTY
        isClickable = true
        setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.x
                    downY = event.y
                    gestureState = currentState
                    onSelected?.invoke()
                    true
                }

                MotionEvent.ACTION_POINTER_UP -> {
                    initialDistance = 0f
                    gestureState = currentState
                    val remaining = if (event.actionIndex == 0) 1 else 0
                    downX = event.getX(remaining)
                    downY = event.getY(remaining)
                    true
                }

                MotionEvent.ACTION_POINTER_DOWN -> {
                    if (event.pointerCount >= 2) {
                        initialDistance = distance(event)
                        gestureState = currentState
                    }
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val next = if (event.pointerCount >= 2 && initialDistance > 0f) {
                        currentState.copy(
                            size = (gestureState.size * distance(event) / initialDistance)
                                .coerceIn(0.05f, 0.8f)
                        )
                    } else {
                        currentState.copy(
                            positionX = (gestureState.positionX +
                                    (event.x - downX) / width.coerceAtLeast(1))
                                .coerceIn(0f, 1f),
                            positionY = (gestureState.positionY +
                                    (event.y - downY) / height.coerceAtLeast(1))
                                .coerceIn(0f, 1f)
                        )
                    }
                    currentState = next
                    onVisualizerChanged?.invoke(next)
                    true
                }

                MotionEvent.ACTION_UP -> {
                    initialDistance = 0f
                    performClick()
                    true
                }

                MotionEvent.ACTION_CANCEL -> {
                    initialDistance = 0f
                    true
                }

                else -> true
            }
        }
    }

    fun updateFrame(
        spectrum: FloatArray?,
        state: VisualizerState,
        opacity: Float,
        isPlaying: Boolean,
        phase: Float
    ) {
        currentState = state
        renderer.update(spectrum, state, opacity, phase)
        renderMode = if (isPlaying) RENDERMODE_CONTINUOUSLY else RENDERMODE_WHEN_DIRTY
        requestRender()
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun distance(event: MotionEvent): Float {
        val dx = event.getX(0) - event.getX(1)
        val dy = event.getY(0) - event.getY(1)
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }

    fun release() {
        val released = CountDownLatch(1)
        queueEvent {
            renderer.release()
            released.countDown()
        }
        try {
            released.await(300, TimeUnit.MILLISECONDS)
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        onPause()
    }

    private class VisualizerRenderer : Renderer {
        @Volatile
        private var spectra = FloatArray(VisualizerSpectrumSize.BANDS)

        @Volatile
        private var state = VisualizerState()

        @Volatile
        private var opacity = 1f

        @Volatile
        private var phase = 0f

        @Volatile
        private var ringColors = LongArray(5) { state.color1 }

        private var rendererHandle = 0L
        private var smoothingHandle = 0L
        private val initialized = AtomicBoolean(false)

        fun update(
            spectrum: FloatArray?,
            newState: VisualizerState,
            alpha: Float,
            animationPhase: Float
        ) {
            spectra = spectrum?.copyOf() ?: FloatArray(VisualizerSpectrumSize.BANDS)
            ringColors = LongArray(5) {
                if (it % 2 == 0) newState.color1 else newState.color2
            }
            state = newState
            opacity = alpha.coerceIn(0f, 1f)
            phase = animationPhase
        }

        override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
            if (rendererHandle == 0L) rendererHandle = VisualizerNative.nativeCreateGlRenderer()
            if (smoothingHandle == 0L) {
                smoothingHandle = VisualizerNative.nativeCreateProcessor(VisualizerSpectrumSize.BANDS)
            }
            if (rendererHandle != 0L) {
                initialized.set(VisualizerNative.nativeInitializeGlRenderer(rendererHandle))
            }
        }

        override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
            if (rendererHandle != 0L) {
                VisualizerNative.nativeResizeGlRenderer(rendererHandle, width, height)
            }
        }

        override fun onDrawFrame(gl: GL10?) {
            if (rendererHandle == 0L || smoothingHandle == 0L || !initialized.get()) return
            val currentState = state
            val smoothed = VisualizerNative.nativeSmoothBands(
                smoothingHandle,
                spectra,
                currentState.smoothing
            ) ?: return
            val pulseScale = 1f + VisualizerNative.nativeBassEnergy(spectra) *
                    0.22f * currentState.sensitivity
            val layout = when (currentState.preset) {
                com.moody.moodyvideoeditor.data.VisualizerPreset.LINEAR_WAVEFORM,
                com.moody.moodyvideoeditor.data.VisualizerPreset.SINE_WAVE -> 4
                com.moody.moodyvideoeditor.data.VisualizerPreset.PERSPECTIVE_3D -> 5
                com.moody.moodyvideoeditor.data.VisualizerPreset.RADIAL_BARS,
                com.moody.moodyvideoeditor.data.VisualizerPreset.STAR_BURST -> 2
                else -> 3
            }
            VisualizerNative.nativeDrawGlRenderer(
                rendererHandle,
                smoothed,
                layout,
                currentState.positionX,
                currentState.positionY,
                (currentState.size / 0.32f * pulseScale).coerceIn(0.1f, 4f),
                currentState.color1,
                currentState.color2,
                ringColors,
                if (currentState.glow) currentState.glowIntensity.coerceIn(0f, 5f) else 0f,
                opacity,
                phase
            )
        }

        fun release() {
            if (rendererHandle != 0L) {
                VisualizerNative.nativeDestroyGlRenderer(rendererHandle)
                rendererHandle = 0L
            }
            if (smoothingHandle != 0L) {
                VisualizerNative.nativeReleaseProcessor(smoothingHandle)
                smoothingHandle = 0L
            }
            initialized.set(false)
        }
    }
}

private object VisualizerSpectrumSize {
    const val BANDS = 64
}
