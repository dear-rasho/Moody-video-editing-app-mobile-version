package com.moody.moodyvideoeditor.utils

object VisualizerNative {
    init {
        System.loadLibrary("moodywhisper")
    }

    external fun nativeAnalyzePcm(
        pcm: ShortArray,
        fftSize: Int,
        hopSize: Int,
        bandCount: Int
    ): FloatArray?
    external fun nativeBassEnergy(bands: FloatArray): Float

    external fun nativeCreateProcessor(bandCount: Int): Long
    external fun nativeSmoothBands(handle: Long, bands: FloatArray, smoothing: Float): FloatArray?
    external fun nativeResetProcessor(handle: Long)
    external fun nativeReleaseProcessor(handle: Long)
    external fun nativeGenerateGeometry(
        bands: FloatArray,
        layout: Int,
        scale: Float,
        phase: Float
    ): FloatArray?

    external fun nativeCreateGlRenderer(): Long
    external fun nativeInitializeGlRenderer(handle: Long): Boolean
    external fun nativeResizeGlRenderer(handle: Long, width: Int, height: Int)
    external fun nativeDrawGlRenderer(
        handle: Long,
        bands: FloatArray,
        layout: Int,
        positionX: Float,
        positionY: Float,
        scale: Float,
        primaryColor: Long,
        accentColor: Long,
        ringColors: LongArray,
        glow: Float,
        opacity: Float,
        phase: Float
    )

    external fun nativeDestroyGlRenderer(handle: Long)
}
