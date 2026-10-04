package com.moody.moodyvideoeditor.utils

import android.media.audiofx.Visualizer
import android.util.Log
import kotlin.math.max
import kotlin.math.min
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

    private val lock = Any()

    private var latestFft: FloatArray = FloatArray(0)
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

    val isAttached: Boolean get() = visualizer != null
    val currentSessionId: Int get() = attachedSessionId

    fun attach(sessionId: Int, fftSize: Int = DEFAULT_FFT_SIZE) {
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
            this.fftSize = useSize

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

            Log.e(TAG, "✅ Attached: session=$sessionId, size=$useSize")
        } catch (e: Throwable) {
            Log.e(TAG, "❌ Attach failed: ${e.message}", e)
            visualizer = null
            attachedSessionId = 0
        }
    }

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
     */
    fun sampleBands(
        fft: FloatArray,
        bands: Int,
        startHz: Float,
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
    }
}