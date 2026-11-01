package com.moody.moodyvideoeditor.utils

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.nio.ByteOrder
import java.util.LinkedHashMap
import kotlin.math.max

data class VisualizerSpectrum(
    val values: FloatArray,
    val sampleRate: Int = SAMPLE_RATE,
    val hopSize: Int = HOP_SIZE,
    val bandCount: Int = BAND_COUNT
) {
    val frameCount: Int get() = values.size / bandCount

    fun bandsAt(sourceTimeMs: Long): FloatArray {
        if (frameCount == 0) return FloatArray(bandCount)
        val frame = (sourceTimeMs.coerceAtLeast(0L) * sampleRate /
                (hopSize * 1000.0)).coerceIn(0.0, (frameCount - 1).toDouble())
        val before = frame.toInt()
        val after = (before + 1).coerceAtMost(frameCount - 1)
        val fraction = (frame - before).toFloat()
        val result = FloatArray(bandCount)
        val start = before * bandCount
        val next = after * bandCount
        for (i in 0 until bandCount) {
            result[i] = values[start + i] * (1f - fraction) + values[next + i] * fraction
        }
        return result
    }

    companion object {
        const val SAMPLE_RATE = 22_050
        const val FFT_SIZE = 2_048
        const val HOP_SIZE = 512
        const val BAND_COUNT = 64
        private const val MAX_DECODE_MS = 10 * 60 * 1000L
    }
}

object VisualizerAudioAnalyzer {
    private const val TAG = "VISUALIZER_AUDIO"
    private const val MAX_DECODE_MS = 10 * 60 * 1000L

    private val cache = object : LinkedHashMap<String, VisualizerSpectrum>(4, 0.75f, true) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<String, VisualizerSpectrum>?
        ): Boolean = size > 3
    }

    suspend fun analyze(context: Context, uri: Uri): VisualizerSpectrum =
        withContext(Dispatchers.IO) {
            synchronized(cache) { cache[uri.toString()] }?.let { return@withContext it }
            val pcm = decodeToMono(context, uri)
            val spectrum = VisualizerNative.nativeAnalyzePcm(
                pcm,
                VisualizerSpectrum.FFT_SIZE,
                VisualizerSpectrum.HOP_SIZE,
                VisualizerSpectrum.BAND_COUNT
            ) ?: throw IOException("Native spectrum analysis failed")
            if (spectrum.isEmpty()) throw IOException("No audio samples were decoded")
            val result = VisualizerSpectrum(spectrum)
            synchronized(cache) { cache[uri.toString()] = result }
            result
        }

    fun clearCache() = synchronized(cache) { cache.clear() }

    private fun decodeToMono(context: Context, uri: Uri): ShortArray {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(context, uri, null)
            var audioTrack = -1
            var audioFormat: MediaFormat? = null
            for (index in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(index)
                if (format.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                    audioTrack = index
                    audioFormat = format
                    break
                }
            }
            if (audioTrack < 0 || audioFormat == null) {
                throw IOException("Selected media does not contain an audio track")
            }

            extractor.selectTrack(audioTrack)
            val mime = audioFormat.getString(MediaFormat.KEY_MIME)
                ?: throw IOException("Audio format has no MIME type")
            val sourceRate = audioFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channels = audioFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
            if (sourceRate <= 0) throw IOException("Invalid audio sample rate")

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(audioFormat, null, null, 0)
            codec.start()

            val decoded = ShortAccumulator()
            val info = MediaCodec.BufferInfo()
            var inputEnded = false
            var outputEnded = false
            var sourceFrames = 0L
            var nextTargetFrame = 0L
            val maxTargetFrames = VisualizerSpectrum.SAMPLE_RATE.toLong() *
                    MAX_DECODE_MS / 1000L

            while (!outputEnded && nextTargetFrame < maxTargetFrames) {
                if (!inputEnded) {
                    val inputIndex = codec.dequeueInputBuffer(10_000)
                    if (inputIndex >= 0) {
                        val input = codec.getInputBuffer(inputIndex)
                            ?: throw IOException("Audio decoder input buffer unavailable")
                        input.clear()
                        val size = extractor.readSampleData(input, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(
                                inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            inputEnded = true
                        } else {
                            codec.queueInputBuffer(
                                inputIndex, 0, size, extractor.sampleTime, 0
                            )
                            extractor.advance()
                        }
                    }
                }

                val outputIndex = codec.dequeueOutputBuffer(info, 10_000)
                when {
                    outputIndex >= 0 -> {
                        val output = codec.getOutputBuffer(outputIndex)
                        if (output != null && info.size > 0) {
                            output.position(info.offset)
                            output.limit(info.offset + info.size)
                            val shorts = output.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                            while (shorts.remaining() >= channels) {
                                var mixed = 0
                                for (channel in 0 until channels) mixed += shorts.get().toInt()
                                while (nextTargetFrame * sourceRate / VisualizerSpectrum.SAMPLE_RATE
                                    <= sourceFrames
                                ) {
                                    decoded.add((mixed / channels).coerceIn(Short.MIN_VALUE.toInt(),
                                        Short.MAX_VALUE.toInt()).toShort())
                                    nextTargetFrame++
                                }
                                sourceFrames++
                            }
                        }
                        codec.releaseOutputBuffer(outputIndex, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            outputEnded = true
                        }
                    }

                    outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val outputFormat = codec.outputFormat
                        val encoding = outputFormat.getInteger(
                            MediaFormat.KEY_PCM_ENCODING,
                            android.media.AudioFormat.ENCODING_PCM_16BIT
                        )
                        if (encoding != android.media.AudioFormat.ENCODING_PCM_16BIT) {
                            throw IOException("Audio decoder returned unsupported PCM encoding")
                        }
                    }
                }
            }
            if (decoded.size == 0) throw IOException("No audio samples were decoded")
            return decoded.toArray()
        } catch (error: IOException) {
            throw error
        } catch (error: Exception) {
            Log.e(TAG, "Audio analysis failed for $uri", error)
            throw IOException("Could not decode audio for the visualizer", error)
        } finally {
            try {
                codec?.stop()
            } catch (_: IllegalStateException) {
                // Codec may not have reached the started state.
            }
            codec?.release()
            extractor.release()
        }
    }

    private class ShortAccumulator {
        private var values = ShortArray(16_384)
        var size: Int = 0
            private set

        fun add(value: Short) {
            if (size == values.size) {
                values = values.copyOf(max(values.size * 2, 1))
            }
            values[size++] = value
        }

        fun toArray(): ShortArray = values.copyOf(size)
    }
}
