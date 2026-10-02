package com.moody.moodyvideoeditor.utils

import android.media.audiofx.Visualizer
import android.util.Log
import kotlin.math.sqrt

object AudioVisualizerBridge {

    private const val TAG = "VISUALIZER"

    private var visualizer: Visualizer? = null
    private var attachedSessionId: Int = 0
    private var fftSize: Int = 1024

    private val lock = Any()

    @Volatile
    private var latestFft: FloatArray = FloatArray(0)
    @Volatile
    private var latestWaveform: FloatArray = FloatArray(0)
    @Volatile
    private var lastFftMs: Long = 0L

    // 🆕 Beat detection state
    private val energyHistory = ArrayDeque<Float>()
    private val maxHistory = 43  // ~1 second at 23 fps
    @Volatile
    private var lastBeatMs: Long = 0L
    @Volatile
    private var currentBeatPulse: Float = 0f

    val isAttached: Boolean get() = visualizer != null
    val currentSessionId: Int get() = attachedSessionId

    fun attach(sessionId: Int, fftSize: Int = 1024) {
        if (sessionId <= 0) return
        if (attachedSessionId == sessionId && visualizer != null) return
        release()
        try {
            this.fftSize = fftSize
            val v = Visualizer(sessionId)
            val captureRange = try {
                Visualizer.getCaptureSizeRange()
            } catch (_: Throwable) {
                intArrayOf(128, 1024)
            }
            val useSize = fftSize.coerceIn(captureRange[0], captureRange[1])

            v.scalingMode = Visualizer.SCALING_MODE_NORMALIZED
            v.measurementMode = Visualizer.MEASUREMENT_MODE_PEAK_RMS
            v.captureSize = useSize
            this.fftSize = useSize

            val fftBuf = ByteArray(useSize)
            val captureRate = try {
                Visualizer.getMaxCaptureRate() / 2
            } catch (_: Throwable) {
                10000
            }

            v.setDataCaptureListener(
                object : Visualizer.OnDataCaptureListener {

                    override fun onWaveFormDataCapture(
                        vis: Visualizer?, waveform: ByteArray?, sr: Int
                    ) {
                        if (waveform == null) return
                        val n = minOf(waveform.size, fftBuf.size)
                        val out = FloatArray(n)
                        for (i in 0 until n) {
                            out[i] = ((waveform[i].toInt() and 0xFF) - 128) / 128f
                        }
                        synchronized(lock) { latestWaveform = out }
                    }

                    override fun onFftDataCapture(
                        vis: Visualizer?, fft: ByteArray?, sr: Int
                    ) {
                        if (fft == null) return
                        val n = minOf(fft.size, fftBuf.size)
                        System.arraycopy(fft, 0, fftBuf, 0, n)

                        val bins = n / 2
                        val out = FloatArray(bins)
                        for (i in 0 until bins) {
                            val re = fftBuf[i * 2].toFloat()
                            val im = fftBuf[i * 2 + 1].toFloat()
                            val mag = sqrt(re * re + im * im) / 128f
                            out[i] = mag.coerceIn(0f, 1f)
                        }

                        // 🆕 Compute beat detection here
                        val beatResult = detectBeat(out)

                        synchronized(lock) {
                            latestFft = out
                            lastFftMs = System.currentTimeMillis()
                            currentBeatPulse = beatResult
                        }
                    }
                },
                captureRate,
                true,
                true
            )

            v.enabled = true
            visualizer = v
            attachedSessionId = sessionId

            Log.e(TAG, "✅ Attached: session=$sessionId, size=$useSize")
        } catch (e: Throwable) {
            Log.e(TAG, "❌ Attach failed: ${e.message}", e)
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  BEAT DETECTION — Energy-based with adaptive threshold
    // ═══════════════════════════════════════════════════════════
    private fun detectBeat(fft: FloatArray): Float {
        if (fft.isEmpty()) return 0f

        // Bass region = first 5% of bins
        val bassEnd = (fft.size * 0.05f).toInt().coerceAtLeast(1)
        var bassSum = 0f
        for (i in 0 until bassEnd) bassSum += fft[i]
        val bassEnergy = bassSum / bassEnd

        // Rolling average
        val avg = if (energyHistory.isEmpty()) bassEnergy
        else energyHistory.average().toFloat()

        // Variance
        val variance = if (energyHistory.size > 1) {
            energyHistory.map { (it - avg) * (it - avg) }.average().toFloat()
        } else 0f

        // Adaptive threshold
        val threshold = ((-0.0025714f * variance) + 1.5142857f)
            .coerceIn(1.2f, 2.0f)

        // Add to history
        energyHistory.addLast(bassEnergy)
        if (energyHistory.size > maxHistory) energyHistory.removeFirst()

        // Beat detection
        val now = System.currentTimeMillis()
        val isBeat = bassEnergy > threshold * avg && (now - lastBeatMs) > 180L

        if (isBeat) {
            lastBeatMs = now
            // Beat strength = how much above threshold
            val strength = ((bassEnergy / (avg + 0.001f)) - 1f).coerceIn(0f, 1f)
            return strength
        }

        // Decay pulse over time
        val elapsed = (now - lastBeatMs).toFloat() / 1000f
        return (currentBeatPulse * (1f - elapsed * 3f)).coerceAtLeast(0f)
    }

    fun release() {
        try {
            visualizer?.enabled = false
            visualizer?.release()
        } catch (_: Throwable) {
        }
        visualizer = null
        attachedSessionId = 0
        latestFft = FloatArray(0)
        latestWaveform = FloatArray(0)
        energyHistory.clear()
        lastBeatMs = 0L
        currentBeatPulse = 0f
    }

    fun getFft(): FloatArray = synchronized(lock) { latestFft }
    fun getWaveform(): FloatArray = synchronized(lock) { latestWaveform }
    fun getBeatPulse(): Float = synchronized(lock) { currentBeatPulse }
    fun isFresh(): Boolean = (System.currentTimeMillis() - lastFftMs) < 500L
}