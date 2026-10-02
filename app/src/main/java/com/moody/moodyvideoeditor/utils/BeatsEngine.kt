package com.moody.moodyvideoeditor.utils

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.util.Log
import com.moody.moodyvideoeditor.data.BeatsState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

object BeatsEngine {

    private const val TAG = "BEATS"

    // Decoding targets
    private const val TARGET_SAMPLE_RATE = 22050
    private const val MAX_DURATION_MS = 5 * 60 * 1000L

    // Analysis params
    private const val HOP_SIZE_MS = 10L
    private const val MIN_BEAT_GAP_MS = 250L
    private const val LOWPASS_ALPHA = 0.08f

    // Classification
    private const val HARD_PERCENTILE = 0.75f
    private const val SOFT_PERCENTILE = 0.25f

    // Speech rejection
    private const val MIN_BEATS = 12
    private const val MUSICAL_GAP_MIN_MS = 250L
    private const val MUSICAL_GAP_MAX_MS = 1200L
    private const val MIN_MUSICAL_GAP_RATIO = 0.65f
    private const val MIN_BPM_CONSISTENCY = 0.65f

    suspend fun detect(
        context: Context,
        uri: Uri,
        filter: String = "all",
        onProgress: (Float) -> Unit = {}
    ): BeatsState = withContext(Dispatchers.Default) {
        try {
            onProgress(0.05f)

            val samples = decodeAudioToMonoFloat(context, uri, onProgress)
            if (samples.isEmpty()) {
                Log.e(TAG, "No audio samples decoded")
                return@withContext BeatsState()
            }

            onProgress(0.75f)

            val (beatTimesMs, beatStrengths) = analyzeBeatsWithStrengths(samples, filter)
            onProgress(1f)

            BeatsState(
                detected = beatTimesMs.isNotEmpty(),
                count = beatTimesMs.size,
                filter = filter,
                beatTimesMs = beatTimesMs,
                beatStrengths = beatStrengths
            )
        } catch (e: Throwable) {
            Log.e(TAG, "Detection failed", e)
            BeatsState()
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  DECODE
    // ═══════════════════════════════════════════════════════════
    private fun decodeAudioToMonoFloat(
        context: Context,
        uri: Uri,
        onProgress: (Float) -> Unit
    ): FloatArray {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, uri, null)
        } catch (e: Throwable) {
            Log.e(TAG, "Cannot open source: $uri", e)
            extractor.release()
            return FloatArray(0)
        }

        var audioTrackIdx = -1
        var audioFormat: MediaFormat? = null
        for (i in 0 until extractor.trackCount) {
            val fmt = extractor.getTrackFormat(i)
            val mime = fmt.getString(MediaFormat.KEY_MIME) ?: ""
            if (mime.startsWith("audio/")) {
                audioTrackIdx = i
                audioFormat = fmt
                break
            }
        }

        if (audioTrackIdx < 0 || audioFormat == null) {
            Log.e(TAG, "No audio track found")
            extractor.release()
            return FloatArray(0)
        }

        extractor.selectTrack(audioTrackIdx)

        val mime = audioFormat.getString(MediaFormat.KEY_MIME) ?: ""
        val srcSampleRate = if (audioFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE))
            audioFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE) else 44100
        val srcChannels = if (audioFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT))
            audioFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else 2

        val codec = MediaCodec.createDecoderByType(mime)

        try {
            codec.configure(audioFormat, null, null, 0)
            codec.start()
        } catch (e: Throwable) {
            Log.e(TAG, "Codec config failed", e)
            codec.release()
            extractor.release()
            return FloatArray(0)
        }

        val outputSamples = ArrayList<Float>(srcSampleRate * 30)
        val bufferInfo = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false
        var decodedFrames = 0L
        val maxFrames = (srcSampleRate.toLong() * MAX_DURATION_MS / 1000L)

        try {
            while (!outputDone) {
                if (!inputDone) {
                    val inIdx = codec.dequeueInputBuffer(10_000)
                    if (inIdx >= 0) {
                        val inBuf = codec.getInputBuffer(inIdx)!!
                        val sampleSize = extractor.readSampleData(inBuf, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(
                                inIdx, 0, 0, 0,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            inputDone = true
                        } else {
                            val pts = extractor.sampleTime
                            codec.queueInputBuffer(inIdx, 0, sampleSize, pts, 0)
                            extractor.advance()
                        }
                    }
                }

                val outIdx = codec.dequeueOutputBuffer(bufferInfo, 10_000)
                if (outIdx >= 0) {
                    val outBuf = codec.getOutputBuffer(outIdx)!!
                    if (bufferInfo.size > 0) {
                        outBuf.position(bufferInfo.offset)
                        outBuf.limit(bufferInfo.offset + bufferInfo.size)

                        val shortBuf = outBuf.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                        val shortArray = ShortArray(shortBuf.remaining())
                        shortBuf.get(shortArray)

                        if (srcChannels >= 2) {
                            var i = 0
                            while (i < shortArray.size - 1) {
                                val l = shortArray[i].toFloat() / 32768f
                                val r = shortArray[i + 1].toFloat() / 32768f
                                outputSamples.add((l + r) * 0.5f)
                                i += 2
                                decodedFrames++
                            }
                        } else {
                            for (s in shortArray) {
                                outputSamples.add(s.toFloat() / 32768f)
                                decodedFrames++
                            }
                        }

                        if (decodedFrames >= maxFrames) {
                            outputDone = true
                        } else if (decodedFrames % 50000 < shortArray.size) {
                            val frac = (decodedFrames.toFloat() / maxFrames)
                                .coerceIn(0f, 0.7f)
                            onProgress(0.05f + frac)
                        }
                    }
                    codec.releaseOutputBuffer(outIdx, false)

                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        outputDone = true
                    }
                } else if (outIdx == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    // continue
                }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Decode loop error", e)
        } finally {
            try {
                codec.stop()
            } catch (_: Throwable) {
            }
            try {
                codec.release()
            } catch (_: Throwable) {
            }
            try {
                extractor.release()
            } catch (_: Throwable) {
            }
        }

        // Down-sample
        return if (srcSampleRate == TARGET_SAMPLE_RATE) {
            outputSamples.toFloatArray()
        } else {
            val ratio = srcSampleRate.toFloat() / TARGET_SAMPLE_RATE
            val newLen = (outputSamples.size / ratio).toInt().coerceAtLeast(1)
            val out = FloatArray(newLen)
            for (i in 0 until newLen) {
                val srcIdx = (i * ratio).toInt().coerceIn(0, outputSamples.size - 1)
                out[i] = outputSamples[srcIdx]
            }
            out
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  ANALYZE — returns (times, strengths)
    // ═══════════════════════════════════════════════════════════
    private fun analyzeBeatsWithStrengths(
        samples: FloatArray,
        filter: String
    ): Pair<List<Long>, List<Float>> {
        if (samples.isEmpty()) return emptyList<Long>() to emptyList<Float>()

        val samplesPerHop = (TARGET_SAMPLE_RATE * HOP_SIZE_MS / 1000L).toInt()
            .coerceAtLeast(1)
        val numHops = samples.size / samplesPerHop
        if (numHops < 20) return emptyList<Long>() to emptyList<Float>()

        // ─── Energy envelope (bass-focused) ───
        val energy = FloatArray(numHops)
        var lowpass = 0f
        for (h in 0 until numHops) {
            val start = h * samplesPerHop
            val end = (start + samplesPerHop).coerceAtMost(samples.size)
            var sumSq = 0f
            for (i in start until end) {
                val s = samples[i]
                sumSq += s * s
            }
            val rms = sqrt(sumSq / (end - start).coerceAtLeast(1))
            lowpass = lowpass + LOWPASS_ALPHA * (rms - lowpass)
            energy[h] = lowpass
        }

        // ─── Onset strength ───
        val onset = FloatArray(numHops)
        for (h in 1 until numHops) {
            val diff = energy[h] - energy[h - 1]
            onset[h] = if (diff > 0f) diff else 0f
        }

        // ─── Adaptive threshold ───
        val thresholdWindow = 25
        val threshold = FloatArray(numHops)
        for (h in 0 until numHops) {
            val from = max(0, h - thresholdWindow)
            val to = (h + thresholdWindow).coerceAtMost(numHops)
            var sum = 0f
            for (i in from until to) sum += onset[i]
            val avg = sum / (to - from).coerceAtLeast(1)
            threshold[h] = avg * 1.5f + 0.003f
        }

        // ─── Peak pick ───
        val rawBeats = mutableListOf<Pair<Long, Float>>()
        var lastBeatMs = -MIN_BEAT_GAP_MS
        for (h in 1 until numHops - 1) {
            val isPeak = onset[h] > onset[h - 1] && onset[h] > onset[h + 1]
            val above = onset[h] > threshold[h]
            val timeMs = h * HOP_SIZE_MS

            if (isPeak && above && timeMs - lastBeatMs >= MIN_BEAT_GAP_MS) {
                rawBeats.add(timeMs to onset[h])
                lastBeatMs = timeMs
            }
        }

        Log.e(TAG, "Raw peaks found: ${rawBeats.size}")

        // ─── Speech rejection ───
        if (rawBeats.size < MIN_BEATS) {
            Log.e(TAG, "❌ Speech — only ${rawBeats.size} peaks")
            return emptyList<Long>() to emptyList<Float>()
        }

        val gaps = mutableListOf<Long>()
        for (i in 1 until rawBeats.size) {
            gaps.add(rawBeats[i].first - rawBeats[i - 1].first)
        }

        val musicalGaps = gaps.filter {
            it in MUSICAL_GAP_MIN_MS..MUSICAL_GAP_MAX_MS
        }
        val musicalRatio = musicalGaps.size.toFloat() / gaps.size.toFloat()

        if (musicalRatio < MIN_MUSICAL_GAP_RATIO) {
            Log.e(TAG, "❌ Speech — ${(musicalRatio * 100).toInt()}% musical gaps")
            return emptyList<Long>() to emptyList<Float>()
        }

        val meanGap = musicalGaps.average()
        val variance = musicalGaps.map { (it - meanGap).pow(2) }.average()
        val stdDev = sqrt(variance)
        val consistency = (1.0 - (stdDev / meanGap)).coerceIn(0.0, 1.0)

        Log.e(TAG, "BPM consistency: ${(consistency * 100).toInt()}%")

        if (consistency < MIN_BPM_CONSISTENCY) {
            Log.e(TAG, "❌ Speech — low BPM consistency")
            return emptyList<Long>() to emptyList<Float>()
        }

        // ─── Classify by strength ───
        val sortedByStrength = rawBeats.sortedBy { it.second }
        val n = sortedByStrength.size
        val softCutoff = (n * SOFT_PERCENTILE).toInt()
        val hardCutoff = (n * HARD_PERCENTILE).toInt()

        // 🆕 Max strength for normalization
        val maxStrength = sortedByStrength.maxOfOrNull { it.second } ?: 1f
        val minStrength = sortedByStrength.minOfOrNull { it.second } ?: 0f
        val strengthRange = (maxStrength - minStrength).coerceAtLeast(0.0001f)

        // 🆕 Normalize all strengths to 0..1
        val normalizedStrengths = rawBeats.associate { (t, s) ->
            t to ((s - minStrength) / strengthRange).coerceIn(0f, 1f)
        }

        val hardSet = mutableSetOf<Long>()
        val mediumSet = mutableSetOf<Long>()
        val softSet = mutableSetOf<Long>()

        sortedByStrength.forEachIndexed { i, (t, _) ->
            when {
                i >= hardCutoff -> hardSet.add(t)
                i >= softCutoff -> mediumSet.add(t)
                else -> softSet.add(t)
            }
        }

        // ─── Apply user filter ───
        val result = when (filter) {
            "hard" -> hardSet.toList()
            "medium" -> mediumSet.toList()
            "soft" -> softSet.toList()
            "hard,med" -> (hardSet + mediumSet).toList()
            "med,soft" -> (mediumSet + softSet).toList()
            else -> rawBeats.map { it.first }
        }

        // ─── Sort + build parallel strength list ───
        val sortedTimes = result.sorted()
        val strengthList = sortedTimes.map { t ->
            normalizedStrengths[t] ?: 0.5f
        }

        Log.e(
            TAG,
            "✅ Beats: ${sortedTimes.size}, " +
                    "avg strength: ${
                        if (strengthList.isNotEmpty())
                            "%.2f".format(strengthList.average())
                        else "0"
                    }, " +
                    "max: ${if (strengthList.isNotEmpty()) "%.2f".format(strengthList.max()) else "0"}"
        )

        return sortedTimes to strengthList
    }

    // ═══════════════════════════════════════════════════════════
    //  SYNTHETIC FALLBACK
    // ═══════════════════════════════════════════════════════════
    fun detectSynthetic(durationMs: Long, filter: String): BeatsState {
        val gap = 500L
        val all = (0..durationMs step gap).toList()
        val filtered = applyFilterSynthetic(all, filter)
        return BeatsState(
            detected = filtered.isNotEmpty(),
            count = filtered.size,
            filter = filter,
            beatTimesMs = filtered,
            beatStrengths = List(filtered.size) { 0.5f }
        )
    }

    private fun applyFilterSynthetic(list: List<Long>, filter: String): List<Long> {
        return when (filter) {
            "hard" -> list.filterIndexed { i, _ -> i % 4 == 0 }
            "medium" -> list.filterIndexed { i, _ -> i % 4 == 1 || i % 4 == 3 }
            "soft" -> list.filterIndexed { i, _ -> i % 4 == 2 }
            "hard,med" -> list.filterIndexed { i, _ -> i % 2 == 0 }
            "med,soft" -> list.filterIndexed { i, _ -> i % 2 == 1 }
            else -> list
        }
    }

    fun clear(): BeatsState = BeatsState()
}