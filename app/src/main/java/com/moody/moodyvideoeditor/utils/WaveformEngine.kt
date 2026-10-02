package com.moody.moodyvideoeditor.utils

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteOrder
import kotlin.math.abs

object WaveformEngine {

    private const val TAG = "WAVEFORM"
    private const val SAMPLES_PER_SECOND = 100   // 10ms per bar
    private const val MAX_DURATION_MS = 10 * 60 * 1000L

    // Cache: URI string → normalized FloatArray (0..1 amplitudes)
    private val cache = mutableMapOf<String, FloatArray>()

    suspend fun loadWaveform(
        context: Context,
        uri: Uri
    ): FloatArray = withContext(Dispatchers.IO) {
        val key = uri.toString()
        cache[key]?.let { return@withContext it }

        val result = try {
            extractAmplitudes(context, uri)
        } catch (e: Throwable) {
            Log.e(TAG, "Waveform extraction failed", e)
            FloatArray(0)
        }

        if (result.isNotEmpty()) cache[key] = result
        result
    }

    fun getCached(uri: Uri): FloatArray? = cache[uri.toString()]

    fun clearCache() = cache.clear()

    // ═══════════════════════════════════════════════════════════
    //  EXTRACT AMPLITUDE ARRAY
    // ═══════════════════════════════════════════════════════════
    private fun extractAmplitudes(context: Context, uri: Uri): FloatArray {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, uri, null)
        } catch (e: Throwable) {
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
            codec.release()
            extractor.release()
            return FloatArray(0)
        }

        val samplesPerBar = (srcSampleRate / SAMPLES_PER_SECOND).coerceAtLeast(1)
        val maxBars = ((MAX_DURATION_MS * SAMPLES_PER_SECOND) / 1000L).toInt()

        val amplitudes = ArrayList<Float>(2048)
        val bufferInfo = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false
        var sampleAccum = 0
        var sampleSum = 0f
        var sampleCount = 0

        try {
            while (!outputDone && amplitudes.size < maxBars) {
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
                            codec.queueInputBuffer(
                                inIdx, 0, sampleSize, extractor.sampleTime, 0
                            )
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
                        val shortArr = ShortArray(shortBuf.remaining())
                        shortBuf.get(shortArr)

                        val step = if (srcChannels >= 2) 2 else 1
                        var i = 0
                        while (i < shortArr.size) {
                            val v = shortArr[i].toFloat() / 32768f
                            sampleSum += abs(v)
                            sampleCount++
                            sampleAccum++

                            if (sampleAccum >= samplesPerBar) {
                                val avg = if (sampleCount > 0)
                                    sampleSum / sampleCount else 0f
                                amplitudes.add(avg)
                                sampleSum = 0f
                                sampleCount = 0
                                sampleAccum = 0
                            }
                            i += step
                        }
                    }
                    codec.releaseOutputBuffer(outIdx, false)

                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        outputDone = true
                    }
                }
            }

            if (sampleCount > 0) {
                amplitudes.add(sampleSum / sampleCount)
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Decode error", e)
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

        if (amplitudes.isEmpty()) return FloatArray(0)

        val arr = amplitudes.toFloatArray()
        val maxVal = arr.maxOrNull() ?: 1f
        if (maxVal > 0.001f) {
            for (i in arr.indices) {
                arr[i] = (arr[i] / maxVal).coerceIn(0f, 1f)
            }
        }

        Log.e(TAG, "Loaded waveform: ${arr.size} bars")
        return arr
    }
}