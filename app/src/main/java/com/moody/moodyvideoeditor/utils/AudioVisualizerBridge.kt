package com.moody.moodyvideoeditor.utils

import android.media.audiofx.Visualizer
import android.util.Log
import kotlin.math.abs
import kotlin.math.sqrt

object AudioVisualizerBridge {

    private const val TAG = "VISUALIZER"
    private const val MAX_FFT_SIZE = 4096

    private var visualizer: Visualizer? = null
    private var attachedSessionId: Int = 0
    private var fftSize: Int = 2048

    private val lock = Any()

    @Volatile
    private var latestFft: FloatArray = FloatArray(0)

    @Volatile
    private var latestWaveform: FloatArray = FloatArray(0)

    @Volatile
    private var lastFftMs: Long = 0L

    // Beat detection state
    private val energyHistory = ArrayDeque<Float>()
    private val maxHistory = 43

    @Volatile
    private var lastBeatMs: Long = 0L

    @Volatile
    private var currentBeatPulse: Float = 0f

    // 🆕 Source sample rate — assume 44100 unless Visualizer reports otherwise
    private var sampleRateHz: Int = 44100

    val isAttached: Boolean get() = visualizer != null
    val currentSessionId: Int get() = attachedSessionId
    val currentFftSize: Int get() = fftSize
    val currentSampleRateHz: Int get() = sampleRateHz

    fun attach(sessionId: Int, requestedFftSize: Int = 2048) {
        if (sessionId <= 0) return
        if (attachedSessionId == sessionId && visualizer != null) return
        release()
        try {
            val v = Visualizer(sessionId)
            val captureRange = try {
                Visualizer.getCaptureSizeRange()
            } catch (_: Throwable) {
                intArrayOf(128, 1024)
            }
            val useSize = requestedFftSize.coerceIn(
                captureRange[0].coerceAtLeast(128),
                captureRange[1].coerceAtMost(MAX_FFT_SIZE)
            )

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

            Log.e(
                TAG,
                "✅ Attached: session=$sessionId, fftSize=$useSize, sampleRate=$sampleRateHz"
            )
        } catch (e: Throwable) {
            Log.e(TAG, "❌ Attach failed: ${e.message}", e)
        }
    }

    // ─── BEAT DETECTION — Energy-based with adaptive threshold

    private fun detectBeat(fft: FloatArray): Float {
        if (fft.isEmpty()) return 0f

        val bassEnd = (fft.size * 0.05f).toInt().coerceAtLeast(1)
        var bassSum = 0f
        for (i in 0 until bassEnd) bassSum += fft[i]
        val bassEnergy = bassSum / bassEnd

        val avg = if (energyHistory.isEmpty()) bassEnergy
        else energyHistory.average().toFloat()

        val variance = if (energyHistory.size > 1) {
            energyHistory.map { (it - avg) * (it - avg) }.average().toFloat()
        } else 0f

        val threshold = ((-0.0025714f * variance) + 1.5142857f)
            .coerceIn(1.2f, 2.0f)

        energyHistory.addLast(bassEnergy)
        if (energyHistory.size > maxHistory) energyHistory.removeFirst()

        val now = System.currentTimeMillis()
        val isBeat = bassEnergy > threshold * avg && (now - lastBeatMs) > 180L

        if (isBeat) {
            lastBeatMs = now
            val strength = ((bassEnergy / (avg + 0.001f)) - 1f).coerceIn(0f, 1f)
            return strength
        }

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

    // ─── 🆕 PHASE 1 HELPERS ───────────────────────────────────

    /**
     * Sample FFT into configurable number of bands between start/end Hz.
     * FFT bin → Hz: binIndex * sampleRate / fftSize
     */
    fun sampleBands(
        fft: FloatArray,
        bands: Int,
        startHz: Float,
        endHz: Float,
        sampleRate: Int = sampleRateHz
    ): FloatArray {
        if (fft.isEmpty() || bands <= 0) return FloatArray(0)

        val n = fft.size * 2       // fft.size = bins, total samples = bins * 2
        val hzPerBin = sampleRate.toFloat() / n
        val startBin = (startHz / hzPerBin).toInt().coerceIn(0, fft.size - 1)
        val endBin = (endHz / hzPerBin).toInt().coerceIn(startBin + 1, fft.size)

        val out = FloatArray(bands)
        val step = (endBin - startBin).toFloat() / bands

        for (i in 0 until bands) {
            val binStart = (startBin + i * step).toInt().coerceIn(0, fft.size - 1)
            val binEnd = (startBin + (i + 1) * step).toInt().coerceIn(binStart + 1, fft.size)

            var sum = 0f
            var count = 0
            for (b in binStart until binEnd) {
                sum += fft[b]
                count++
            }
            out[i] = if (count > 0) (sum / count).coerceIn(0f, 1f) else 0f
        }
        return out
    }

    /**
     * Average waveform over a window (in samples).
     * Used for "Audio Duration (ms)" — longer window = smoother.
     */
    fun averageWaveform(waveform: FloatArray, windowSamples: Int): FloatArray {
        if (waveform.isEmpty() || windowSamples <= 1) return waveform
        val out = FloatArray(waveform.size)
        val half = windowSamples / 2
        for (i in waveform.indices) {
            val lo = (i - half).coerceAtLeast(0)
            val hi = (i + half).coerceAtMost(waveform.size - 1)
            var sum = 0f
            for (j in lo..hi) sum += abs(waveform[j])
            out[i] = (sum / (hi - lo + 1)).coerceIn(0f, 1f)
        }
        return out
    }
}