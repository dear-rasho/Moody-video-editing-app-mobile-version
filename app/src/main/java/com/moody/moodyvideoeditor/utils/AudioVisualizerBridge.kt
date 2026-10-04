package com.moody.moodyvideoeditor.utils

import android.media.audiofx.Visualizer
import android.util.Log
<<<<<<< HEAD
import kotlin.math.max
import kotlin.math.min
=======
import kotlin.math.abs
>>>>>>> 5681a8706659a5f06557a27780f8683a58525bbd
import kotlin.math.sqrt

/**
 * AudioVisualizerBridge — real-time FFT + waveform + beat detection.
 *
 * ═══════════════════════════════════════════════════════════════
 *  DATA FLOW
 * ═══════════════════════════════════════════════════════════════
 *  Visualizer (system)
 *      ↓
 *  FFT + Waveform (bytes)
 *      ↓
 *  this bridge
 *      ↓
 *  getFft() / getWaveform()
 *  getBass() / getMid() / getTreble() / getRms() / getBeat()
 *  sampleBands(fft, bands, startHz, endHz)
 *      ↓
 *  VisualizerEngine
 *      ↓
 *  Canvas
 */
object AudioVisualizerBridge {

<<<<<<< HEAD
    private const val TAG = "AudioVizBridge"
    private const val DEFAULT_FFT_SIZE = 1024

    private const val BASS_BIN_END = 6
    private const val MID_BIN_END = 46
    private const val TREBLE_BIN_END = 280

    private const val PEAK_DECAY_PER_SEC = 0.9f

    private const val BEAT_HISTORY_SIZE = 43
    private const val BEAT_MIN_INTERVAL_MS = 180L
    private const val BEAT_THRESHOLD_MIN = 1.15f
    private const val BEAT_THRESHOLD_MAX = 2.20f

    private var visualizer: Visualizer? = null
    private var attachedSessionId: Int = 0
    private var fftSize: Int = DEFAULT_FFT_SIZE
=======
    private const val TAG = "VISUALIZER"
    private const val MAX_FFT_SIZE = 4096

    private var visualizer: Visualizer? = null
    private var attachedSessionId: Int = 0
    private var fftSize: Int = 2048
>>>>>>> 5681a8706659a5f06557a27780f8683a58525bbd

    private val lock = Any()

    private var latestFft: FloatArray = FloatArray(0)
<<<<<<< HEAD
    private var latestWaveform: FloatArray = FloatArray(0)

    @Volatile
    private var bassLevel: Float = 0f
    @Volatile
    private var midLevel: Float = 0f
    @Volatile
    private var trebleLevel: Float = 0f
    @Volatile
    private var rmsLevel: Float = 0f
    @Volatile
    private var overallEnergy: Float = 0f
    @Volatile
    private var peakLevel: Float = 0f

    private val energyHistory = ArrayDeque<Float>(BEAT_HISTORY_SIZE)
=======

    @Volatile
    private var latestWaveform: FloatArray = FloatArray(0)

    @Volatile
    private var lastFftMs: Long = 0L

    // Beat detection state
    private val energyHistory = ArrayDeque<Float>()
    private val maxHistory = 43

>>>>>>> 5681a8706659a5f06557a27780f8683a58525bbd
    @Volatile
    private var lastBeatMs: Long = 0L

    @Volatile
    private var currentBeatPulse: Float = 0f
    @Volatile
    private var lastBeatStrength: Float = 0f

    @Volatile
    private var lastFftMs: Long = 0L
    @Volatile
    private var lastFrameMs: Long = 0L

    // 🆕 Source sample rate — assume 44100 unless Visualizer reports otherwise
    private var sampleRateHz: Int = 44100

    val isAttached: Boolean get() = visualizer != null
    val currentSessionId: Int get() = attachedSessionId
    val currentFftSize: Int get() = fftSize
    val currentSampleRateHz: Int get() = sampleRateHz

<<<<<<< HEAD
    fun attach(sessionId: Int, fftSize: Int = DEFAULT_FFT_SIZE) {
=======
    fun attach(sessionId: Int, requestedFftSize: Int = 2048) {
>>>>>>> 5681a8706659a5f06557a27780f8683a58525bbd
        if (sessionId <= 0) return
        if (attachedSessionId == sessionId && visualizer != null) return
        release()

        try {
<<<<<<< HEAD
            this.fftSize = fftSize

=======
>>>>>>> 5681a8706659a5f06557a27780f8683a58525bbd
            val v = Visualizer(sessionId)
            val captureRange = try {
                Visualizer.getCaptureSizeRange()
            } catch (_: Throwable) {
                intArrayOf(128, 1024)
            }
<<<<<<< HEAD
            val useSize = fftSize.coerceIn(captureRange[0], captureRange[1])
            this.fftSize = useSize
=======
            val useSize = requestedFftSize.coerceIn(
                captureRange[0].coerceAtLeast(128),
                captureRange[1].coerceAtMost(MAX_FFT_SIZE)
            )
>>>>>>> 5681a8706659a5f06557a27780f8683a58525bbd

            v.scalingMode = Visualizer.SCALING_MODE_NORMALIZED
            v.measurementMode = Visualizer.MEASUREMENT_MODE_PEAK_RMS
            v.captureSize = useSize

            val scratch = ByteArray(useSize)
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
                        val n = min(waveform.size, scratch.size)
                        val out = FloatArray(n)
                        var sumSq = 0f
                        for (i in 0 until n) {
                            val f = ((waveform[i].toInt() and 0xFF) - 128) / 128f
                            out[i] = f
                            sumSq += f * f
                        }
                        val rms = if (n > 0) sqrt(sumSq / n) else 0f

                        synchronized(lock) {
                            latestWaveform = out
                            rmsLevel = rms.coerceIn(0f, 1f)
                        }
                    }

                    override fun onFftDataCapture(
                        vis: Visualizer?, fft: ByteArray?, sr: Int
                    ) {
                        if (fft == null) return
                        val n = min(fft.size, scratch.size)
                        System.arraycopy(fft, 0, scratch, 0, n)

                        val bins = n / 2
                        if (bins <= 0) return

                        val mags = FloatArray(bins)
                        for (i in 0 until bins) {
                            val re = scratch[i * 2].toFloat()
                            val im = scratch[i * 2 + 1].toFloat()
                            val mag = sqrt(re * re + im * im) / 128f
                            mags[i] = if (mag < 0.0001f) 0f else mag.coerceAtMost(1f)
                        }

<<<<<<< HEAD
                        val bassRaw = bandAverage(mags, 0, BASS_BIN_END)
                        val midRaw = bandAverage(mags, BASS_BIN_END, MID_BIN_END)
                        val trebleRaw = bandAverage(mags, MID_BIN_END, TREBLE_BIN_END)
                        val energyRaw = (bassRaw + midRaw + trebleRaw) / 3f

                        val now = System.currentTimeMillis()
                        val dtSec = if (lastFrameMs == 0L) 0.016f
                        else ((now - lastFrameMs) / 1000f).coerceIn(0.001f, 0.1f)

                        val newBass = smooth(bassRaw, bassLevel)
                        val newMid = smooth(midRaw, midLevel)
                        val newTreble = smooth(trebleRaw, trebleLevel)
                        val newEnergy = smooth(energyRaw, overallEnergy)

                        val decayedPeak = (peakLevel - PEAK_DECAY_PER_SEC * dtSec)
                            .coerceAtLeast(0f)
                        val newPeak = max(decayedPeak, newEnergy)

                        val beatPulse = detectBeat(bassRaw, now)
=======
                        val beatResult = detectBeat(out)
>>>>>>> 5681a8706659a5f06557a27780f8683a58525bbd

                        synchronized(lock) {
                            latestFft = mags
                            lastFftMs = now
                            lastFrameMs = now

                            bassLevel = newBass
                            midLevel = newMid
                            trebleLevel = newTreble
                            overallEnergy = newEnergy
                            peakLevel = newPeak
                            currentBeatPulse = beatPulse
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
            visualizer = null
            attachedSessionId = 0
        }
    }

<<<<<<< HEAD
=======
    //  BEAT DETECTION — Energy-based with adaptive threshold

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

>>>>>>> 5681a8706659a5f06557a27780f8683a58525bbd
    fun release() {
        try {
            visualizer?.enabled = false
            visualizer?.release()
        } catch (_: Throwable) {
        }
        visualizer = null
        attachedSessionId = 0

        synchronized(lock) {
            latestFft = FloatArray(0)
            latestWaveform = FloatArray(0)
            bassLevel = 0f
            midLevel = 0f
            trebleLevel = 0f
            overallEnergy = 0f
            rmsLevel = 0f
            peakLevel = 0f
            energyHistory.clear()
            lastBeatMs = 0L
            currentBeatPulse = 0f
            lastBeatStrength = 0f
            lastFftMs = 0L
            lastFrameMs = 0L
        }
    }

    fun getFft(): FloatArray = synchronized(lock) { latestFft }
    fun getWaveform(): FloatArray = synchronized(lock) { latestWaveform }
    fun getBeatPulse(): Float = synchronized(lock) { currentBeatPulse }

    fun isFresh(): Boolean = (System.currentTimeMillis() - lastFftMs) < 500L

<<<<<<< HEAD
    fun getBass(): Float = bassLevel
    fun getMid(): Float = midLevel
    fun getTreble(): Float = trebleLevel
    fun getRms(): Float = rmsLevel
    fun getEnergy(): Float = overallEnergy
    fun getPeak(): Float = peakLevel
    fun getBeatStrength(): Float = lastBeatStrength

    fun isBeatActive(): Boolean =
        (System.currentTimeMillis() - lastBeatMs) < 120L

    /**
     * Resamples FFT into `bands` slots between `startHz` and `endHz`.
     * Uses logarithmic mapping for natural music feel.
     * Assumes 44.1 kHz sample rate.
=======
    // ─── 🆕 PHASE 1 HELPERS ───────────────────────────────────

    /**
     * Sample FFT into configurable number of bands between start/end Hz.
     * FFT bin → Hz: binIndex * sampleRate / fftSize
>>>>>>> 5681a8706659a5f06557a27780f8683a58525bbd
     */
    fun sampleBands(
        fft: FloatArray,
        bands: Int,
        startHz: Float,
<<<<<<< HEAD
        endHz: Float
    ): FloatArray {
        val safeBands = bands.coerceIn(1, 6400)
        if (fft.isEmpty()) return FloatArray(safeBands)

        val sampleRate = 44100f
        val binCount = fft.size
        val hzPerBin = (sampleRate / 2f) / binCount

        val startBinF = (startHz / hzPerBin).coerceIn(0f, (binCount - 1).toFloat())
        val endBinF = (endHz / hzPerBin).coerceIn(
            (startBinF + 1f),
            binCount.toFloat()
        )

        val result = FloatArray(safeBands)

        val logStart = kotlin.math.ln(startBinF.coerceAtLeast(1f))
        val logEnd = kotlin.math.ln(endBinF.coerceAtLeast(startBinF + 1f))
        val logRange = (logEnd - logStart).coerceAtLeast(0.0001f)

        for (i in 0 until safeBands) {
            val t0 = i.toFloat() / safeBands
            val t1 = (i + 1).toFloat() / safeBands

            val binStartF = kotlin.math.exp(logStart + logRange * t0)
            val binEndF = kotlin.math.exp(logStart + logRange * t1)

            val binStart = binStartF.toInt().coerceIn(0, binCount - 1)
            val binEnd = binEndF.toInt().coerceIn(binStart + 1, binCount)

            var sum = 0f
            var count = 0
            for (j in binStart until binEnd) {
                sum += fft[j]
                count++
            }
            result[i] = if (count > 0) (sum / count).coerceIn(0f, 1f) else 0f
        }

        return result
    }

    private fun bandAverage(mags: FloatArray, start: Int, end: Int): Float {
        if (start >= end || start >= mags.size) return 0f
        val e = min(end, mags.size)
        var sum = 0f
        for (i in start until e) sum += mags[i]
        val count = e - start
        return if (count > 0) (sum / count) else 0f
    }

    private fun smooth(target: Float, current: Float): Float {
        val k = if (target > current) 0.35f else 0.15f
        return current + (target - current) * k
    }

    private fun detectBeat(bassEnergy: Float, now: Long): Float {
        val avg = if (energyHistory.isEmpty()) bassEnergy
        else energyHistory.average().toFloat()

        val variance = if (energyHistory.size > 1) {
            var v = 0f
            for (e in energyHistory) {
                val d = e - avg
                v += d * d
            }
            v / energyHistory.size
        } else 0f

        val threshold = (-0.0025714f * variance + 1.5142857f)
            .coerceIn(BEAT_THRESHOLD_MIN, BEAT_THRESHOLD_MAX)

        energyHistory.addLast(bassEnergy)
        if (energyHistory.size > BEAT_HISTORY_SIZE) energyHistory.removeFirst()

        val isBeat = bassEnergy > threshold * avg &&
                (now - lastBeatMs) > BEAT_MIN_INTERVAL_MS

        if (isBeat) {
            lastBeatMs = now
            val strength = ((bassEnergy / (avg + 0.0001f)) - 1f).coerceIn(0f, 1f)
            lastBeatStrength = strength
            currentBeatPulse = 1f
            return 1f
        }

        val elapsed = (now - lastBeatMs).toFloat() / 300f
        currentBeatPulse = (currentBeatPulse * (1f - elapsed * 0.9f))
            .coerceAtLeast(0f)

        return currentBeatPulse
=======
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
>>>>>>> 5681a8706659a5f06557a27780f8683a58525bbd
    }
}